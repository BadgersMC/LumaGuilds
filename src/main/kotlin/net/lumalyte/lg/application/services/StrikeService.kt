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

    /** Records a strike for a guild member's punishment. No-op if disabled or dedupe-hit. */
    fun recordStrike(
        guildId: UUID,
        playerUuid: UUID,
        playerName: String?,
        punishmentType: String,
        reason: String?,
        executorName: String?,
        issuedAt: Instant,
        litebansEntryId: Long?,
    ) {
        if (!configProvider().enabled) return
        repository.recordStrike(
            GuildStrike(
                guildId = guildId,
                playerUuid = playerUuid,
                playerName = playerName,
                punishmentType = punishmentType,
                reason = reason,
                executorName = executorName,
                issuedAt = issuedAt,
                litebansEntryId = litebansEntryId,
                active = true
            )
        )
    }

    /** Marks a strike inactive when its LiteBans punishment is removed/expired. */
    fun deactivateStrike(litebansEntryId: Long) {
        if (!configProvider().enabled) return
        repository.deactivateStrike(litebansEntryId)
    }

    fun countByGuild(guildId: UUID): Int = repository.countByGuild(guildId)

    fun getByGuild(guildId: UUID): List<GuildStrike> = repository.getByGuild(guildId)

    fun getAllCounts(): Map<UUID, Int> = repository.getAllCounts()

    fun countAll(): Int = repository.countAll()

    /** True when a guild has reached (or passed) the strike threshold. */
    fun isUpForPenalty(guildId: UUID): Boolean {
        val threshold = configProvider().threshold
        return threshold > 0 && repository.countByGuild(guildId) >= threshold
    }
}
