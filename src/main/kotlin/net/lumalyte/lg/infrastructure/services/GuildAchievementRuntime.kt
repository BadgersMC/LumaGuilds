package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.StallReadResult
import net.lumalyte.lg.domain.achievements.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Server-thread adapter. Progress, claimed IDs and personal XP share Minecraft's player-data save. */
internal class GuildAchievementRuntime(
    private val plugin: JavaPlugin,
    private val members: MemberRepository,
    private val progression: ProgressionRepository,
    private val stalls: GuildStallReadService = GuildStallReadClient(),
) {
    private val key by lazy { NamespacedKey(plugin, "guild_achievements_v1") }
    val engine by lazy { GuildAchievementEngine(loadDefinitions()) }
    private val projection by lazy { GuildAchievementProjection(plugin, engine.definitions) }
    private val dirty = mutableSetOf<UUID>()
    private val failed = mutableSetOf<UUID>()
    private val records = mutableMapOf<UUID, AchievementRecord>()
    private val reviewRequired = mutableSetOf<UUID>()
    private val ownershipReads = mutableSetOf<UUID>()

    fun start() {
        projection.register()
        plugin.server.pluginManager.registerEvents(GuildAchievementListener(this), plugin)
        runCatching { GuildAchievementCompanions(plugin, this).start() }
            .onFailure { plugin.logger.severe("Achievement companion state unavailable; core tracking remains active: ${it.javaClass.simpleName}") }
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.uniqueId in dirty) runCatching { flush(player) }
            }
            Bukkit.getOnlinePlayers().groupBy { guild(it.uniqueId) }.forEach { (guild, players) ->
                if (guild != null) {
                    refreshStallOwnership(players.first(), guild)
                    val hours = combinedHours(guild)
                    val level = progression.getGuildProgression(guild)?.currentLevel
                    players.forEach { player ->
                        signal(player, "ROOT")
                        level?.let { signal(player, "GUILD_LEVEL_UP", it.toString()) }
                        signal(player, "GUILD_COMBINED_PLAYTIME", amount = hours, absolute = true)
                        read(player)?.let { projection.project(player, it) }
                    }
                }
            }
        }, 100L, 1200L)
    }

    fun stop() {
        Bukkit.getOnlinePlayers().filter { it.uniqueId in dirty }.forEach { flush(it) }
        projection.close()
    }

    fun guild(playerId: UUID): UUID? = members.getGuildsByPlayer(playerId).sortedBy { it.toString() }.firstOrNull()
    fun isMember(playerId: UUID, guildId: UUID): Boolean = guildId in members.getGuildsByPlayer(playerId)
    fun online(guildId: UUID): List<Player> = members.getByGuild(guildId).mapNotNull { Bukkit.getPlayer(it.playerId) }

    fun initialize(player: Player) {
        val guild = guild(player.uniqueId) ?: return
        read(player)?.let { record ->
            val pending = engine.pending(record)
            if (pending.completed.isNotEmpty()) deliver(player, record, pending)
        }
        signal(player, "ROOT")
        progression.getGuildProgression(guild)?.let { signal(player, "GUILD_LEVEL_UP", it.currentLevel.toString()) }
        signal(player, "GUILD_COMBINED_PLAYTIME", amount = combinedHours(guild), absolute = true)
        read(player)?.let { projection.project(player, it) }
        refreshStallOwnership(player, guild)
    }

    /** Existing ownership qualifies. Unavailable or late data never invents a purchase or an award. */
    internal fun refreshStallOwnership(viewer: Player, guildId: UUID) {
        if (!ownershipReads.add(guildId)) return
        runCatching { stalls.read(guildId, viewer.uniqueId) }.getOrElse {
            ownershipReads.remove(guildId)
            return
        }.whenComplete { result, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                ownershipReads.remove(guildId)
                if (error == null && viewer.isOnline && guild(viewer.uniqueId) == guildId &&
                    result is StallReadResult.Available && result.stalls.any { it.state in setOf("OWNED", "GRACE") }) {
                    online(guildId).filter { isMember(it.uniqueId, guildId) }.forEach {
                        signal(it, "GUILD_STALL_OWNED", absolute = true)
                    }
                }
            })
        }
    }

    fun read(player: Player): AchievementRecord? = runCatching {
        check(Bukkit.isPrimaryThread())
        check(player.uniqueId !in failed) { "Achievement record is quarantined until reconnect/review" }
        records[player.uniqueId]?.let { return@runCatching it }
        val raw = player.persistentDataContainer.get(key, PersistentDataType.STRING) ?: return@runCatching AchievementRecord()
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1)
        check(!json.optBoolean("reviewRequired", false)) { "Ambiguous XP delivery requires administrator review" }
        val counts = json.getJSONObject("progress")
        val progress = counts.keySet().associateWith { id ->
            val count = counts.getInt(id)
            require(count >= 0)
            engine.definitions.find { it.id == id }?.let { require(count <= it.goal) }
            count
        }
        AchievementRecord(progress, strings(json.getJSONArray("rewarded")), strings(json.getJSONArray("receipts")),
            json.optString("tracked").takeIf { it.isNotBlank() && it != "null" }).also { records[player.uniqueId] = it }
    }.onFailure { error ->
        if (failed.add(player.uniqueId)) plugin.logger.warning("Guild achievements unavailable for ${player.uniqueId}: ${error.javaClass.simpleName}; retained data was not cleared")
    }.getOrNull()

    fun track(player: Player, id: String?): Boolean {
        if (guild(player.uniqueId) == null || id != null && engine.definitions.none { it.id == id }) return false
        val before = read(player) ?: return false
        write(player, before.copy(tracked = id))
        flush(player)
        return true
    }

    fun signal(player: Player, type: String, target: String? = null, amount: Int = 1,
        absolute: Boolean = false, receipt: String? = null) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) signal(player, type, target, amount, absolute, receipt) })
            return
        }
        if (!player.isOnline || guild(player.uniqueId) == null) return
        val before = read(player) ?: return
        val change = engine.advance(before, type, target, amount, absolute, receipt)
        if (change.record == before) return
        deliver(player, before, change)
    }

    private fun deliver(player: Player, before: AchievementRecord, change: AchievementChange) {
        val xp = change.completed.sumOf { it.personalXp }
        val oldXp = Triple(player.totalExperience, player.level, player.exp)
        try {
            // Claimed IDs and experience are saved together in player.dat; no guild XP or bank calls here.
            write(player, change.record)
            if (xp > 0) player.giveExp(xp)
            if (change.completed.isNotEmpty()) flush(player)
            if (change.completed.isNotEmpty()) projection.project(player, change.record)
            change.completed.forEach { node ->
                projection.celebrate(player, node.id)
                player.sendMessage(net.kyori.adventure.text.Component.text("Guild achievement: ${node.title}", net.kyori.adventure.text.format.NamedTextColor.GOLD))
                node.messages.forEach { player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(it)) }
                if (node.personalXp > 0) player.sendMessage(net.kyori.adventure.text.Component.text("+${node.personalXp} personal Minecraft XP"))
            }
        } catch (error: Exception) {
            // Only a verified unchanged XP outcome is retryable. Ambiguous outcomes retain claimed IDs.
            if (Triple(player.totalExperience, player.level, player.exp) == oldXp) {
                write(player, change.record.copy(rewarded = before.rewarded))
                runCatching { flush(player) }
            } else {
                reviewRequired += player.uniqueId
                runCatching { flush(player) }
            }
            failed += player.uniqueId
            plugin.logger.severe("Achievement delivery needs review for ${player.uniqueId}; no automatic replay: ${error.javaClass.simpleName}")
        }
    }

    fun shared(guildId: UUID, type: String, target: String? = null, amount: Int = 1,
        absolute: Boolean = false, receipt: String? = null) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { shared(guildId, type, target, amount, absolute, receipt) })
            return
        }
        online(guildId).forEach { signal(it, type, target, amount, absolute, receipt) }
    }

    fun projectionAvailable(): Boolean = projection.available

    fun refreshProjection() { if (!projection.available) projection.register() }

    fun leave(player: Player) {
        if (player.uniqueId in dirty) flush(player)
        records.remove(player.uniqueId)
        failed.remove(player.uniqueId)
    }

    private fun combinedHours(guild: UUID): Int = (members.getByGuild(guild).sumOf {
        Bukkit.getOfflinePlayer(it.playerId).getStatistic(Statistic.PLAY_ONE_MINUTE).toLong()
    } / 72000L).coerceAtMost(1000L).toInt()

    private fun write(player: Player, record: AchievementRecord) {
        records[player.uniqueId] = record
        dirty += player.uniqueId
    }

    private fun flush(player: Player) {
        val record = records[player.uniqueId] ?: return
        val json = JSONObject().put("schema", 1).put("progress", JSONObject(record.progress))
            .put("rewarded", JSONArray(record.rewarded.toList())).put("receipts", JSONArray(record.receipts.toList()))
            .put("tracked", record.tracked ?: JSONObject.NULL)
            .put("reviewRequired", player.uniqueId in reviewRequired)
        player.persistentDataContainer.set(key, PersistentDataType.STRING, json.toString())
        player.saveData()
        dirty.remove(player.uniqueId)
    }

    private fun strings(array: JSONArray): Set<String> = (0 until array.length()).map { array.getString(it) }.toSet()

    private fun loadDefinitions(): List<GuildAchievement> {
        val raw = requireNotNull(plugin.getResource("guild-achievements.json")).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("nodes")
        return (0 until array.length()).map { i ->
            val n = array.getJSONObject(i)
            fun list(name: String) = n.getJSONArray(name).let { a -> (0 until a.length()).map { a.getString(it) } }
            GuildAchievement(n.getString("id"), n.optString("parent").takeUnless { it.isBlank() || it == "null" },
                n.getString("title"), list("description"), n.getString("requirement"),
                n.optString("target").takeUnless { it.isBlank() || it == "null" }, n.getInt("goal"),
                n.getInt("personalXp"), list("messages"), n.getString("category"), n.getString("icon"),
                n.getString("frame"), n.getFloat("x"), n.getFloat("y"))
        }
    }
}
