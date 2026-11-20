package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-165: prestige tiers and counts read as Roman numerals. */
class PrestigeNumeralTest {
    @Test
    fun `tiers one to six are Roman numerals`() {
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI"), (1..6).map(PrestigeNumeral::of))
    }

    @Test
    fun `zero stays 0 and anything out of range stays a plain number`() {
        assertEquals("0", PrestigeNumeral.of(0))
        assertEquals("7", PrestigeNumeral.of(7))
        assertEquals("-1", PrestigeNumeral.of(-1))
    }
}
