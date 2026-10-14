package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ANSWER_LINE_COUNT = 1_000
private const val REASON_LINE_COUNT = 150
private const val UNICODE_REPETITIONS = 100
private const val EXPECTED_PAGE_LIMIT = 100

internal class RecruitmentBookPagesTest {
    @Test
    fun multilineContentRetained() {
        val answer = "a\n".repeat(ANSWER_LINE_COUNT)
        val reason = "b\n".repeat(REASON_LINE_COUNT)
        val pages = RecruitmentPresentation.bookPages(listOf(answer, reason), Component.text("BACK"))
        assertTrue(pages.size <= EXPECTED_PAGE_LIMIT)
        val content =
            pages.joinToString("") {
                PlainTextComponentSerializer.plainText().serialize(it).substringBeforeLast("\nBACK").replace("\n", "")
            }
        assertEquals(answer.replace("\n", "") + reason.replace("\n", ""), content)
    }

    @Test
    fun unicodePagesRetainContent() {
        val answer = "😀漢字".repeat(UNICODE_REPETITIONS)
        val pages = RecruitmentPresentation.bookPages(listOf(answer), Component.text("BACK"))
        val content =
            pages.joinToString("") {
                PlainTextComponentSerializer.plainText().serialize(it).substringBeforeLast("\nBACK").replace("\n", "")
            }
        assertEquals(answer, content)
    }
}
