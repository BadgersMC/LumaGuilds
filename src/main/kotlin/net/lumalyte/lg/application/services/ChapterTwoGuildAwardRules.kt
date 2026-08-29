package net.lumalyte.lg.application.services

import java.time.Duration
import java.time.Instant

/** Pure eligibility and unit calculations for Chapter 2 guild-wide awards. */
object ChapterTwoGuildAwardRules {
    fun netNewBankUnits(previousHighWater: Long, currentBalance: Long, valuePerUnit: Long): Int {
        require(previousHighWater >= 0) { "Previous high-water balance cannot be negative" }
        require(currentBalance >= 0) { "Current balance cannot be negative" }
        require(valuePerUnit > 0) { "Value per XP unit must be positive" }
        val priorUnits = previousHighWater / valuePerUnit
        val currentUnits = currentBalance / valuePerUnit
        return (currentUnits - priorUnits).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun recruitQualifies(joinedAt: Instant, now: Instant, isStillMember: Boolean, alreadyAwarded: Boolean): Boolean =
        isStillMember && !alreadyAwarded && !joinedAt.plus(RECRUIT_RETENTION).isAfter(now)

    fun warWinQualifies(permanentLevel: Int): Boolean = permanentLevel in 1 until 100

    val recruitRetention: Duration get() = RECRUIT_RETENTION

    private val RECRUIT_RETENTION = Duration.ofDays(7)
}
