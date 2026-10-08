package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.StrikeRepository
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.GuildStrike
import java.time.Instant
import java.util.UUID

/** Configured write policy and historical/current strike queries. */
// Preserve the established public query and reconciliation facade used by plugin adapters.
@Suppress("TooManyFunctions")
class StrikeService(private val repository: StrikeRepository, private val configProvider: () -> StrikesConfig) {
    /** Record an attributed legacy strike while enabled. */
    fun recordStrike(
        guildId: UUID,
        playerUuid: UUID,
        playerName: String?,
        punishmentType: String,
        reason: String?,
        executorName: String?,
        issuedAt: Instant,
        litebansEntryId: Long?,
        active: Boolean = true,
    ): Boolean {
        if (!configProvider().enabled) return false
        return repository.recordStrike(
            GuildStrike(
                guildId = guildId,
                playerUuid = playerUuid,
                playerName = playerName,
                punishmentType = punishmentType,
                reason = reason,
                executorName = executorName,
                issuedAt = issuedAt,
                litebansEntryId = litebansEntryId,
                active = active,
            ),
        )
    }

    /** Record the complete provider identity and attribution only while strikes are enabled. */
    fun recordExternalStrike(strike: GuildStrike): Boolean =
        configProvider().enabled && repository.recordExternalStrike(strike)

    /** Deactivate an existing legacy identity while enabled. */
    fun deactivateStrike(punishmentType: String, litebansEntryId: Long) {
        if (!configProvider().enabled) return
        repository.deactivateStrike(punishmentType, litebansEntryId)
    }

    /** Update a known legacy identity without changing its historical guild. */
    fun reconcileLegacyStrike(punishmentType: String, litebansEntryId: Long, active: Boolean): Boolean {
        if (!configProvider().enabled) return false
        return repository.reconcileLegacyStrike(punishmentType, litebansEntryId, active)
    }

    /** Update a known provider identity; failures propagate so the feed can retry. */
    fun reconcileExternalStrike(
        sourceProvider: String,
        sourcePunishmentId: String,
        active: Boolean,
        expiresAt: Instant?,
    ): Boolean {
        if (!configProvider().enabled) return false
        return repository.reconcileExternalStrike(sourceProvider, sourcePunishmentId, active, expiresAt)
    }

    /** Expire persisted provider strikes without waiting for another source event. */
    fun deactivateExpiredExternal(now: Instant): Int =
        if (configProvider().enabled) repository.deactivateExpiredExternal(now) else 0

    /** Count every historical strike for a guild. */
    fun countByGuild(guildId: UUID): Int = repository.countByGuild(guildId)
    /** Count active strikes for a guild. */
    fun countActiveByGuild(guildId: UUID): Int = repository.countActiveByGuild(guildId)
    /** Read the ordered historical strikes for a guild. */
    fun getByGuild(guildId: UUID): List<GuildStrike> = repository.getByGuild(guildId)
    /** Read historical counts by guild. */
    fun getAllCounts(): Map<UUID, Int> = repository.getAllCounts()
    /** Read active counts by guild. */
    fun getAllActiveCounts(): Map<UUID, Int> = repository.getAllActiveCounts()
    /** Count all historical strikes. */
    fun countAll(): Int = repository.countAll()

    /** Compare active strikes with the configured positive threshold. */
    fun isUpForPenalty(guildId: UUID): Boolean {
        val threshold = configProvider().threshold
        return threshold > 0 && repository.countActiveByGuild(guildId) >= threshold
    }
}
