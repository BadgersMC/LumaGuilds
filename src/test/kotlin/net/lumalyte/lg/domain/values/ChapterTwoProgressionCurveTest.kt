package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * REQ-049 regression contract for the permanent Chapter 2 curve.
 *
 * These literal anchors catch the legacy 800/1.3/200 curve, an off-by-one
 * interpretation of target level L, and any return of legacy level 101.
 */
class ChapterTwoProgressionCurveTest {

    private val curve = ProgressionCurve(
        baseXp = 500.0,
        exponent = 1.15,
        linearBonusPerLevel = 150,
    )

    @Test
    fun `target-level formula uses exact chapter two anchors`() {
        assertEquals(1_409, curve.experienceForNextLevel(1))
        assertEquals(114_763, curve.experienceForNextLevel(99))
        assertEquals(5_446_893, curve.totalExperienceForLevel(100))
    }

    @Test
    fun `permanent level never exceeds one hundred`() {
        assertEquals(100, curve.levelFromExperience(5_446_893))
        assertEquals(100, curve.levelFromExperience(Int.MAX_VALUE))
    }

    @Test
    fun `level one hundred has no next-level progress`() {
        assertEquals(0, curve.experienceForNextLevel(100))
        assertEquals(0, curve.experienceInCurrentLevel(5_446_893))
        assertEquals(0, curve.experienceInCurrentLevel(Int.MAX_VALUE))
    }
}
