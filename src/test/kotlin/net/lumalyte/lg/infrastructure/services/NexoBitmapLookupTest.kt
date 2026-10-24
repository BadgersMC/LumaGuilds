package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.MenuTitleGlyphs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NexoBitmapLookupTest {
    class Glyph(private val unicode: String) {
        fun getUnicodes() = listOf(unicode)
        fun getFont() = Key.key("nexo:default")
        fun glyphComponent(): Component = error("Do not invoke version-dependent presentation API")
    }
    class Fonts {
        fun glyphFromID(id: String): Glyph? = when (id) {
            "guild_bg_enthusia_3_row" -> Glyph("\uA042")
            "guild_inventory_label_enthusia_3_row" -> Glyph("\uE102")
            else -> null
        }
    }
    class Provider { fun fontManager() = Fonts() }

    @Test fun liveBitmapIdentityPreservesFontAndDoesNotUseFallback() {
        val provider = Provider()
        assertEquals(Component.text("\uA042").font(Key.key("nexo:default"))
            .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
            registeredNexoBitmap(provider, "guild_bg_enthusia_3_row"))
        assertNull(registeredNexoBitmap(provider, "missing"))
    }

    @Test fun separateNexoSpacingNodeDoesNotPreventPlateInsertion() {
        val title = Component.text("\uA042").font(Key.key("nexo:default"))
            .append(Component.text("\uF801").font(Key.key("nexo:space")))
            .append(Component.text("Statistics"))
        val lookup = { id: String -> registeredNexoBitmap(Provider(), id) }
        val result = MenuTitleGlyphs.withGlyphFonts(title, lookup)
        assertEquals("\uA042\uE000\uE102\uF801Statistics",
            PlainTextComponentSerializer.plainText().serialize(result))
        assertEquals(result, MenuTitleGlyphs.withGlyphFonts(result, lookup))
    }
}
