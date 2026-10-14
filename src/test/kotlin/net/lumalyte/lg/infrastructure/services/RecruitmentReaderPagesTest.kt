package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RecruitmentReaderPagesTest {
    @Test
    fun wordsStayTogether() {
        val words = "I enjoy building towns and helping with guild quests."
        val rows = recruitmentReaderPages(listOf(words)).single().lines()
        assertTrue(rows.all { it.codePointCount(0, it.length) <= 24 })
        assertEquals(words, rows.joinToString(""))
        assertTrue(rows.dropLast(1).all { it.endsWith(" ") })
    }

    @Test
    fun unicodeTextIsRetained() {
        val answer = "😀漢字".repeat(500)
        val pages = recruitmentReaderPages(listOf(answer))
        assertEquals(answer, pages.joinToString("").replace("\n", ""))
        assertTrue(pages.all { it.lines().size <= 8 })
    }

    @Test
    fun newlineHeavyAnswerIsRetained() {
        val pages = recruitmentReaderPages(listOf("\n".repeat(2_000)))
        assertEquals(2_001, pages.sumOf { it.lines().size })
    }
}
