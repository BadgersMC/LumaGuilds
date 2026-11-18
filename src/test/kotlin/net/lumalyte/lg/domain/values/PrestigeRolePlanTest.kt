package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.domain.rewards.PrestigeAdjustment
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-298: who earns prestige Discord roles, and which one each member holds. */
class PrestigeRolePlanTest {
    private val guild = UUID.randomUUID()
    private fun t(day: Long) = Instant.parse("2026-11-01T00:00:00Z").plusSeconds(day * 86_400)
    private fun record(tier: Int, day: Long) = PrestigeRecord(UUID.randomUUID(), guild, UUID.randomUUID(), tier, "r", t(day))
    private fun stint(player: UUID, from: Long, to: Long? = null) =
        MembershipHistory(UUID.randomUUID(), player, guild, t(from), to?.let { t(it) })

    @Test
    fun `milestones take the first time each tier was reached, including staff raises`() {
        val reached = PrestigeRolePlan.milestones(
            listOf(record(1, 2), record(2, 5)),
            listOf(
                PrestigeAdjustment(guild, UUID.randomUUID(), 2, 4, t(7)),
                PrestigeAdjustment(guild, UUID.randomUUID(), 4, 1, t(8)),
                PrestigeAdjustment(guild, UUID.randomUUID(), 1, 2, t(9)),
            ),
        )
        assertEquals(mapOf(1 to t(2), 2 to t(5), 3 to t(7), 4 to t(7)), reached)
    }

    @Test
    fun `roster is who was in the guild at that moment`() {
        val stayed = UUID.randomUUID(); val left = UUID.randomUUID(); val late = UUID.randomUUID()
        val leftBefore = UUID.randomUUID(); val unrecorded = UUID.randomUUID()
        val stints = listOf(stint(stayed, 0), stint(left, 1, 10), stint(late, 6), stint(leftBefore, 0, 3))
        val current = mapOf(stayed to t(0), late to t(6), unrecorded to t(1))
        assertEquals(setOf(stayed, left, unrecorded), PrestigeRolePlan.rosterAt(t(5), stints, current))
    }

    @Test
    fun `a member holds only their highest tier's role`() {
        val roles = (1..6).associateWith { "r$it" }
        assertEquals(PrestigeRolePlan.Sync("r3", setOf("r1", "r2", "r4", "r5", "r6")), PrestigeRolePlan.sync(3, roles))
        assertEquals(PrestigeRolePlan.Sync(null, setOf("r1", "r2")), PrestigeRolePlan.sync(3, mapOf(1 to "r1", 2 to "r2")))
    }
}
