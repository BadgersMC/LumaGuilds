package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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

/**
 * Bedrock Edition guild kick confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildKickConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val memberToKick: Member,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val targetName = Bukkit.getOfflinePlayer(memberToKick.playerId).name ?: lang.raw("menu.common.unknown_player")

        return SimpleForm.builder()
            .title(lang.raw("bedrock.kick_confirmation.title"))
            .content(lang.legacy("bedrock.kick_confirmation.content", "player" to targetName, "guild" to guild.name, "joined" to memberToKick.joinedAt.toString()))
            .button(lang.raw("bedrock.kick_confirmation.button.kick"))
            .button(lang.raw("bedrock.kick_confirmation.button.cancel"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> performKick()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(lang.msg("bedrock.kick_confirmation.feedback.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.kick_confirmation.feedback.cancelled"))
            })
            .build()
    }

    private fun performKick() {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        val targetName = Bukkit.getOfflinePlayer(memberToKick.playerId).name ?: lang.raw("menu.common.unknown_player")

        // Perform the kick
        val success = memberService.removeMember(memberToKick.playerId, guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(lang.msg("bedrock.kick_confirmation.feedback.kicked", "player" to targetName))

            // Notify the kicked player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage(lang.msg("bedrock.kick_confirmation.feedback.target_kicked", "guild" to guild.name, "player" to player.name))
            }

            // Return to member management menu
            bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage(lang.msg("bedrock.kick_confirmation.feedback.failed"))
            bedrockNavigator.openMenu(GuildKickMenu(menuNavigator, player, guild))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}
