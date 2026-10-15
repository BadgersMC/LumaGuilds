package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_FONT = "lumaguilds:dialogs"
private const val TEST_CHARACTER = "\uE400"
private const val SPACER_ID = "guild_dialog_spacer"

internal class GuildDialogThemeTest {
    private val names = mutableListOf<String>()
    private val resolver =
        NexoGlyphResolver { name ->
            names += name
            ResolvedNexoGlyph(TEST_CHARACTER, TEST_FONT, false, name)
        }

    @Test
    fun resolvesSelectedArtwork() {
        val skin = assertNotNull(GuildDialogTheme(resolver).resolve(GuiTheme.VOIDLIGHT, true))
        val expected = listOf("header", "divider", "footer", "marker").map { "guild_dialog_voidlight_$it" }
        assertEquals(expected + SPACER_ID, names)
        assertEquals(NamedTextColor.LIGHT_PURPLE, skin.accent)
        val label = skin.label(Component.text("Confirm 100 coins"))
        assertTrue(PlainTextComponentSerializer.plainText().serialize(label).endsWith("Confirm 100 coins"))
    }

    @Test
    fun plainWithoutPack() {
        assertNull(GuildDialogTheme(resolver).resolve(GuiTheme.VANILLA, true))
        assertNull(GuildDialogTheme(resolver).resolve(GuiTheme.ENTHUSIA, false))
        assertTrue(names.isEmpty())
    }

    @Test
    fun incompleteThemeStaysPlain() {
        val partial =
            NexoGlyphResolver { name ->
                if (name.endsWith("footer")) null else resolver.resolve(name)
            }
        assertNull(GuildDialogTheme(partial).resolve(GuiTheme.ENTHUSIA, true))
    }

    @Test
    fun invalidOrEmojiGlyphStaysPlain() {
        for (glyph in listOf(
            ResolvedNexoGlyph(TEST_CHARACTER, "bad font", false),
            ResolvedNexoGlyph(TEST_CHARACTER, TEST_FONT, true),
            ResolvedNexoGlyph("", TEST_FONT, false),
        )) {
            assertNull(GuildDialogTheme { glyph }.resolve(GuiTheme.ENTHUSIA, true))
        }
    }

    @Test
    fun pluginAndLegacyFallback() {
        assertNull(GuildDialogTheme { throw NoClassDefFoundError("Nexo") }.resolve(GuiTheme.ENTHUSIA, true))
        assertNotNull(GuildDialogTheme(resolver).resolve(GuiTheme.NEUTRAL, true))
        assertTrue(names.filterNot { it == SPACER_ID }.all { it.startsWith("guild_dialog_enthusia_") })
    }

    @Test
    fun missingSpacerStaysPlain() {
        val missing =
            NexoGlyphResolver { name ->
                if (name == SPACER_ID) null else resolver.resolve(name)
            }
        assertNull(GuildDialogTheme(missing).resolve(GuiTheme.ENTHUSIA, true))
    }

    @Test
    fun defaultLookupFallsBack() {
        assertNull(GuildDialogTheme().resolve(GuiTheme.ENTHUSIA, false))
        assertNull(GuildDialogTheme().resolve(GuiTheme.ENTHUSIA, true))
    }
}
