package net.lumalyte.lg.domain.achievements

import org.junit.jupiter.api.Test
import kotlin.test.*

internal class GuildCombatMilestonesTest {
    @Test fun `empty and complete snapshots use exactly eight stable predicates`() {
        assertEquals(0, GuildCombatMilestones.evaluate(0, 0, 0, 0.0, 0, 0).count { it.achieved })
        val all = GuildCombatMilestones.evaluate(1000, 1, 999, 1000.0, 25, 5)
        assertEquals(8, all.size)
        assertEquals(8, all.map { it.key }.distinct().size)
        assertEquals(8, all.count { it.achieved })
    }
    @Test fun `first kill and strict positive net differ from old summary conditions`() {
        val badges = GuildCombatMilestones.evaluate(1, 1, 0, 1.0, 1, 0).associateBy { it.key }
        assertTrue(badges.getValue("first_blood").achieved)
        assertTrue(badges.getValue("well_rounded").achieved)
        assertFalse(badges.getValue("net_positive").achieved)
        assertFalse(badges.getValue("centurion").achieved)
        assertFalse(GuildCombatMilestones.evaluate(1, 1, 1, 0.0, 1, 0).last().achieved)
    }
    @Test fun `war boundaries preserve existing conditions independently of combat totals`() {
        val before = GuildCombatMilestones.evaluate(99, 0, -1, 0.0, 9, 4).associateBy { it.key }
        assertFalse(before.getValue("survivor").achieved)
        assertFalse(before.getValue("champion").achieved)
        assertFalse(before.getValue("veteran").achieved)
        val after = GuildCombatMilestones.evaluate(100, 0, 1, 1.0, 10, 5).associateBy { it.key }
        assertTrue(after.getValue("survivor").achieved)
        assertTrue(after.getValue("champion").achieved)
        assertFalse(after.getValue("veteran").achieved)
    }
}
