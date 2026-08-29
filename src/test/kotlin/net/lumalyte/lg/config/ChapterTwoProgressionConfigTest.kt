package net.lumalyte.lg.config

import net.lumalyte.lg.domain.values.ProgressionCurve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Catches a bundled or in-memory fallback to the legacy progression curve. */
class ChapterTwoProgressionConfigTest {

    @Test
    fun `default config produces the chapter two level one hundred threshold`() {
        val config = ProgressionConfig()
        val curve = ProgressionCurve(
            baseXp = config.baseXp,
            exponent = config.levelExponent,
            linearBonusPerLevel = config.linearBonusPerLevel,
            maxLevel = config.maxLevel,
        )

        assertEquals(100, config.maxLevel)
        assertEquals(5_446_893, curve.totalExperienceForLevel(config.maxLevel))
    }
}
