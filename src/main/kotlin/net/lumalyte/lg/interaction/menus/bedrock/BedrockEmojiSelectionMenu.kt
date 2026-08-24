package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val emojiIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.legacy("bedrock.emoji_selection.title", "guild" to guild.name))
            .apply { emojiIcon?.let { icon(it) } }
            .label(lang.raw("bedrock.emoji_selection.description"))
            .input(
                lang.raw("bedrock.emoji_selection.input.label"),
                lang.raw("bedrock.emoji_selection.input.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val emojiInput = response.asInput(2)?.trim() ?: ""

                if (emojiInput.isNotEmpty()) {
                    if (nexoEmojiService.isValidEmojiFormat(emojiInput)) {
                        handleEmojiSelection(emojiInput)
                    } else {
                        player.sendMessage(lang.msg("bedrock.emoji_selection.feedback.invalid"))
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

        player.sendMessage(lang.msg("bedrock.emoji_selection.feedback.selected", "emoji" to emoji))
        bedrockNavigator.goBack()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
