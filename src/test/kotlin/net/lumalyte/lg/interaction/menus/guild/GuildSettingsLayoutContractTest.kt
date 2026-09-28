package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildSettingsLayoutContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildSettingsMenu.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `settings groups identity appearance and access controls without dropping controls`() {
        val identity = source.substringAfter("private fun addGuildInfoSection").substringBefore("private fun createLevelingInfoItem")
        val appearance = source.substringAfter("private fun addAppearanceSection").substringBefore("private fun addLocationModeSection")
        val access = source.substringAfter("private fun addLocationModeSection").substringBefore("private fun openThemeSelector")

        assertTrue(identity.contains("menu.guild_settings.item.name.name"))
        assertTrue(identity.contains("menu.guild_settings.item.description.name"))
        assertTrue(identity.contains("menu.guild_settings.item.tag.name"), "Tag belongs with guild identity")

        assertTrue(appearance.contains("menu.guild_settings.item.banner.name"))
        assertTrue(appearance.contains("menu.guild_settings.item.emoji.name"))
        assertTrue(appearance.contains("menu.guild_settings.item.theme.name"))
        assertFalse(appearance.contains("menu.guild_settings.item.tag.name"))
        assertTrue(appearance.contains("lg_theme_" + "$" + "{guild.guiTheme.name.lowercase()}"))

        listOf(
            "menu.guild_settings.item.homes.name",
            "menu.guild_settings.item.access.name",
            "menu.guild_settings.item.tracking.name",
            "menu.guild_settings.item.members.name",
            "menu.guild_settings.item.mode.name",
        ).forEach { key -> assertTrue(access.contains(key), "Missing access/location control " + key) }
        assertTrue(access.contains("pane.addItem(backGuiItem, 4, 5)"))
    }
}
