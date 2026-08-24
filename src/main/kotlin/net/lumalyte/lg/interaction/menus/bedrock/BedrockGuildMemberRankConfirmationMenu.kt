package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildMemberManagementMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberRankMenu
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild member rank change confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildMemberRankConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val targetMember: Member,
    private val newRank: Rank,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val targetName = Bukkit.getOfflinePlayer(targetMember.playerId).name ?: lang.raw("menu.common.unknown_player")
        val currentRank = rankService.getRank(targetMember.rankId)

        return SimpleForm.builder()
            .title(lang.raw("bedrock.member_rank_confirmation.title"))
            .content(lang.legacy(
                "bedrock.member_rank_confirmation.content",
                "guild" to guild.name,
                "player" to targetName,
                "current_rank" to (currentRank?.name ?: lang.raw("bedrock.member_rank_confirmation.unknown_rank")),
                "new_rank" to newRank.name,
                "change" to getRankChangeDescription(currentRank, newRank)
            ))
            .button(lang.raw("bedrock.member_rank_confirmation.button.confirm"))
            .button(lang.raw("bedrock.member_rank_confirmation.button.cancel"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> changeRank()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(lang.msg("bedrock.member_rank_confirmation.feedback.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.member_rank_confirmation.feedback.cancelled"))
            })
            .build()
    }

    private fun getRankChangeDescription(currentRank: Rank?, newRank: Rank): String {
        return when {
            currentRank == null -> lang.raw("bedrock.member_rank_confirmation.change.initial")
            newRank.priority < (currentRank.priority) -> lang.raw("bedrock.member_rank_confirmation.change.promotion")
            newRank.priority > (currentRank.priority) -> lang.raw("bedrock.member_rank_confirmation.change.demotion")
            else -> lang.raw("bedrock.member_rank_confirmation.change.adjustment")
        }
    }

    private fun changeRank() {
        val targetName = Bukkit.getOfflinePlayer(targetMember.playerId).name ?: lang.raw("menu.common.unknown_player")

        // Update the member's rank
        val success = memberService.changeMemberRank(targetMember.playerId, guild.id, newRank.id, player.uniqueId)

        if (success) {
            player.sendMessage(lang.msg("bedrock.member_rank_confirmation.feedback.changed", "player" to targetName, "rank" to newRank.name))

            // Notify the target player if they're online
            val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
            if (targetPlayer != null) {
                targetPlayer.sendMessage(lang.msg("bedrock.member_rank_confirmation.feedback.target_changed", "guild" to guild.name, "rank" to newRank.name))
            }

            // Return to member management menu
            bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage(lang.msg("bedrock.member_rank_confirmation.feedback.failed"))
            bedrockNavigator.openMenu(GuildMemberRankMenu(menuNavigator, player, guild, targetMember))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}


