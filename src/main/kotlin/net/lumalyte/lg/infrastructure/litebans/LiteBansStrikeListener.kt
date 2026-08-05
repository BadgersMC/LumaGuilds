package net.lumalyte.lg.infrastructure.litebans

import litebans.api.Entry
import litebans.api.Events
import litebans.api.`Events$Listener`
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.UUID

/**
 * Bridges LiteBans punishment events into Guild Strikes.
 *
 * When a punishment is issued ([entryAdded]) the punished player's guild at
 * that moment is resolved and a strike is recorded against it. When a
 * punishment is removed ([entryRemoved]) the strike is marked inactive — the
 * public view keeps showing it but flagged as lifted.
 *
 * Only registered when the LiteBans plugin is present at runtime (softdepend).
 *
 * Note: the LiteBans 2.x `Events$Listener` class file ships without an
 * InnerClasses attribute, so Kotlin sees it as a top-level class and it must
 * be referenced with the backtick-quoted name.
 */
class LiteBansStrikeListener(
    private val plugin: JavaPlugin,
    private val guildService: GuildService,
    private val strikeService: StrikeService,
    private val configProvider: () -> StrikesConfig,
) : `Events$Listener`() {

    private val countedTypes: Set<String>
        get() = configProvider().countedTypes.map { it.uppercase() }.toSet()

    override fun entryAdded(entry: Entry) {
        if (!configProvider().enabled) return
        // LiteBans can fire this on its own threads; hop to the main thread
        // before touching Bukkit/GuildService state.
        Bukkit.getScheduler().runTask(plugin, Runnable {
            handleEntryAdded(entry)
        })
    }

    override fun entryRemoved(entry: Entry) {
        if (!configProvider().enabled) return
        val entryId = entry.id
        if (entryId <= 0) return
        // Same thread policy as entryAdded: LiteBans may fire on its own threads.
        Bukkit.getScheduler().runTask(plugin, Runnable {
            strikeService.deactivateStrike(entryId)
        })
    }

    private fun handleEntryAdded(entry: Entry) {
        val type = entry.type?.uppercase() ?: return
        if (type !in countedTypes) return

        val playerUuid = runCatching { UUID.fromString(entry.uuid) }.getOrNull() ?: return

        // Resolve the guild the player belonged to at punishment time.
        val guild = guildService.getPlayerGuilds(playerUuid).firstOrNull() ?: return

        strikeService.recordStrike(
            guildId = guild.id,
            playerUuid = playerUuid,
            playerName = Bukkit.getOfflinePlayer(playerUuid).name,
            punishmentType = type,
            reason = entry.reason,
            executorName = entry.executorName,
            issuedAt = Instant.ofEpochMilli(entry.dateStart),
            litebansEntryId = entry.id.takeIf { it > 0 }
        )
    }
}
