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
 * Bedrock Edition guild description editor menu using Cumulus CustomForm
 * Allows editing guild description with validation
 */
class BedrockDescriptionEditorMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()

    override fun getForm(): Form {
        val currentDescription = guildService.getDescription(guild.id) ?: ""
        val config = getBedrockConfig()
        val descIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.description.title")} - ${guild.name}")
            .apply { descIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.description.instructions"))
            .label(createCurrentDescriptionSection(currentDescription))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.description.input.label"),
                bedrockLocalization.getBedrockString(player, "guild.description.input.placeholder"),
                currentDescription
            )
            .label(bedrockLocalization.getBedrockString(player, "guild.description.format.info"))
            .validResultHandler { response ->
                val newDescription = response.asInput(2) ?: ""  // Index 2 is the input field
                handleDescriptionUpdate(newDescription)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun createCurrentDescriptionSection(description: String): String {
        return if (description.isNotEmpty()) {
            """
            |${bedrockLocalization.getBedrockString(player, "guild.description.current")}:
            |$description
            """.trimMargin()
        } else {
            bedrockLocalization.getBedrockString(player, "guild.description.none")
        }
    }

    private fun handleDescriptionUpdate(newDescription: String) {
        val trimmedDescription = newDescription.trim()

        // Validate description
        val validationError = validateDescription(trimmedDescription)
        if (validationError != null) {
            player.sendMessage("§c$validationError")
            bedrockNavigator.goBack()
            return
        }

        // Update description
        val success = guildService.setDescription(guild.id, trimmedDescription, player.uniqueId)
        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.description.updated"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.description.failed"))
            bedrockNavigator.goBack()
        }
    }

    private fun validateDescription(description: String?): String? {
        if (description == null || description.isEmpty()) {
            return null // Empty is allowed
        }

        if (description.length > 200) {
            return bedrockLocalization.getBedrockString(player, "guild.description.too.long")
        }

        // Check for inappropriate content (basic filter)
        val inappropriate = listOf("fuck", "shit", "damn", "bitch", "ass")
        if (inappropriate.any { description.lowercase().contains(it) }) {
            return bedrockLocalization.getBedrockString(player, "guild.description.inappropriate")
        }

        return null
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
