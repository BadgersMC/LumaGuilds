package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuildDescriptionContent
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val currentDescription = guildService.getDescription(guild.id) ?: ""
        val config = getBedrockConfig()
        val descIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.description_editor.title", "guild" to guild.name))
            .apply { descIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.description_editor.instructions"))
            .label(createCurrentDescriptionSection(currentDescription))
            .input(
                lang.bedrock("bedrock.description_editor.input.label"),
                lang.bedrock("bedrock.description_editor.input.placeholder"),
                currentDescription
            )
            .label(lang.bedrock("bedrock.description_editor.format_info"))
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
            lang.bedrock(
                "bedrock.description_editor.current",
                "description" to GuildDescriptionContent.plainText(description),
            )
        } else {
            lang.bedrock("bedrock.description_editor.none")
        }
    }

    private fun handleDescriptionUpdate(newDescription: String) {
        val trimmedDescription = newDescription.trim()

        // Validate description
        val validationError = validateDescription(trimmedDescription)
        if (validationError != null) {
            player.sendMessage(lang.msg("bedrock.description_editor.feedback.validation", "error" to validationError))
            bedrockNavigator.goBack()
            return
        }

        // Update description
        val success = guildService.setDescription(
            guild.id,
            trimmedDescription.ifEmpty { null },
            player.uniqueId,
        )
        if (success) {
            player.sendMessage(lang.msg("bedrock.description_editor.feedback.updated"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.description_editor.feedback.failed"))
            bedrockNavigator.goBack()
        }
    }

    private fun validateDescription(description: String?): String? {
        when (val failure = GuildDescriptionContent.validationFailure(description)) {
            is GuildDescriptionContent.Failure.TooLong ->
                return lang.bedrock("bedrock.description_editor.validation.too_long")
            is GuildDescriptionContent.Failure.InteractiveTag ->
                return lang.bedrock(
                    "bedrock.description_editor.validation.interactive_tag",
                    "tag" to failure.tagName,
                )
            is GuildDescriptionContent.Failure.InvalidFormat ->
                return lang.bedrock("bedrock.description_editor.validation.invalid_format")
            null -> Unit
        }

        if (description.isNullOrEmpty()) return null

        // Preserve the existing Bedrock-side language filter.
        val inappropriate = listOf("fuck", "shit", "damn", "bitch", "ass")
        if (inappropriate.any { description.lowercase().contains(it) }) {
            return lang.bedrock("bedrock.description_editor.validation.inappropriate")
        }

        return null
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
