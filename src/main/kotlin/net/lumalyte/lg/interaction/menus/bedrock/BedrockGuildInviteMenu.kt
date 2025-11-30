package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
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

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val inviteIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.invite.title")} - ${guild.name}")
            .apply { inviteIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.invite.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.invite.player.name.label"),
                bedrockLocalization.getBedrockString(player, "guild.invite.player.name.placeholder"),
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.error"))
            bedrockNavigator.goBack()
        }
    }

    private fun validateAndInvitePlayer(playerName: String) {
        // Basic validation
        if (playerName.isBlank()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "validation.required"))
            return
        }

        if (playerName.length < 3) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "validation.too.short", 3))
            return
        }

        if (playerName.equals(player.name, ignoreCase = true)) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.validation.cannot.invite.self"))
            bedrockNavigator.goBack()
            return
        }

        // Check if player is online
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.validation.player.not.found"))
            bedrockNavigator.goBack()
            return
        }

        // Check if player is already in guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.validation.player.in.guild"))
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
