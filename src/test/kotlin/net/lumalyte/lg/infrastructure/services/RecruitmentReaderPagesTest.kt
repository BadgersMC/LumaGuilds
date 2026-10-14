package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PAGE_COLUMNS = 24
private const val PAGE_ROWS = 8
private const val UNICODE_REPEATS = 500
private const val NEWLINES = 2_000

internal class RecruitmentReaderPagesTest {
    @Test
    fun wordsStayTogether() {
        val words = "I enjoy building towns and helping with guild quests."
        val rows = recruitmentReaderPages(listOf(words)).single().lines()
        assertTrue(rows.all { it.codePointCount(0, it.length) <= PAGE_COLUMNS })
        assertEquals(words, rows.joinToString(""))
        assertTrue(rows.dropLast(1).all { it.endsWith(" ") })
    }

    @Test
    fun unicodeTextIsRetained() {
        val answer = "😀漢字".repeat(UNICODE_REPEATS)
        val pages = recruitmentReaderPages(listOf(answer))
        assertEquals(answer, pages.joinToString("").replace("\n", ""))
        assertTrue(pages.all { it.lines().size <= PAGE_ROWS })
    }

    @Test
    fun newlineHeavyAnswerIsRetained() {
        val pages = recruitmentReaderPages(listOf("\n".repeat(NEWLINES)))
        assertEquals(NEWLINES + 1, pages.sumOf { it.lines().size })
    }
}
