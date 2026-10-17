package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class GuildToggleIconContractTest {
    private fun source(name: String) = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/$name"
    ).toFile().readText()

    @Test
    fun settingsTogglesRenderCurrentPersistedStateAndRefreshAfterSuccess() {
        val source = source("GuildSettingsMenu.kt")
        assertTrue(source.contains("if (guild.isOpen) \"lg_toggle_on\" else \"lg_toggle_off\""))
        assertTrue(source.contains("if (guild.trackingEnabled) \"lg_toggle_on\" else \"lg_toggle_off\""))
        assertTrue(source.contains("guild = guild.copy(isOpen = newIsOpen)"))
        assertTrue(source.contains("guild = guild.copy(trackingEnabled = newTracking)"))
        assertTrue(source.contains("open()"))
    }

    @Test
    fun allyHomeToggleRendersCurrentAllowedState() {
        val source = source("AllyHomeAccessMenu.kt")
        assertTrue(source.contains("if (on) \"lg_toggle_on\" else \"lg_toggle_off\""))
        assertTrue(source.contains("guildService.setAllyHomeAllowedGuilds"))
    }

    @Test
    fun activeAutomationsStatusIsNotConvertedIntoToggle() {
        val source = source("GuildBankAutomationMenu.kt")
        assertTrue(source.contains("if (activeAutomations.isNotEmpty()) \"lg_bank_active_automations\" else \"lg_bank_inactive_automations\""))
        assertTrue(source.contains("automationPane.addItem(GuiItem(statusItem), 6, 0)"))
    }
}
