package net.lumalyte.lg.infrastructure.litebans

import litebans.api.Database
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * One-shot backfill: imports pre-existing LiteBans punishments into the
 * strike ledger on startup.
 *
 * Attribution rule (matches the live listener): a punishment is attributed to
 * the guild the player was a member of AT THE TIME of the punishment, proven
 * via membership history stints. When no stint covers the punishment time
 * (history tracking predates the punishment), optionally falls back to the
 * player's current guild so old data still contributes to the "most
 * troublesome guild" ranking.
 *
 * Idempotent: every insert is deduped by LiteBans entry id, so re-runs (or a
 * backfill racing the live listener) cannot double-count.
 */
class StrikeBackfillService(
    private val strikeService: StrikeService,
    private val membershipHistoryRepository: MembershipHistoryRepository,
    private val guildService: GuildService,
    private val configProvider: () -> StrikesConfig,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Result summary of a backfill run. */
    data class BackfillResult(
        val scanned: Int,
        val attributed: Int,
        val skippedUnattributable: Int,
        val recorded: Int,
    )

    /**
     * Runs the backfill synchronously (caller decides threading). Reads from
     * LiteBans' own connection via [Database.prepareStatement] — no external
     * DB credentials required.
     */
    fun run(): BackfillResult {
        val config = configProvider()
        if (!config.enabled || !config.countedTypes.any { it in BACKFILL_QUERIES }) {
            logger.info("Strike backfill skipped (disabled or no counted types).")
            return BackfillResult(0, 0, 0, 0)
        }

        var scanned = 0
        var attributed = 0
        var skippedUnattributable = 0
        var recorded = 0

        // Player-name cache to avoid hammering LiteBans per row.
        val nameCache = mutableMapOf<UUID, String?>()
        // Per-player history cache (stints are stable during a backfill run).
        val historyCache = mutableMapOf<UUID, List<net.lumalyte.lg.domain.entities.MembershipHistory>>()

        for ((type, table) in BACKFILL_QUERIES) {
            if (type !in config.countedTypes) continue

            try {
                Database.get().prepareStatement("SELECT id, uuid, reason, banned_by_name, time, until FROM $table").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val playerUuid = rs.getUuid("uuid")
                            val entryId = rs.getLong("id")
                            if (playerUuid == null || entryId <= 0) {
                                continue
                            }

                            scanned++
                            val punishmentTime = Instant.ofEpochMilli(rs.getLong("time"))

                            val guildId = resolveGuildAtTime(
                                playerUuid,
                                punishmentTime,
                                config,
                                historyCache,
                            )
                            if (guildId == null) {
                                skippedUnattributable++
                                continue
                            }

                            attributed++
                            val playerName = nameCache.getOrPut(playerUuid) {
                                Database.get().getPlayerName(playerUuid)
                            }

                            strikeService.recordStrike(
                                guildId = guildId,
                                playerUuid = playerUuid,
                                playerName = playerName,
                                punishmentType = type,
                                reason = rs.getString("reason"),
                                executorName = rs.getString("banned_by_name"),
                                issuedAt = punishmentTime,
                                litebansEntryId = entryId,
                            )
                            recorded++
                        }
                    }
                }
            } catch (e: Exception) {
                // One table failing (e.g. renamed table) shouldn't kill the whole backfill.
                logger.warn("Strike backfill: failed reading $table: {}", e.message)
            }
        }

        logger.info(
            "Strike backfill complete: scanned={}, attributed={}, unattributable={}, recorded={}",
            scanned, attributed, skippedUnattributable, recorded,
        )
        return BackfillResult(scanned, attributed, skippedUnattributable, recorded)
    }

    /**
     * Resolves the guild the player belonged to at [at], using membership
     * history stints. Falls back to the player's current guild if configured
     * and no stint proves membership.
     */
    private fun resolveGuildAtTime(
        playerUuid: UUID,
        at: Instant,
        config: StrikesConfig,
        historyCache: MutableMap<UUID, List<net.lumalyte.lg.domain.entities.MembershipHistory>>,
    ): UUID? {
        val stints = historyCache.getOrPut(playerUuid) {
            runCatching { membershipHistoryRepository.getByPlayer(playerUuid) }.getOrElse { emptyList() }
        }
        // A stint covers `at` when joinedAt <= at AND (departedAt == null OR departedAt > at).
        val stintGuild = stints.firstOrNull { stint ->
            !stint.joinedAt.isAfter(at) && (stint.departedAt == null || stint.departedAt!!.isAfter(at))
        }?.guildId

        if (stintGuild != null) return stintGuild
        if (!config.backfill.fallbackToCurrentGuild) return null

        // Fallback: the player's current guild (best effort for old data).
        return runCatching {
            guildService.getPlayerGuilds(playerUuid).firstOrNull()?.id
        }.getOrNull()
    }

    private fun ResultSet.getUuid(column: String): UUID? {
        return runCatching { UUID.fromString(getString(column)) }.getOrNull()
    }

    companion object {
        /** Punishment type -> LiteBans table name. Keys must match countedTypes values. */
        val BACKFILL_QUERIES: Map<String, String> = linkedMapOf(
            "WARN" to "litebans_warnings",
            "KICK" to "litebans_kicks",
            "MUTE" to "litebans_mutes",
            "BAN" to "litebans_bans",
        )
    }
}
