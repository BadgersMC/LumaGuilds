package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.StrikeRepository
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.GuildStrike
import java.time.Instant
import java.util.UUID

/**
 * Application-layer service for Guild Strikes.
 *
 * Bridges LiteBans punishment events and the persistence layer, and answers
 * the queries the public command + admin GUI need.
 */
class StrikeService(
    private val repository: StrikeRepository,
    private val configProvider: () -> StrikesConfig,
) {

    /** Records a strike for a guild member's punishment. Returns true if a new row
     *  was inserted, false if disabled or a dedupe hit. */
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
                active = active
            )
        )
    }

    /** Marks a strike inactive when its LiteBans punishment is removed/expired. */
    fun deactivateStrike(punishmentType: String, litebansEntryId: Long) {
        if (!configProvider().enabled) return
        repository.deactivateStrike(punishmentType, litebansEntryId)
    }

    fun countByGuild(guildId: UUID): Int = repository.countByGuild(guildId)

    fun countActiveByGuild(guildId: UUID): Int = repository.countActiveByGuild(guildId)

    fun getByGuild(guildId: UUID): List<GuildStrike> = repository.getByGuild(guildId)

    fun getAllCounts(): Map<UUID, Int> = repository.getAllCounts()

    fun getAllActiveCounts(): Map<UUID, Int> = repository.getAllActiveCounts()

    fun countAll(): Int = repository.countAll()

    /** True when a guild's ACTIVE strikes have reached (or passed) the threshold. */
    fun isUpForPenalty(guildId: UUID): Boolean {
        val threshold = configProvider().threshold
        return threshold > 0 && repository.countActiveByGuild(guildId) >= threshold
    }
}
