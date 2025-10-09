package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildKickMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu
import org.bukkit.Bukkit
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
 * Bedrock Edition guild kick confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildKickConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val memberToKick: Member,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    override fun getForm(): Form {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        val targetName = targetPlayer?.name ?: "Unknown Player"

        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.title"))
            .content(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.message", targetName, guild.name, memberToKick.joinedAt.toString()))
            .button(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.button.kick"))
            .button(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.button.cancel"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> performKick()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.kick.confirm.cancelled"))
            })
            .build()
    }

    private fun performKick() {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        val targetName = targetPlayer?.name ?: "Unknown Player"

        // Perform the kick
        val success = memberService.removeMember(memberToKick.playerId, guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.kick.success", targetName))

            // Notify the kicked player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage(bedrockLocalization.getBedrockString(targetPlayer, "guild.kick.notify.player", guild.name, player.name))
            }

            // Return to member management menu
            bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild, messageService))
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.kick.failed"))
            bedrockNavigator.openMenu(GuildKickMenu(menuNavigator, player, guild, messageService))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}

