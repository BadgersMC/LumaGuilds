package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RecruitmentBookPagesTest {
    @Test
    fun boundedMultilineAnswersAndReasonsAreNotSilentlyTruncated() {
        val answer = "a\n".repeat(1000)
        val reason = "b\n".repeat(150)
        val pages = RecruitmentPresentation.bookPages(listOf(answer, reason), Component.text("BACK"))
        assertTrue(pages.size <= 100)
        val content = pages.joinToString("") {
            PlainTextComponentSerializer.plainText().serialize(it).substringBeforeLast("\nBACK").replace("\n", "")
        }
        assertEquals(answer.replace("\n", "") + reason.replace("\n", ""), content)
    }

    @Test
    fun unicodeCodePointsSurviveWrappingAcrossPages() {
        val answer = "😀漢字".repeat(100)
        val pages = RecruitmentPresentation.bookPages(listOf(answer), Component.text("BACK"))
        val content = pages.joinToString("") {
            PlainTextComponentSerializer.plainText().serialize(it).substringBeforeLast("\nBACK").replace("\n", "")
        }
        assertEquals(answer, content)
    }
}
