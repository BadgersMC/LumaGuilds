package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition emoji selection menu using Cumulus CustomForm
 * Allows manually entering emoji placeholder
 */
class BedrockEmojiSelectionMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val parentMenu: net.lumalyte.lg.interaction.menus.guild.GuildEmojiMenu,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val nexoEmojiService: NexoEmojiService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val emojiIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.emoji.selection.title")} - ${guild.name}")
            .apply { emojiIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.emoji.selection.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.emoji.input"),
                ":emoji_name:",
                ""
            )
            .validResultHandler { response ->
                val emojiInput = response.asInput(2)?.trim() ?: ""

                if (emojiInput.isNotEmpty()) {
                    if (nexoEmojiService.isValidEmojiFormat(emojiInput)) {
                        handleEmojiSelection(emojiInput)
                    } else {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.invalid.format"))
                        bedrockNavigator.goBack()
                    }
                } else {
                    bedrockNavigator.goBack()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleEmojiSelection(emoji: String) {
        // Pass the selected emoji back to the parent menu
        parentMenu.passData(mapOf("selectedEmoji" to emoji))

        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.selection.selected", emoji))
        bedrockNavigator.goBack()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
