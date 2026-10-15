package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildDialogCardsTest {
    private val resolver = NexoGlyphResolver { id -> ResolvedNexoGlyph("\uE500", "lumaguilds:dialogs", false, id) }

    @Test
    fun panelRetainsEverySummaryLine() {
        val cards = assertNotNull(GuildDialogCards.resolve(resolver, "enthusia"))
        val text = PlainTextComponentSerializer.plainText().serialize(
            cards.render(listOf(Component.text("Everlights"), Component.text("Joining fee: 100 coins"))),
        )
        assertTrue("Everlights" in text)
        assertTrue("Joining fee: 100 coins" in text)
        assertTrue(text.endsWith("\n\uE500"))
    }

    @Test
    fun partialCardsFallBackWithoutDisablingExistingSkin() {
        val partial = NexoGlyphResolver { id -> if (id.endsWith("card_line")) null else resolver.resolve(id) }
        val skin = assertNotNull(GuildDialogTheme(partial).resolve(net.lumalyte.lg.utils.GuiTheme.ENTHUSIA))
        assertNull(skin.card(listOf(Component.text("Still readable"))))
    }

    @Test
    fun invalidCardIdentityOrEmojiIsRejected() {
        for (glyph in listOf(
            ResolvedNexoGlyph("\uE500", "minecraft:default", false),
            ResolvedNexoGlyph("\uE500", "lumaguilds:dialogs", true),
            ResolvedNexoGlyph("\uE500", "lumaguilds:dialogs", false, "other"),
        )) assertNull(GuildDialogCards.resolve({ glyph }, "enthusia"))
    }
}
