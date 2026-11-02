package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildDialogColumnsTest {
    private val cards = assertNotNull(GuildDialogCards.resolve(
        NexoGlyphResolver { id -> ResolvedNexoGlyph("\uE500", "lumaguilds:dialogs", false, id) }, "enthusia"))

    @Test
    fun longLabelsWrapWithoutDroppingTheRightStatus() {
        val rendered = assertNotNull(cards.renderColumns(
            listOf(Component.text("The Everlasting Midnight Lanterns"), Component.text("Building - 24 members")),
            listOf(Component.text("Available"), Component.text("1,200 guild gold"))))
        val text = PlainTextComponentSerializer.plainText().serialize(rendered)
        for (word in listOf("Everlasting", "Midnight", "Lanterns", "Available", "1,200")) assertTrue(word in text)
        assertTrue(text.endsWith("\n\uE500"))
    }

    @Test
    fun unknownFontsUseTheReadableFallback() {
        assertNull(cards.renderColumns(listOf(Component.text("Custom").font(net.kyori.adventure.key.Key.key("other:font"))),
            listOf(Component.text("On"))))
    }
}
