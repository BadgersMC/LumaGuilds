package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
        val targetName = targetPlayer?.name ?: "Unknown Player"
        val currentRank = rankService.getRank(targetMember.rankId)

        return SimpleForm.builder()
            .title("⚡ Confirm Rank Change")
            .content("""
                |Change member rank for ${guild.name}?
                |
                |👤 Player: $targetName
                |📊 Current Rank: ${currentRank?.name ?: "Unknown"}
                |⬆ New Rank: ${newRank.name}
                |
                |${getRankChangeDescription(currentRank, newRank)}
                |
                |This will change their permissions and access level.
            """.trimMargin())
            .button("⚡ Change Rank")
            .button("❌ Cancel")
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> changeRank()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage("§c❌ Rank change cancelled.")
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage("§c❌ Rank change cancelled.")
            })
            .build()
    }

    private fun getRankChangeDescription(currentRank: Rank?, newRank: Rank): String {
        return when {
            currentRank == null -> "⚠ Setting initial rank"
            newRank.priority < (currentRank.priority) -> "⬆ Promoting member"
            newRank.priority > (currentRank.priority) -> "⬇ Demoting member"
            else -> "⚖ Adjusting rank priority"
        }
    }

    private fun changeRank() {
        val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
        val targetName = targetPlayer?.name ?: "Unknown Player"

        // Update the member's rank
        val success = memberService.changeMemberRank(targetMember.playerId, guild.id, newRank.id, player.uniqueId)

        if (success) {
            player.sendMessage("§a✅ Successfully changed $targetName's rank to ${newRank.name}!")

            // Notify the target player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage("§6⚡ Your rank in ${guild.name} has been changed to ${newRank.name}")
            }

            // Return to member management menu
            bedrockNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage("§c❌ Failed to change rank. Check permissions.")
            bedrockNavigator.openMenu(GuildMemberRankMenu(menuNavigator, player, guild, targetMember))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}


