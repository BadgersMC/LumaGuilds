package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class GuildDialogAlignmentTest {
    @Test
    fun allBackgroundRowsHaveTheSameAdvanceBeforeNativeCentering() {
        val resolver = NexoGlyphResolver { id -> ResolvedNexoGlyph("\uE500", "lumaguilds:dialogs", false, id) }
        val card = assertNotNull(GuildDialogCards.resolve(resolver, "enthusia"))
        val text =
            PlainTextComponentSerializer.plainText().serialize(
                assertNotNull(
                    card.render(
                        listOf(
                            Component.text("Wi"),
                            Component.text("Review the details before choosing an action."),
                        ),
                    ),
                ),
            )
        val widths =
            javaClass.getResourceAsStream("/dialogs/default-font-advances.tsv")!!.bufferedReader().useLines {
                it.filterNot { line -> line.startsWith("#") }.associate { line ->
                    val (code, width) = line.split('\t')
                    code.toInt(16) to width.toInt()
                }
            }
        val rows =
            text.split('\n').map { line ->
                line.codePoints().toArray().sumOf { code ->
                    when (code) {
                        0xE500 -> 514
                        0xE000 -> -514
                        0xE001 -> 8
                        in 0xE010..0xE01A -> 1 shl (code - 0xE010)
                        else -> widths.getValue(code)
                    }
                }
            }
        assertEquals(
            setOf(514),
            rows.toSet(),
            "Native centering must not move a background when the text length changes",
        )
    }
}
