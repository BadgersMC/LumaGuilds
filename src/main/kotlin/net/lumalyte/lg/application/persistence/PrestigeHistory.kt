package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.PrestigeRecord
import java.util.UUID

/** Read-only view of applied prestiges (REQ-152). */
interface PrestigeHistory {
    /** Applied prestiges for one guild, oldest first. */
    fun history(guildId: UUID): List<PrestigeRecord>

    /** The first applied prestige server-wide that reached [tier], or null. */
    fun firstToReach(tier: Int): PrestigeRecord?

    /** REQ-275: every guild with at least one prestige, with when it reached its current count. */
    fun standings(): List<net.lumalyte.lg.domain.rewards.HallStanding> = emptyList()
}
