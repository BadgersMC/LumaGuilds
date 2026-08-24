package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        return SimpleForm.builder()
            .title(lang.raw("bedrock.invite_confirmation.title"))
            .content(lang.legacy("bedrock.invite_confirmation.content", "guild" to guild.name, "player" to targetPlayer.name))
            .button(lang.raw("bedrock.invite_confirmation.button.send"))
            .button(lang.raw("bedrock.invite_confirmation.button.cancel"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> sendInvite()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(lang.msg("bedrock.invite_confirmation.feedback.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.invite_confirmation.feedback.cancelled"))
            })
            .build()
    }

    private fun sendInvite() {
        // Check if player is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("bedrock.invite_confirmation.feedback.already_member", "player" to targetPlayer.name))
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
        player.sendMessage(lang.msg("bedrock.invite_confirmation.feedback.sent", "player" to targetPlayer.name))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)

        targetPlayer.sendMessage(lang.msg("menu.common.blank"))
        targetPlayer.sendMessage(lang.msg("bedrock.invite_confirmation.notification.title"))
        targetPlayer.sendMessage(lang.msg("bedrock.invite_confirmation.notification.invited", "player" to player.name, "guild" to guild.name))
        targetPlayer.sendMessage(lang.msg("menu.common.blank"))
        targetPlayer.sendMessage(lang.msg("bedrock.invite_confirmation.notification.accept", "guild" to guild.name))
        targetPlayer.sendMessage(lang.msg("bedrock.invite_confirmation.notification.decline", "guild" to guild.name))
        targetPlayer.sendMessage(lang.msg("menu.common.blank"))
        targetPlayer.playSound(targetPlayer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

        // Return to member management menu
        bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}

