package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.GuildInvitationManager
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildInviteMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild invite confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildInviteConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val targetPlayer: Player,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

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

        GuildInvitationManager.addInvite(
            guildId = guild.id,
            guildName = guild.name,
            invitedPlayerId = targetPlayer.uniqueId,
            inviterPlayerId = player.uniqueId,
            inviterName = player.name
        )

        // Send invitation message
        player.sendMessage("§a✅ Invitation sent to ${targetPlayer.name}!")
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)

        targetPlayer.sendMessage("")
        targetPlayer.sendMessage("§6§l📨 GUILD INVITATION")
        targetPlayer.sendMessage("§7${player.name} invited you to join §6${guild.name}§7!")
        targetPlayer.sendMessage("")
        targetPlayer.sendMessage("§7To accept: §a/guild join ${guild.name}")
        targetPlayer.sendMessage("§7To decline: §c/guild decline ${guild.name}")
        targetPlayer.sendMessage("")
        targetPlayer.playSound(targetPlayer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

        // Return to member management menu
        bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}

