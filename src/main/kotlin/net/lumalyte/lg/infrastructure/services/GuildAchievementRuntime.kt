package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.StallReadResult
import net.lumalyte.lg.domain.achievements.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
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
    /** When each guild was founded; drives the guild-age advancements (REQ-225). */
    private val createdAt: (UUID) -> java.time.Instant? = { null },
    private val clock: () -> java.time.Instant = java.time.Instant::now,
    /**
     * Pays guild XP at most once per payout ID (REQ-227). Returns true when paid now or already paid;
     * false leaves the payout to be retried on a later check.
     */
    private val guildXp: (guildId: UUID, amount: Int, payoutId: UUID) -> net.lumalyte.lg.domain.values.SystemAward =
        { _, _, _ -> net.lumalyte.lg.domain.values.SystemAward.PAID_NOW },
    /** REQ-300: where each member's last known playtime is kept between restarts. */
    playtimeStore: net.lumalyte.lg.domain.values.MemberPlaytimeStore = net.lumalyte.lg.domain.values.MemberPlaytimeStore.InMemory(),
) {
    internal val playtime = GuildPlaytime(plugin, members, playtimeStore)
    /** True while a member is being caught up on join, so reward-free completions stay private (REQ-229). */
    private var joining = false
    /** Guild-wide payouts confirmed this run, so the ledger is not asked again every minute. */
    private val paidGuildXp = mutableSetOf<UUID>()
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
        playtime.start()
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
                    val totals = totals(guild)
                    players.forEach { player ->
                        signal(player, "ROOT")
                        signalTotals(player, totals)
                        read(player)?.let { projection.project(player, it) }
                    }
                    // After members are signalled, so a goal reached this minute is announced by its first completer.
                    payStanding(guild, totals)
                }
            }
        }, 100L, 1200L)
    }

    fun stop() {
        Bukkit.getOnlinePlayers().filter { it.uniqueId in dirty }.forEach { flush(it) }
        projection.close()
        playtime.stop()
    }

    fun guild(playerId: UUID): UUID? = members.getGuildsByPlayer(playerId).sortedBy { it.toString() }.firstOrNull()
    fun isMember(playerId: UUID, guildId: UUID): Boolean = guildId in members.getGuildsByPlayer(playerId)
    fun online(guildId: UUID): List<Player> = members.getByGuild(guildId).mapNotNull { Bukkit.getPlayer(it.playerId) }

    /** Let vanilla's join-time advancement reset finish before the first custom definition packet. */
    fun initializeAfterJoin(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) initialize(player)
        }, 20L)
    }

    fun initialize(player: Player) {
        playtime.record(player)
        val guild = guild(player.uniqueId) ?: return
        joining = true
        try {
            read(player)?.let { record ->
                val pending = engine.pending(record)
                if (pending.completed.isNotEmpty()) deliver(player, record, pending)
            }
            signal(player, "ROOT")
            val totals = totals(guild)
            // Pay what the guild already qualifies for first, so this member's catch-up reads as earned earlier.
            payStanding(guild, totals)
            signalTotals(player, totals)
            read(player)?.let { projection.project(player, it) }
        } finally {
            joining = false
        }
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
            val guildId = guild(player.uniqueId)
            val payouts = change.completed.associateWith { node -> guildId?.let { payGuild(it, node) } }
            if (change.completed.isNotEmpty()) flush(player)
            if (change.completed.isNotEmpty()) projection.project(player, change.record)
            change.completed.forEach { announce(player, it, broadcasts(payouts[it], joining)) }
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

    /** [public] celebrates to the server (toast, chat, Discord); otherwise only this member is told. */
    private fun announce(player: Player, node: GuildAchievement, public: Boolean) {
        if (public) projection.celebrate(player, node.id)
        player.sendMessage(net.kyori.adventure.text.Component.text(
            if (public) "Guild achievement: ${node.title}" else "Guild achievement: ${node.title} (your guild earned this earlier)",
            net.kyori.adventure.text.format.NamedTextColor.GOLD,
        ))
        node.messages.forEach {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(it))
        }
        if (public && node.guildXp > 0) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "+${"%,d".format(java.util.Locale.ROOT, node.guildXp)} guild XP",
                net.kyori.adventure.text.format.NamedTextColor.GOLD,
            ))
        }
        if (node.personalXp > 0) {
            player.sendMessage(net.kyori.adventure.text.Component.text("+${node.personalXp} personal Minecraft XP"))
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
        runCatching { playtime.record(player) }
        if (player.uniqueId in dirty) flush(player)
        records.remove(player.uniqueId)
        failed.remove(player.uniqueId)
    }

    /** Guild-wide figures read from the guild itself rather than counted while a member is online. */
    /** [hours] is null while some member's stored playtime is still being read (REQ-300). */
    private data class GuildTotals(val level: Int?, val hours: Int?, val members: Int, val ageDays: Int?)

    private fun totals(guild: UUID): GuildTotals {
        val roster = members.getByGuild(guild)
        val hours = playtime.memberTicks(guild)?.let(::playHours)
        val age = createdAt(guild)?.let { java.time.Duration.between(it, clock()).toDays().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }
        return GuildTotals(progression.getGuildProgression(guild)?.currentLevel, hours, roster.size, age)
    }

    /** Each guild is paid once per advancement; the XP ledger rejects a repeated payout ID. */
    /** Null when [node] pays no guild XP, so there is no ledger entry to tell a first from a repeat. */
    private fun payGuild(guildId: UUID, node: GuildAchievement): net.lumalyte.lg.domain.values.SystemAward? {
        if (node.guildXp <= 0) return null
        val id = payoutId(guildId, node.id)
        if (id in paidGuildXp) return net.lumalyte.lg.domain.values.SystemAward.ALREADY_PAID
        val result = runCatching { guildXp(guildId, node.guildXp, id) }
            .onFailure { plugin.logger.warning("Guild XP for advancement ${node.id} will be retried: ${it.javaClass.simpleName}") }
            .getOrDefault(net.lumalyte.lg.domain.values.SystemAward.FAILED)
        if (result != net.lumalyte.lg.domain.values.SystemAward.FAILED) paidGuildXp += id
        return result
    }

    /** REQ-228: guild-wide goals pay the guild once it qualifies, even if members completed them earlier. */
    private fun payStanding(guildId: UUID, totals: GuildTotals) {
        engine.definitions.filter { node ->
            when (node.requirement) {
                "GUILD_LEVEL_UP" -> (totals.level ?: 0) >= (node.target?.toIntOrNull() ?: Int.MAX_VALUE)
                "GUILD_COMBINED_PLAYTIME" -> (totals.hours ?: -1) >= node.goal
                "GUILD_MEMBER_COUNT" -> totals.members >= node.goal
                "GUILD_AGE_DAYS" -> (totals.ageDays ?: -1) >= node.goal
                else -> false
            }
        }.forEach { payGuild(guildId, it) }
    }

    private fun signalTotals(player: Player, totals: GuildTotals) {
        totals.level?.let { signal(player, "GUILD_LEVEL_UP", it.toString()) }
        totals.hours?.let { signal(player, "GUILD_COMBINED_PLAYTIME", amount = it, absolute = true) }
        signal(player, "GUILD_MEMBER_COUNT", amount = totals.members, absolute = true)
        totals.ageDays?.let { signal(player, "GUILD_AGE_DAYS", amount = it, absolute = true) }
    }

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
                n.getString("frame"), n.getFloat("x"), n.getFloat("y"), n.optInt("guildXp", 0))
        }
    }

    companion object {
        /** Stable per guild and advancement, so every member's completion maps to one ledger entry. */
        /** REQ-229: the guild's first completion is public; catch-ups and repeats are private. */
        fun broadcasts(payout: net.lumalyte.lg.domain.values.SystemAward?, joining: Boolean): Boolean = when (payout) {
            net.lumalyte.lg.domain.values.SystemAward.PAID_NOW -> true
            net.lumalyte.lg.domain.values.SystemAward.ALREADY_PAID -> false
            else -> !joining
        }

        fun payoutId(guildId: UUID, nodeId: String): UUID =
            UUID.nameUUIDFromBytes("lumaguilds:advancement-xp:$guildId:$nodeId".toByteArray(Charsets.UTF_8))

        /** Whole hours from vanilla play ticks, with no upper limit below Int.MAX_VALUE (REQ-226). */
        fun playHours(ticks: List<Long>): Int {
            var total = 0L
            for (t in ticks) { total += t.coerceAtLeast(0L) / 72_000L; if (total >= Int.MAX_VALUE) return Int.MAX_VALUE }
            return total.toInt()
        }
    }
}
