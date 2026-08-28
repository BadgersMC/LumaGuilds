package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [ProgressionCurve] — the single source of truth for guild XP math.
 *
 * Anchor values locked from the production DB: every stored
 * `experience_for_next_level` that was ever written by the service matches this
 * curve (level 1 -> 2369, level 3 -> 5650, ...).
 */
class ProgressionCurveTest {

    private val curve = ProgressionCurve(baseXp = 800.0, exponent = 1.3, linearBonusPerLevel = 200)

    @Test
    fun `experience for next level matches the service curve`() {
        // 800 * 2^1.3 + 2*200 = 2369.6 -> 2369 (the value every non-fresh guild stored)
        assertEquals(2369, curve.experienceForNextLevel(1))
        // 800 * 4^1.3 + 4*200 = 5650.3 -> 5650
        assertEquals(5650, curve.experienceForNextLevel(3))
        // level 0 -> threshold for level 1: 800 * 1^1.3 + 1*200 = 1000
        assertEquals(1000, curve.experienceForNextLevel(0))
    }

    @Test
    fun `cumulative totals`() {
        assertEquals(0, curve.totalExperienceForLevel(1))
        assertEquals(2369, curve.totalExperienceForLevel(2))
        assertEquals(2369 + 3936, curve.totalExperienceForLevel(3)) // 3936 = 800*3^1.3 + 600
    }

    @Test
    fun `level from experience`() {
        assertEquals(1, curve.levelFromExperience(0))
        assertEquals(1, curve.levelFromExperience(2368))
        assertEquals(2, curve.levelFromExperience(2369))
        assertEquals(2, curve.levelFromExperience(5000))
        assertEquals(3, curve.levelFromExperience(2369 + 3936))
    }

    @Test
    fun `experience in current level`() {
        assertEquals(0, curve.experienceInCurrentLevel(2369))
        assertEquals(100, curve.experienceInCurrentLevel(2469))
        assertEquals(2631, curve.experienceInCurrentLevel(5000)) // 5000 - 2369 (start of level 2)
    }

    @Test
    fun `caps at permanent level 100`() {
        assertEquals(100, curve.levelFromExperience(17_518_681)) // prod max total XP
        assertEquals(100, curve.levelFromExperience(Int.MAX_VALUE / 2))
    }
}
