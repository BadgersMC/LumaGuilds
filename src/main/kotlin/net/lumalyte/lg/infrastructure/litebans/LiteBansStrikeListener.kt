package net.lumalyte.lg.infrastructure.litebans

import litebans.api.Database
import litebans.api.Entry
import litebans.api.Events
import litebans.api.Events.Listener
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
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
 */
class LiteBansStrikeListener(
    private val plugin: JavaPlugin,
    private val guildService: GuildService,
    private val strikeService: StrikeService,
    private val membershipHistoryRepository: MembershipHistoryRepository,
    private val configProvider: () -> StrikesConfig,
) : Listener() {

    private val countedTypes: Set<String>
        get() = configProvider().countedTypes.map { it.uppercase() }.toSet()

    override fun entryAdded(entry: Entry) {
        if (!configProvider().enabled) return
        // Resolve the player's name here on LiteBans' own thread via LiteBans'
        // DB — never Bukkit.getOfflinePlayer(), which can block on a Mojang
        // lookup if the profile isn't cached (and would run on the tick thread).
        val playerUuid = runCatching { UUID.fromString(entry.uuid) }.getOrNull()
        val playerName = playerUuid?.let { runCatching { Database.get().getPlayerName(it) }.getOrNull() }
        // LiteBans can fire this on its own threads; hop to the main thread
        // before touching Bukkit/GuildService state.
        Bukkit.getScheduler().runTask(plugin, Runnable {
            handleEntryAdded(entry, playerUuid, playerName)
        })
    }

    override fun entryRemoved(entry: Entry) {
        if (!configProvider().enabled) return
        val type = entry.type?.uppercase() ?: return
        val entryId = entry.id
        if (entryId <= 0) return
        // Same thread policy as entryAdded: LiteBans may fire on its own threads.
        Bukkit.getScheduler().runTask(plugin, Runnable {
            strikeService.deactivateStrike(type, entryId)
        })
    }

    private fun handleEntryAdded(entry: Entry, playerUuid: UUID?, playerName: String?) {
        val type = entry.type?.uppercase() ?: return
        if (type !in countedTypes) return
        if (playerUuid == null) return

        // Resolve the guild the player belonged to at punishment time. For a
        // live event that's "now" — resolve via membership-history stints first
        // (deterministic, matches the backfill), falling back to current guilds.
        val guildId = resolveGuildAtTime(playerUuid, Instant.ofEpochMilli(entry.dateStart))
            ?: return

        strikeService.recordStrike(
            guildId = guildId,
            playerUuid = playerUuid,
            playerName = playerName,
            punishmentType = type,
            reason = entry.reason,
            executorName = entry.executorName,
            issuedAt = Instant.ofEpochMilli(entry.dateStart),
            litebansEntryId = entry.id.takeIf { it > 0 }
        )
    }

    /**
     * The guild the player belonged to at [at]: the membership-history stint
     * covering that moment (same resolution the backfill uses), falling back to
     * the player's current guild — sorted by id for determinism if a player is
     * somehow in several.
     */
    private fun resolveGuildAtTime(playerUuid: UUID, at: Instant): UUID? {
        val stints = runCatching { membershipHistoryRepository.getByPlayer(playerUuid) }
            .getOrElse { emptyList() }
        stints.firstOrNull { stint ->
            !stint.joinedAt.isAfter(at) && (stint.departedAt == null || stint.departedAt!!.isAfter(at))
        }?.let { return it.guildId }
        return guildService.getPlayerGuilds(playerUuid).sortedBy { it.id }.firstOrNull()?.id
    }
}
