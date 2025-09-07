package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.*

/**
 * Represents a wager placed on a war.
 */
data class WarWager(
    val id: UUID = UUID.randomUUID(),
    val warId: UUID,
    val declaringGuildId: UUID,
    val defendingGuildId: UUID,
    val declaringGuildWager: Int,
    val defendingGuildWager: Int,
    val totalPot: Int = declaringGuildWager + defendingGuildWager,
    val status: WagerStatus = WagerStatus.ESCROWED,
    val createdAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val winnerGuildId: UUID? = null
) {
    /**
     * Checks if this is a draw (no winner).
     */
    val isDraw: Boolean
        get() = status == WagerStatus.DRAW

    /**
     * Gets the refund amount for a specific guild in case of draw.
     */
    fun getRefundAmount(guildId: UUID): Int {
        return when (guildId) {
            declaringGuildId -> declaringGuildWager
            defendingGuildId -> defendingGuildWager
            else -> 0
        }
    }

    /**
     * Gets the winnings amount for the winning guild.
     */
    fun getWinningsAmount(winnerGuildId: UUID): Int {
        return if (this.winnerGuildId == winnerGuildId) totalPot else 0
    }
}

/**
 * Status of a war wager.
 */
enum class WagerStatus {
    ESCROWED,    // Funds are held in escrow
    WON,         // War ended with a winner, funds distributed
    DRAW,        // War ended in draw, funds refunded
    CANCELLED    // War cancelled, funds refunded
}
