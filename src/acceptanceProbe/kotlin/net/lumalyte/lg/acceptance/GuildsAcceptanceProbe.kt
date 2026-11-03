package net.lumalyte.lg.acceptance

import net.lumalyte.lg.api.events.GuildBankContributionEvent
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.QuestService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Separate opt-in TEST tool; absent from the LumaGuilds production artifact. */
class GuildsAcceptanceProbe : JavaPlugin(), Listener {
    private val journal get() = dataFolder.toPath().resolve("quest-fixture.json")
    private val repository get() = ProbeServices.resolve(QuestRepository::class.java)
    private val quests get() = ProbeServices.resolve(QuestService::class.java)
    private val guilds get() = ProbeServices.resolve(GuildService::class.java)
    private val actor get() = UUID.fromString(config.getString("actor-uuid"))
    private var latest: GuildBankContributionEvent? = null

    override fun onEnable() {
        if (!config.getBoolean("enabled", false)) {
            logger.info("Acceptance probe disabled by configuration")
            server.pluginManager.disablePlugin(this)
            return
        }
        dataFolder.mkdirs()
        server.pluginManager.registerEvents(this, this)
        // An interrupted fixture is restored first, even after its action window expires.
        if (Files.exists(journal)) runCatching { gate(cleanup = true); closeQuest() }
            .onFailure { logger.severe("Acceptance fixture requires review: ${it.message}") }
        server.scheduler.runTaskTimer(this, Runnable {
            if (Files.exists(journal) && runCatching {
                    !Instant.now().isBefore(Instant.parse(config.getString("expires-at")))
                }.getOrDefault(true)) restoreIfOpen()
        }, 20L, 1200L)
    }

    override fun onDisable() = restoreIfOpen()

    private fun restoreIfOpen() {
        if (Files.exists(journal)) runCatching { gate(cleanup = true); closeQuest() }
            .onFailure { logger.severe("Acceptance fixture requires review: ${it.message}") }
    }

    private fun gate(cleanup: Boolean = false) {
        reloadConfig()
        check(config.getBoolean("enabled", false)) { "Probe disabled" }
        val bridge = requireNotNull(server.pluginManager.getPlugin("EnthusiaTestBridge"))
        check(bridge.isEnabled && bridge.config.getBoolean("enabled"))
        check(bridge.config.getString("environment") == "TEST")
        check(!config.getString("environment-id").isNullOrBlank())
        check(config.getString("environment-id") == bridge.config.getString("environment-id"))
        val markerPath = bridge.dataFolder.toPath().resolve("TEST-ONLY.lock")
        check(Files.isRegularFile(markerPath) && !Files.isSymbolicLink(markerPath) && Files.size(markerPath) <= 4096)
        val lines = Files.readAllLines(markerPath).filter { it.isNotBlank() && !it.trim().startsWith("#") }
        val marker = lines.associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
        check(marker.size == lines.size && marker.keys == setOf("schema", "environment", "environment-id", "deployment-nonce"))
        check(marker["schema"] == "1" && marker["deployment-nonce"].orEmpty().length >= 16)
        check(marker["environment"] == "TEST" && marker["environment-id"] == config.getString("environment-id"))
        check(marker["deployment-nonce"] == bridge.config.getString("deployment-nonce"))
        val guildsPlugin = requireNotNull(server.pluginManager.getPlugin("LumaGuilds"))
        ProbeServices.requireOwner(guildsPlugin.javaClass.classLoader)
        val jar = java.nio.file.Path.of(guildsPlugin.javaClass.protectionDomain.codeSource.location.toURI())
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))
            .joinToString("") { "%02x".format(it) }
        check(hash == config.getString("guilds-sha256")) { "Guilds pin mismatch" }
        if (!cleanup) {
            val end = Instant.parse(config.getString("expires-at"))
            check(Instant.now().isBefore(end) && end.isBefore(Instant.now().plusSeconds(7200)))
            check(server.onlinePlayers.all { it.uniqueId == actor }) { "Other participants online" }
            val player = requireNotNull(server.getPlayer(actor))
            check(player.name == "FainNeito")
            val guild = guilds.getPlayerGuilds(actor).single()
            check(guild.name == "GoldProofOct26") { "Disposable guild required" }
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is ConsoleCommandSender) return true
        runCatching {
            check(args.size == 1)
            gate(cleanup = args[0] == "quest-close")
            when (args[0]) {
                "snapshot" -> snapshot()
                "quest-open" -> openQuest()
                "quest-close" -> closeQuest()
                "replay-last" -> {
                    val event = requireNotNull(latest)
                    check(event.playerId == actor && guilds.getPlayerGuilds(actor).single().id == event.guildId)
                    quests.incrementBankDeposit(event.guildId, actor, event.transactionId)
                    snapshot()
                }
                else -> error("Unknown closed operation")
            }
        }.onFailure { sender.sendMessage("[GuildsAcceptanceProbe] DENIED/FAILED ${it.message}") }
        return true
    }

    private fun openQuest() {
        check(!Files.exists(journal)) { "Journal already exists; reconcile first" }
        val original = requireNotNull(repository.getActiveQuestSet())
        val updated = QuestFixturePlan.install(original)
        val record = JSONObject().put("week", original.weekId).put("hash", QuestFixturePlan.fingerprint(original))
        Files.writeString(journal, record.toString(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        repository.saveActiveQuestSet(updated)
        check(repository.getActiveQuestSet() == updated)
        logger.info("ACCEPTANCE quest-open count=5000 reward=500 original-hash=${record.getString("hash")}")
        snapshot()
    }

    private fun closeQuest() {
        if (!Files.exists(journal)) { logger.info("ACCEPTANCE fixture already closed"); return }
        val record = JSONObject(Files.readString(journal))
        val restored = QuestFixturePlan.restore(requireNotNull(repository.getActiveQuestSet()),
            record.getString("week"), record.getString("hash"))
        repository.saveActiveQuestSet(restored)
        check(repository.getActiveQuestSet() == restored)
        Files.move(journal, dataFolder.toPath().resolve("closed-${System.currentTimeMillis()}.json"))
        logger.info("ACCEPTANCE original quest definitions restored exactly")
    }

    private fun snapshot() {
        val guild = guilds.getPlayerGuilds(actor).single()
        val week = requireNotNull(repository.getActiveQuestSet())
        logger.info("ACCEPTANCE guild=${guild.id} bank=${guild.bankBalance} fixture=" +
            week.quests.firstOrNull { it.id == QuestFixturePlan.ID }?.let { quests.progressFor(guild.id, it.id) })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun contribution(event: GuildBankContributionEvent) {
        if (config.getString("actor-uuid") == event.playerId.toString()) {
            latest = event
            logger.info("ACCEPTANCE observed deposit transaction=${event.transactionId}")
        }
    }
}
