package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildSettingsThemeSelectorContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildSettingsMenu.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `three row selector uses art swatches wrapped slots and dedicated heading`() {
        assertTrue(Regex("""ChestGui\(\s*3,""").containsMatchIn(source))
        assertTrue(source.contains("menu.guild_settings.theme_selector.title"))
        assertTrue(source.contains("lg_theme_"))
        assertFalse(source.contains("index * 1 + index"))
        assertTrue(source.contains("}, index % 9, index / 9)"))
        assertTrue(source.contains("}, 4, 2)"))
    }

    @Test
    fun `theme change result is validated before success feedback`() {
        assertTrue(source.contains("if (guildService.setGuiTheme("))
    }

    @Test
    fun `vanilla style is offered as a plain chest swatch`() {
        assertTrue(source.contains("GuiTheme.VANILLA"))
        assertTrue(source.contains("Material.CHEST"))
    }
}
