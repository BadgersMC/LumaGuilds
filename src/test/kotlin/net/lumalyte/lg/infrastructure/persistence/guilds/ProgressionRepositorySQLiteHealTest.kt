package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.ProgressionCurve
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression: `GuildProgression.create()` hardcoded a different formula than the
 * service curve, so fresh guilds stored `experience_for_next_level = 1200` while
 * every guild that had earned XP stored 2369 (level 1). The preload heal rewrites
 * exactly those rows — only when the row is otherwise internally consistent.
 */
class ProgressionRepositorySQLiteHealTest {

    private val curve = ProgressionCurve(baseXp = 800.0, exponent = 1.3, linearBonusPerLevel = 200)

    @Test
    fun `heals fresh guilds with the hardcoded 1200 value`() {
        // fresh guild: 0 XP, level 1, 0 this-level, stored 1200 (old create() formula)
        assertTrue(ProgressionRepositorySQLite.hasStaleNextLevel(0, 1, 0, 1200, curve))
    }

    @Test
    fun `does not touch rows already on the curve`() {
        // fresh guild with the correct curve value
        assertFalse(ProgressionRepositorySQLite.hasStaleNextLevel(0, 1, 0, 2369, curve))
    }

    @Test
    fun `heals stale rows whose totals are consistent`() {
        // stale next (1200 != 3936 for level 2) AND total == cumulative + this -> heal
        assertTrue(ProgressionRepositorySQLite.hasStaleNextLevel(2369 + 100, 2, 100, 1200, curve))
    }

    @Test
    fun `heals stale-but-otherwise-consistent rows`() {
        // guild at level 1 with XP, still holding the old hardcoded 1200
        assertTrue(ProgressionRepositorySQLite.hasStaleNextLevel(500, 1, 500, 1200, curve))
    }

    @Test
    fun `never rewrites internally inconsistent rows`() {
        // total doesn't match cumulative thresholds + this-level -> leave alone
        assertFalse(ProgressionRepositorySQLite.hasStaleNextLevel(9999, 1, 500, 1200, curve))
    }
}
