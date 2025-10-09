package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a rank change record for audit purposes.
 */
data class RankChangeRecord(
    val id: UUID = UUID.randomUUID(),
    val playerId: UUID,
    val guildId: UUID,
    val oldRankId: UUID?,
    val newRankId: UUID,
    val changedBy: UUID,
    val changedAt: Instant,
    val reason: String? = null
) {
    /**
     * Gets a human-readable description of the rank change.
     */
    fun getDescription(): String {
        return "Rank changed from ${oldRankId?.let { "rank $it" } ?: "none"} to rank $newRankId"
    }
}