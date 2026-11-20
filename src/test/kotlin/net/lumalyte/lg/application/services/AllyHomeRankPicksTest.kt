package net.lumalyte.lg.application.services

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-182: rank picks the host makes for one ally. */
class AllyHomeRankPicksTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val all = setOf(a, b)

    @Test
    fun `turning one rank off from every rank keeps the others`() {
        assertEquals(setOf(b), AllyHomeRankPicks.toggle(null, all, a))
    }

    @Test
    fun `turning the last rank back on returns to every rank`() {
        assertNull(AllyHomeRankPicks.toggle(setOf(b), all, a))
    }

    @Test
    fun `every rank can be turned off`() {
        assertEquals(emptySet(), AllyHomeRankPicks.toggle(setOf(b), all, b))
    }

    @Test
    fun `no pick allows every rank`() {
        assertTrue(AllyHomeRankPicks.isAllowed(null, a))
        assertFalse(AllyHomeRankPicks.isAllowed(setOf(b), a))
    }
}
