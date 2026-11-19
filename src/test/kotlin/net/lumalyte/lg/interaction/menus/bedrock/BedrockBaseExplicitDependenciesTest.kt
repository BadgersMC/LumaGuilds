package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertEquals

class BedrockBaseExplicitDependenciesTest {
    @Test
    fun `explicit dependencies allow base form operations without starting Koin`() {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply {
            bedrock.formTimeoutSeconds = 47
        }
        val lang = mockk<LangService>()
        every { lang.msg("bedrock.common.timeout.expired", "seconds" to 47) } returns Component.text("Expired")
        every { lang.msg("bedrock.common.timeout.reopen") } returns Component.text("Reopen")
        val player = mockk<Player>(relaxed = true)
        val deps = BedrockMenuDependencies(
            menuFactory = mockk<MenuFactory>(),
            localization = mockk<BedrockLocalizationService>(),
            plugin = mockk<Plugin>(),
            lang = lang,
            cache = mockk<FormCacheService>(),
            validation = mockk<FormValidationService>(),
            config = config,
        )
        val menu = object : BaseBedrockMenu(mockk<MenuNavigator>(), player, Logger.getAnonymousLogger(), deps) {
            override fun getForm(): Form = mockk()
            override fun handleResponse(player: Player, response: Any?) = Unit
            fun configuredTimeout(): Int = getBedrockConfig().formTimeoutSeconds
        }
        assertEquals(47, menu.configuredTimeout())
        menu.sendTimeoutMessages(47)
        verify(exactly = 1) { config.loadConfig() }
        verify(exactly = 1) { player.sendMessage(Component.text("Expired")) }
        verify(exactly = 1) { player.sendMessage(Component.text("Reopen")) }
    }
}
