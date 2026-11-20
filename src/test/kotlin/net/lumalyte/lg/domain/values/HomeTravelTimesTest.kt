package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-179: Quick Travel shortens both the home countdown and the cooldown. */
class HomeTravelTimesTest {
    @Test
    fun `no Quick Travel keeps the 5 second countdown`() {
        assertEquals(5, HomeTravelTimes.countdownSeconds(1.0))
    }

    @Test
    fun `Quick Travel 1 to 5 count down 4, 4, 3, 3, 2 seconds`() {
        assertEquals(listOf(4, 4, 3, 3, 2), listOf(0.9, 0.8, 0.7, 0.6, 0.5).map(HomeTravelTimes::countdownSeconds))
    }

    @Test
    fun `the countdown never drops below 1 second or rises above 5`() {
        assertEquals(1, HomeTravelTimes.countdownSeconds(0.0))
        assertEquals(5, HomeTravelTimes.countdownSeconds(3.0))
        assertEquals(5, HomeTravelTimes.countdownSeconds(Double.NaN))
    }

    @Test
    fun `the cooldown scales the configured base`() {
        assertEquals(60L, HomeTravelTimes.cooldownSeconds(60, 1.0))
        assertEquals(30L, HomeTravelTimes.cooldownSeconds(60, 0.5))
        assertEquals(0L, HomeTravelTimes.cooldownSeconds(-5, 0.5))
    }
}
