package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildStrike
import net.lumalyte.lg.domain.entities.StrikeFeedCursor
import java.time.Instant
import java.util.UUID

interface StrikeRepository {
    fun recordStrike(strike: GuildStrike): Boolean

    /** Duplicate provider ids return false; storage failures throw so feed cursors stay replay-safe. */
    fun recordExternalStrike(strike: GuildStrike): Boolean

    fun deactivateStrike(punishmentType: String, litebansEntryId: Long): Boolean
    fun reconcileLegacyStrike(punishmentType: String, litebansEntryId: Long, active: Boolean): Boolean

    fun reconcileExternalStrike(
        sourceProvider: String,
        sourcePunishmentId: String,
        active: Boolean,
        expiresAt: Instant?,
    ): Boolean

    fun deactivateExpiredExternal(now: Instant): Int

    fun feedCursor(provider: String): StrikeFeedCursor?
    fun saveFeedCursor(provider: String, cursor: StrikeFeedCursor)

    fun countByGuild(guildId: UUID): Int
    fun countActiveByGuild(guildId: UUID): Int
    fun getByGuild(guildId: UUID): List<GuildStrike>
    fun getAllCounts(): Map<UUID, Int>
    fun getAllActiveCounts(): Map<UUID, Int>
    fun countAll(): Int
}
