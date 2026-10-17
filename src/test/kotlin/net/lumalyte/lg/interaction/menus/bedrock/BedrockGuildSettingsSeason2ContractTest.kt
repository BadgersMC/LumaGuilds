package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class BedrockGuildSettingsSeason2ContractTest {
    private val source = File(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildSettingsMenu.kt"
    ).readText()

    @Test
    fun `Bedrock settings expose persisted Season 2 controls`() {
        listOf(
            "guildService.getGuild(guild.id)",
            "isOpen",
            "trackingEnabled",
            "guiTheme",
            "GuiTheme.SELECTABLE",
            "setOpen(",
            "setTrackingEnabled(",
            "setGuiTheme("
        ).forEach { expected ->
            assertTrue(source.contains(expected), "missing Season 2 settings behavior: $expected")
        }
    }

    @Test
    fun `Season 2 management controls use shared authorization and current state`() {
        assertTrue(source.contains("BedrockGuildAuthorization"))
        assertTrue(source.contains("authorization.canManageGuildSettings"))
        assertTrue(source.contains("guild.isOpen"))
        assertTrue(source.contains("guild.trackingEnabled"))
        assertTrue(source.contains("guild.guiTheme"))
    }

    @Test
    fun `Bedrock settings mutations return to server thread`() {
        assertTrue(source.contains("Bukkit.getScheduler().runTask"))
    }
}
