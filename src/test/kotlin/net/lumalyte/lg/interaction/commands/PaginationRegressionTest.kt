package net.lumalyte.lg.interaction.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaginationRegressionTest {
    @Test
    fun `eleven items produce ten on page one and one on page two`() {
        assertEquals(0..9, pageBounds(11, 1))
        assertEquals(10..10, pageBounds(11, 2))
    }

    @Test
    fun `invalid empty and overflowing pages are rejected`() {
        assertNull(pageBounds(0, 1))
        assertNull(pageBounds(10, 0))
        assertNull(pageBounds(10, 2))
        assertNull(pageBounds(10, Int.MAX_VALUE))
    }

    @Test
    fun `custom page size uses one based page numbers`() {
        assertEquals(5..9, pageBounds(12, 2, pageSize = 5))
        assertEquals(10..11, pageBounds(12, 3, pageSize = 5))
    }
}
