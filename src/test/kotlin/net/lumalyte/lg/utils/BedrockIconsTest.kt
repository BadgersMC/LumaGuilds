package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Bedrock icon tagging and plain-title cleanup. */
@Suppress("MagicNumber")
internal class BedrockIconsTest {
    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)

    /** Themed titles lose the background glyph and shifts but keep the text. */
    @Test
    fun themedTitleKeepsOnlyText() {
        val raw = MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 3, "Guild Actions")
        assertTrue(BedrockIcons.isThemedTitle(raw))
        assertEquals("Guild Actions", plain(BedrockIcons.plainTitle(Component.text(raw))))
    }

    /** Other plugins' titles are left alone. */
    @Test
    fun foreignTitlesUntouched() {
        assertFalse(BedrockIcons.isThemedTitle("Crate Rewards"))
        assertFalse(BedrockIcons.isThemedTitle("<glyph:shop_bg>Shop"))
    }

    /** Pdc key matches the raw custom_data path the packet hook reads. */
    @Test
    fun pdcKeyMatchesRawPath() {
        assertEquals(BedrockIcons.PDC_KEY, BedrockIcons.FALLBACK_KEY.toString())
    }
}
