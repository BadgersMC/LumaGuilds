package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class GuildDialogIconLabelTest {
    @Test
    fun usesExactPackIconWithItsOwnFontAndPreservesLabel() {
        val text = Component.text("My Applications")
        val result =
            guildDialogIconLabel(text, "lg_description") { id ->
                assertEquals("guild_dialog_icon_lg_description", id)
                ResolvedNexoGlyph("\uA050", "lumaguilds:dialog_icons", false, id)
            }
        assertEquals(Key.key("lumaguilds:dialog_icons"), result.children().first().font())
        assertEquals(text, result.children().last())
    }

    @Test
    fun missingOrWrongFontOrEmojiKeepsReadableTextWithoutBrokenGlyph() {
        val text = Component.text("Back")
        for (glyph in listOf(
            null,
            ResolvedNexoGlyph("x", "nexo:default", false),
            ResolvedNexoGlyph("x", "lumaguilds:dialog_icons", true),
            ResolvedNexoGlyph("xx", "lumaguilds:dialog_icons", false),
            ResolvedNexoGlyph("x", "lumaguilds:dialog_icons", false, "wrong_id"),
        )) {
            assertSame(text, guildDialogIconLabel(text, "lg_back") { glyph })
        }
    }
}
