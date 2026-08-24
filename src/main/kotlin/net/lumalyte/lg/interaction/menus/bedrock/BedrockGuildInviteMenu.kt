package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.findPlayerByName
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild invite menu using Cumulus CustomForm
 * Allows inviting players to join the guild with input validation
 */
class BedrockGuildInviteMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val inviteIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.invite.title", "guild" to guild.name))
            .apply { inviteIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.invite.description"))
            .input(
                lang.bedrock("bedrock.invite.player.label"),
                lang.bedrock("bedrock.invite.player.placeholder"),
                ""
            )
            .validResultHandler { response ->
                handleFormResponse(response)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleFormResponse(response: org.geysermc.cumulus.response.CustomFormResponse) {
        try {
            onFormResponseReceived()

            val playerName = response.next() as? String ?: ""
            validateAndInvitePlayer(playerName)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error handling invite form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.invite.feedback.error"))
            bedrockNavigator.goBack()
        }
    }

    private fun validateAndInvitePlayer(playerName: String) {
        // Basic validation
        if (playerName.isBlank()) {
            player.sendMessage(lang.msg("bedrock.invite.feedback.required"))
            return
        }

        if (playerName.length < 3) {
            player.sendMessage(lang.msg("bedrock.invite.feedback.too_short", "minimum" to 3))
            return
        }

        if (playerName.equals(player.name, ignoreCase = true)) {
            player.sendMessage(lang.msg("bedrock.invite.feedback.self"))
            bedrockNavigator.goBack()
            return
        }

        // Check if player is online — uses Floodgate-aware lookup so Bedrock names work without the dot prefix
        val targetPlayer = findPlayerByName(playerName)
        if (targetPlayer == null) {
            player.sendMessage(lang.msg("bedrock.invite.feedback.not_found"))
            bedrockNavigator.goBack()
            return
        }

        // Check if player is already in guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("bedrock.invite.feedback.already_member"))
            bedrockNavigator.goBack()
            return
        }

        // Show confirmation dialog
        bedrockNavigator.openMenu(BedrockGuildInviteConfirmationMenu(
            menuNavigator,
            player,
            guild,
            targetPlayer,
            logger
        ))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
