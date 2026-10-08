package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildStrike
import java.time.Instant
import java.util.UUID

/** Durable strike history with stable provider identities and retryable reconciliation. */
// All twelve established port operations remain available to callers.
@Suppress("TooManyFunctions")
interface StrikeRepository {
    /** Record an attributed legacy strike while enabled. */
    fun recordStrike(strike: GuildStrike): Boolean

    /** Duplicate provider ids return false; storage failures throw so feed cursors stay replay-safe. */
    fun recordExternalStrike(strike: GuildStrike): Boolean

    /** Deactivate an existing legacy identity while enabled. */
    fun deactivateStrike(punishmentType: String, litebansEntryId: Long): Boolean

    /** Reconcile an existing legacy identity; return whether it exists. */
    fun reconcileLegacyStrike(punishmentType: String, litebansEntryId: Long, active: Boolean): Boolean

    /** Reconcile an existing provider identity; storage failures throw for safe replay. */
    fun reconcileExternalStrike(
        sourceProvider: String,
        sourcePunishmentId: String,
        active: Boolean,
        expiresAt: Instant?,
    ): Boolean

    /** Deactivate expired provider rows and return the number updated. */
    fun deactivateExpiredExternal(now: Instant): Int

    /** Count all historical strikes assigned to the guild. */
    fun countByGuild(guildId: UUID): Int

    /** Count the currently active strikes assigned to the guild. */
    fun countActiveByGuild(guildId: UUID): Int
    /** Read the ordered historical strikes for a guild. */
    fun getByGuild(guildId: UUID): List<GuildStrike>
    /** Read historical counts by guild. */
    fun getAllCounts(): Map<UUID, Int>
    /** Read active counts by guild. */
    fun getAllActiveCounts(): Map<UUID, Int>
    /** Count all historical strikes. */
    fun countAll(): Int
}
