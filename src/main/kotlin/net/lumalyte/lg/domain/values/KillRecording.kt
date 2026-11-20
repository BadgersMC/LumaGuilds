package net.lumalyte.lg.domain.values

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** REQ-199: why a player kill is or is not saved to the kill log. */
enum class KillDecision { RECORD, SELF, NO_GUILD, SAME_GUILD, FARMING }

/** REQ-199: the kill log rules, kept pure so they can be tested without a server. */
object KillRecording {
    val FARM_COOLDOWN: Duration = Duration.ofMinutes(10)

    fun decide(
        killerId: UUID,
        victimId: UUID,
        killerGuildId: UUID?,
        victimGuildId: UUID?,
        lastSamePairKill: Instant?,
        now: Instant,
        cooldown: Duration = FARM_COOLDOWN,
    ): KillDecision = when {
        killerId == victimId -> KillDecision.SELF
        killerGuildId == null && victimGuildId == null -> KillDecision.NO_GUILD
        killerGuildId != null && killerGuildId == victimGuildId -> KillDecision.SAME_GUILD
        lastSamePairKill != null && Duration.between(lastSamePairKill, now) < cooldown -> KillDecision.FARMING
        else -> KillDecision.RECORD
    }
}

/** REQ-200: kills, deaths and the kill/death ratio. */
data class CombatSummary(val kills: Int, val deaths: Int) {
    val ratio: Double get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths
}
