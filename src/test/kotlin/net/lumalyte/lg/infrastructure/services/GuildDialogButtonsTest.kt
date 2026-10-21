package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import kotlin.test.*

internal class GuildDialogButtonsTest {
    private fun art(theme: GuiTheme) = assertNotNull(GuildDialogButtons.resolve(theme) { id ->
        ResolvedNexoGlyph("\uE031", "lumaguilds:dialog_action_contract", false, id)
    })

    @Test fun everyThemeKeepsTextAndFitsNativeControlWithFocusRim() {
        for (theme in GuiTheme.SELECTABLE.filter { it.hasBackground }) {
            for (width in GuildDialogButtons.WIDTHS) {
                val label = assertNotNull(art(theme).label(Component.text("Guild Leaderboard"), width))
                assertTrue(PlainTextComponentSerializer.plainText().serialize(label).contains("Guild Leaderboard"))
                val measured = assertNotNull(GuildDialogText.rows(label, 4000) { font, code ->
                    assertEquals("lumaguilds:dialog_actions_${theme.name.lowercase()}_v1", font)
                    when (code) {
                        0xE000 -> -2
                        in 0xE001..0xE003 -> -(GuildDialogButtons.WIDTHS[code - 0xE001] - 1) * 2
                        in 0xE010..0xE01A -> (1 shl (code - 0xE010)) * 2
                        else -> (width / 2) * 2
                    }
                }).single()
                assertEquals((width - 2) * 2, measured.second)
                assertNull(label.clickEvent())
            }
        }
    }

    @Test fun unsupportedLabelsAndUninstalledContractKeepNativeRendering() {
        assertNull(GuildDialogButtons.resolve(GuiTheme.VANILLA) { error("Vanilla must not query pack") })
        assertNull(GuildDialogButtons.resolve(GuiTheme.ENTHUSIA) { null })
        assertNull(GuildDialogButtons.resolve(GuiTheme.ENTHUSIA) {
            ResolvedNexoGlyph("x", "wrong:font", false, it)
        })
        assertNull(art(GuiTheme.ENTHUSIA).label(GUILD_DIALOG_CLOSE_LABEL, 180))
        assertNull(art(GuiTheme.ENTHUSIA).label(Component.text("Unknown").font(Key.key("unknown:font")), 180))
        assertNull(art(GuiTheme.ENTHUSIA).label(Component.text("Guild"), 150))
    }

    @Test fun longLabelsAreBoundedWithoutLosingOriginalComponent() {
        val original = Component.text("Guild leaderboard ".repeat(30))
        val label = assertNotNull(art(GuiTheme.ENTHUSIA).label(original, 180))
        assertTrue(PlainTextComponentSerializer.plainText().serialize(label).contains("..."))
        assertEquals("Guild leaderboard ".repeat(30), PlainTextComponentSerializer.plainText().serialize(original))
    }
}
