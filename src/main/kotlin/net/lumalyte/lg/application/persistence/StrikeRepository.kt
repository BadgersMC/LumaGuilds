package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildStrike
import java.time.Instant
import java.util.UUID

interface StrikeRepository {
    fun recordStrike(strike: GuildStrike): Boolean

    /** Duplicate provider ids return false; storage failures throw so feed cursors stay replay-safe. */
    fun recordExternalStrike(strike: GuildStrike): Boolean

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
    fun getByGuild(guildId: UUID): List<GuildStrike>
    fun getAllCounts(): Map<UUID, Int>
    fun getAllActiveCounts(): Map<UUID, Int>
    fun countAll(): Int
}
