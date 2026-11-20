package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CenteredRowTest {
    @Test
    fun `items sit in the middle of the row`() {
        assertEquals(emptyList(), CenteredRow.slots(0))
        assertEquals(listOf(4), CenteredRow.slots(1))
        assertEquals(listOf(3, 4, 5), CenteredRow.slots(3))
        assertEquals(listOf(3, 4, 5, 6), CenteredRow.slots(4))
        assertEquals((0..8).toList(), CenteredRow.slots(9))
        assertEquals((0..8).toList(), CenteredRow.slots(12), "a full row never overflows")
    }
}
