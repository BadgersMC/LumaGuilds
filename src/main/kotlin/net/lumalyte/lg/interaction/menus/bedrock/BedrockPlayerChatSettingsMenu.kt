package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

internal class BedrockPlayerChatSettingsMenu(navigator: MenuNavigator, player: Player, logger: Logger) : BaseBedrockMenu(navigator, player, logger) {
    private val service: ChatService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun shouldCacheForm() = false
    override fun getForm(): Form {
        val settings = service.getVisibilitySettings(player.uniqueId)
        return SimpleForm.builder().title(lang.bedrock("community.chat.settings"))
            .content(lang.bedrock("community.chat.description"))
            .button(lang.bedrock("community.chat.global_button", "state" to state(settings.globalChatVisible)))
            .button(lang.bedrock("community.chat.indicator_button", "state" to state(settings.destinationIndicator)))
            .button(lang.bedrock("menu.common.item.back.name"))
            .validResultHandler { response ->
                val selected = response.clickedButtonId()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    if (selected == 2) bedrockNavigator.goBack() else {
                        val current = service.getVisibilitySettings(player.uniqueId)
                        val changed = if (selected == 0) current.copy(globalChatVisible = !current.globalChatVisible)
                            else current.copy(destinationIndicator = !current.destinationIndicator)
                        if (!service.updateVisibilitySettings(player.uniqueId, changed)) player.sendMessage(lang.msg("community.chat.failed"))
                        open()
                    }
                })
            }.closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable { if (player.isOnline) bedrockNavigator.goBack() })
            }.build()
    }
    private fun state(enabled: Boolean) = if (enabled) lang.bedrock("community.chat.enabled") else lang.bedrock("community.chat.disabled")
    override fun handleResponse(player: Player, response: Any?) = Unit
}
