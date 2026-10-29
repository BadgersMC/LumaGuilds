package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildDialogThemeCoverageTest {
    private val themed = GuiTheme.SELECTABLE.filter { it.hasBackground }
    private val font = "lumaguilds:dialogs"

    @Test
    fun everySelectableThemeUsesItsOwnFrameAndCardAssets() {
        assertEquals(7, themed.size)
        for (theme in themed) {
            val requested = mutableListOf<String>()
            val resolver = NexoGlyphResolver { id ->
                requested += id
                ResolvedNexoGlyph("\uE400", font, false, id)
            }
            val skin = assertNotNull(GuildDialogTheme(resolver).resolve(theme), theme.name)
            val card = assertNotNull(skin.card(listOf(Component.text("Guild details"))), theme.name)
            val columns = assertNotNull(skin.columns(listOf(Component.text("Guild")), listOf(Component.text("Ready"))), theme.name)
            val text = PlainTextComponentSerializer.plainText()
            assertTrue("Guild details" in text.serialize(card), theme.name)
            assertTrue("Guild" in text.serialize(columns) && "Ready" in text.serialize(columns), theme.name)
            val prefix = "guild_dialog_${theme.name.lowercase()}_"
            for (part in listOf("header", "divider", "footer", "marker", "card_top", "card_line", "card_bottom")) {
                assertTrue(prefix + part in requested, "${theme.name}: missing $part")
            }
        }
    }

    @Test
    fun missingCardArtworkFallsBackWithoutDiscardingTheFrameForEveryTheme() {
        for (theme in themed) {
            val resolver = NexoGlyphResolver { id ->
                if (id.endsWith("card_line")) null else ResolvedNexoGlyph("\uE400", font, false, id)
            }
            val skin = assertNotNull(GuildDialogTheme(resolver).resolve(theme), theme.name)
            assertNull(skin.card(listOf(Component.text("Guild details"))), theme.name)
            assertNull(skin.columns(listOf(Component.text("Guild")), listOf(Component.text("Ready"))), theme.name)
        }
    }
}

