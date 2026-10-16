package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CARD_TEST_FONT = "lumaguilds:dialogs"
private const val CARD_TEST_CHARACTER = "\uE500"

internal class GuildDialogCardsTest {
    private val resolver = NexoGlyphResolver { id -> ResolvedNexoGlyph(CARD_TEST_CHARACTER, CARD_TEST_FONT, false, id) }

    @Test
    fun panelRetainsEverySummaryLine() {
        val cards = assertNotNull(GuildDialogCards.resolve(resolver, "enthusia"))
        val text =
            PlainTextComponentSerializer.plainText().serialize(
                assertNotNull(
                    cards.render(listOf(Component.text("Everlights"), Component.text("Joining fee: 100 coins"))),
                ),
            )
        assertTrue("Everlights" in text)
        assertTrue("Joining fee: 100 coins" in text)
        assertTrue(text.endsWith("\n\uE500"))
    }

    @Test
    fun partialCardsKeepPlainContent() {
        val partial = NexoGlyphResolver { id -> if (id.endsWith("card_line")) null else resolver.resolve(id) }
        val skin = assertNotNull(GuildDialogTheme(partial).resolve(net.lumalyte.lg.utils.GuiTheme.ENTHUSIA))
        assertNull(skin.card(listOf(Component.text("Still readable"))))
    }

    @Test
    fun rejectsInvalidCards() {
        for (glyph in listOf(
            ResolvedNexoGlyph(CARD_TEST_CHARACTER, "minecraft:default", false),
            ResolvedNexoGlyph(CARD_TEST_CHARACTER, CARD_TEST_FONT, true),
            ResolvedNexoGlyph(CARD_TEST_CHARACTER, CARD_TEST_FONT, false, "other"),
        )) assertNull(GuildDialogCards.resolve({ glyph }, "enthusia"))
    }
}
