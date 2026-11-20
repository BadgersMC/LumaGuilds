package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigeCarryOverConfig
import net.lumalyte.lg.config.ProgressionConfig
import net.lumalyte.lg.domain.values.ProgressionCurve
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-175: how much XP a prestige carries into the next climb. */
class PrestigeCarryOverTest {
    private val curve = ProgressionCurve.from(ProgressionConfig(maxLevel = 100))
    private val level100 = curve.totalExperienceForLevel(100)

    @Test
    fun `off by default carries nothing`() {
        assertEquals(0, PrestigeCarryOver.experience(level100 + 50_000, curve, PrestigeCarryOverConfig()))
    }

    @Test
    fun `only XP earned after reaching level 100 carries`() {
        val on = PrestigeCarryOverConfig(enabled = true, landingLevel = 5)
        assertEquals(0, PrestigeCarryOver.experience(level100, curve, on))
        assertEquals(1_234, PrestigeCarryOver.experience(level100 + 1_234, curve, on))
    }

    @Test
    fun `carry is capped at the start of the landing level`() {
        val on = PrestigeCarryOverConfig(enabled = true, landingLevel = 5)
        val cap = curve.totalExperienceForLevel(5)
        assertEquals(cap, PrestigeCarryOver.experience(level100 + cap * 10, curve, on))
        assertEquals(5, curve.levelFromExperience(PrestigeCarryOver.experience(level100 + cap * 10, curve, on)))
    }

    @Test
    fun `a landing level of 1 carries nothing`() {
        assertEquals(0, PrestigeCarryOver.experience(level100 + 99_999, curve, PrestigeCarryOverConfig(enabled = true, landingLevel = 1)))
    }
}
