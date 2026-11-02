package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class RecruitmentReaderStateTest {
    @Test
    fun stalePageCannotReturnOrAdvance() {
        val reader = RecruitmentReaderState { true }
        val first = reader.beginPage()
        assertTrue(reader.consume(first))
        val second = reader.beginPage()
        assertFalse(reader.consume(first))
        assertTrue(reader.consume(second))
        assertFalse(reader.consume(second))
    }

    @Test
    fun lostAccessRejectsPageActions() {
        var authorized = true
        val reader = RecruitmentReaderState { authorized }
        val page = reader.beginPage()
        authorized = false
        assertFalse(reader.consume(page))
    }

    @Test
    fun closingReaderRejectsCallbacks() {
        val reader = RecruitmentReaderState { true }
        val page = reader.beginPage()
        reader.close()
        assertFalse(reader.consume(page))
    }
}
