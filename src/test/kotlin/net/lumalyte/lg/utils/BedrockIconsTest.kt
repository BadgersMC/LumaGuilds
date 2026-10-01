package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BedrockIconsTest {
    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)

    @Test
    fun `themed titles lose the background glyph and shifts but keep the text`() {
        val raw = MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 3, "Guild Actions")
        assertTrue(BedrockIcons.isThemedTitle(raw))
        assertEquals("Guild Actions", plain(BedrockIcons.plainTitle(Component.text(raw))))
    }

    @Test
    fun `other plugins' titles are left alone`() {
        assertFalse(BedrockIcons.isThemedTitle("Crate Rewards"))
        assertFalse(BedrockIcons.isThemedTitle("<glyph:shop_bg>Shop"))
    }

    @Test
    fun `pdc key matches the raw custom_data path the packet hook reads`() {
        assertEquals(BedrockIcons.PDC_KEY, BedrockIcons.FALLBACK_KEY.toString())
    }
}
