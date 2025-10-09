package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildInviteMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild invite confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildInviteConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val targetPlayer: Player,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    override fun getForm(): Form {
        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.title"))
            .content(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.message", guild.name, targetPlayer.name))
            .button(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.button.send"))
            .button(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.button.cancel"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> sendInvite()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.confirm.cancelled"))
            })
            .build()
    }

    private fun sendInvite() {
        // Check if player is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.already.member", targetPlayer.name))
            menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            return
        }

        // Send invitation message
        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.invite.sent.success", targetPlayer.name))
        targetPlayer.sendMessage(bedrockLocalization.getBedrockString(targetPlayer, "guild.invite.message.title"))
        targetPlayer.sendMessage(bedrockLocalization.getBedrockString(targetPlayer, "guild.invite.message.from", player.name, guild.name))
        targetPlayer.sendMessage(bedrockLocalization.getBedrockString(targetPlayer, "guild.invite.message.join", guild.name))
        targetPlayer.sendMessage(bedrockLocalization.getBedrockString(targetPlayer, "guild.invite.message.decline", guild.name))

        // TODO: Store invitation in database for later acceptance
        // For now, just show the message

        // Return to member management menu
        bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild, messageService))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}


