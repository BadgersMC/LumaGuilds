package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.domain.rewards.PrestigeAdjustment
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import java.time.Instant
import java.util.UUID

/** REQ-298: one member's claim to a prestige tier's Discord role, earned with [guildId] at [earnedAt]. */
data class PrestigeRoleAward(val playerId: UUID, val guildId: UUID, val tier: Int, val earnedAt: Instant)

/** REQ-298: who earns which prestige role, and which roles a member should hold. Pure. */
object PrestigeRolePlan {
    val TIERS = 1..6

    /**
     * When a guild first reached each tier: its prestige receipts, plus staff corrections that raised the
     * count (each tier they skipped over counts as reached at the correction).
     */
    fun milestones(records: List<PrestigeRecord>, adjustments: List<PrestigeAdjustment>): Map<Int, Instant> {
        val reached = mutableMapOf<Int, Instant>()
        fun reach(tier: Int, at: Instant) {
            if (tier !in TIERS) return
            reached.merge(tier, at) { a, b -> minOf(a, b) }
        }
        records.forEach { reach(it.tier, it.appliedAt) }
        adjustments.filter { it.newCount > it.oldCount }.forEach { adj ->
            (adj.oldCount + 1..adj.newCount).forEach { reach(it, adj.at) }
        }
        return reached
    }

    /**
     * The guild's members at [at]: membership stints open at that moment, plus current members who had
     * already joined (covers members from before stints were recorded).
     */
    fun rosterAt(at: Instant, stints: List<MembershipHistory>, current: Map<UUID, Instant>): Set<UUID> =
        stints.filter { !it.joinedAt.isAfter(at) && (it.departedAt == null || it.departedAt!!.isAfter(at)) }
            .map { it.playerId }.toSet() +
            current.filterValues { !it.isAfter(at) }.keys

    /** The single role a member should hold (their highest tier) and the other prestige roles to take away. */
    data class Sync(val grant: String?, val revoke: Set<String>)

    fun sync(highestTier: Int?, roles: Map<Int, String>): Sync {
        val grant = highestTier?.let { roles[it] }
        return Sync(grant, roles.filterKeys { it != highestTier }.values.toSet() - setOfNotNull(grant))
    }
}
