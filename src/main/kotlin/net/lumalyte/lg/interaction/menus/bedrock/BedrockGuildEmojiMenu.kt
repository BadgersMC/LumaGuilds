package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild emoji customization menu using Cumulus CustomForm
 * Allows setting and clearing guild emoji with format validation and preview
 */
class BedrockGuildEmojiMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val emojiIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.emoji.title")} - ${guild.name}")
            .apply { emojiIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.emoji.description"))
            .label(createCurrentEmojiSection())
            .input(
                bedrockLocalization.getBedrockString(player, "guild.emoji.input.label"),
                bedrockLocalization.getBedrockString(player, "guild.emoji.input.placeholder"),
                getCurrentEmojiName() ?: ""
            )
            .label(bedrockLocalization.getBedrockString(player, "guild.emoji.format.info"))
            .label(createPreviewSection())
            .validResultHandler { response ->
                handleFormResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun createCurrentEmojiSection(): String {
        val currentEmoji = guildService.getEmoji(guild.id)
        val displayEmoji = currentEmoji ?: bedrockLocalization.getBedrockString(player, "guild.emoji.not.set")

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.emoji.current")}: $displayEmoji
        """.trimMargin()
    }

    private fun createPreviewSection(): String {
        val previewEmoji = getCurrentEmojiName()?.let { ":$it:" } ?: bedrockLocalization.getBedrockString(player, "guild.emoji.not.set")

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.emoji.preview")}
            |${bedrockLocalization.getBedrockString(player, "guild.emoji.chat.preview")}: [${player.name}] $previewEmoji Hello!
        """.trimMargin()
    }

    private fun getCurrentEmojiName(): String? {
        val currentEmoji = guildService.getEmoji(guild.id)
        return currentEmoji?.let {
            // Extract emoji name from format :emoji_name:
            if (it.startsWith(":") && it.endsWith(":")) {
                it.substring(1, it.length - 1)
            } else {
                it
            }
        }
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val emojiName = response.next() as? String ?: ""

            // Validate and process the emoji
            when {
                emojiName.isBlank() -> {
                    // Clear emoji
                    clearEmoji()
                }
                validateEmojiFormat(emojiName) -> {
                    // Save emoji
                    saveEmoji(emojiName)
                }
                else -> {
                    // Show validation error and reopen menu
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.save.failed"))
                    bedrockNavigator.openMenu(BedrockGuildEmojiMenu(menuNavigator, player, guild, logger))
                }
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error handling emoji form response: ${e.message}")
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.save.failed"))
            bedrockNavigator.goBack()
        }
    }

    private fun validateEmojiFormat(emojiName: String): Boolean {
        // Basic validation for emoji name format
        if (emojiName.isBlank()) return false

        // Check length (reasonable limit for emoji names)
        if (emojiName.length > 20) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.validation.too.long"))
            return false
        }

        // Check for basic format issues
        if (!emojiName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.validation.invalid.chars"))
            return false
        }

        return true
    }

    private fun saveEmoji(emojiName: String) {
        val emojiFormat = ":$emojiName:"

        val success = guildService.setEmoji(guild.id, emojiFormat, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.save.success"))
            player.sendMessage("New emoji: $emojiFormat")
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.save.failed"))
        }

        bedrockNavigator.goBack()
    }

    private fun clearEmoji() {
        val success = guildService.setEmoji(guild.id, null, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.clear.success"))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.emoji.clear.failed"))
        }

        bedrockNavigator.goBack()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
