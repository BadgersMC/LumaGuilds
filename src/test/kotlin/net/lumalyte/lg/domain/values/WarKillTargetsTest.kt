package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.WarObjective
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-230/231: a war ends at its own kill target. */
internal class WarKillTargetsTest {
    private fun kills(n: Int) = WarObjective(type = ObjectiveType.KILLS, targetValue = n, description = "kills")

    @Test fun `a war uses its chosen kill objective`() {
        assertEquals(50, WarKillTargets.forWar(setOf(kills(50)), default = 25))
        assertEquals(5, WarKillTargets.forWar(setOf(kills(5)), default = 25))
    }

    @Test fun `a war without a kill objective uses the configured default`() {
        assertEquals(25, WarKillTargets.forWar(emptySet(), default = 25))
        assertEquals(25, WarKillTargets.forWar(setOf(WarObjective(type = ObjectiveType.CLAIMS_CAPTURED, targetValue = 3, description = "c")), 25))
    }

    @Test fun `guilds can pick up to the maximum, including the default`() {
        assertEquals(listOf(5, 10, 25, 50), WarKillTargets.options(default = 25, max = 50))
        assertEquals(listOf(5, 10, 25, 30, 50), WarKillTargets.options(default = 30, max = 50))
        assertEquals(listOf(5, 10, 25), WarKillTargets.options(default = 25, max = 25))
        assertEquals(listOf(5, 10, 25, 50, 100), WarKillTargets.options(default = 25, max = 100))
    }
}
