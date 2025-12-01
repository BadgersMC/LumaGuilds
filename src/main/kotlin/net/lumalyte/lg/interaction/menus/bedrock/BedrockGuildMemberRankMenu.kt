package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild member rank assignment menu using Cumulus CustomForm
 * Allows changing a member's rank
 */
class BedrockGuildMemberRankMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val member: Member,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        val memberName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown"
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankNames = ranks.map { it.name }
        val currentRank = rankService.getRank(member.rankId)
        val currentRankIndex = ranks.indexOfFirst { it.id == member.rankId }.coerceAtLeast(0)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.member.rank.title")} - $memberName")
            .apply { rankIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.member.rank.description", memberName))
            .label("${bedrockLocalization.getBedrockString(player, "guild.member.rank.current")}: ${currentRank?.name ?: "Unknown"}")
            .dropdown(
                bedrockLocalization.getBedrockString(player, "guild.member.rank.new"),
                rankNames,
                currentRankIndex
            )
            .validResultHandler { response ->
                val newRankIndex = response.asDropdown(2)
                val newRank = ranks.getOrNull(newRankIndex)

                if (newRank != null && newRank.id != member.rankId) {
                    handleRankChange(newRank.id, newRank.name)
                } else {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.member.rank.no.change"))
                    bedrockNavigator.goBack()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleRankChange(newRankId: java.util.UUID, newRankName: String) {
        val success = memberService.changeMemberRank(
            member.playerId,
            guild.id,
            newRankId,
            player.uniqueId
        )

        if (success) {
            player.sendMessage(
                bedrockLocalization.getBedrockString(
                    player,
                    "guild.member.rank.changed.success",
                    Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown",
                    newRankName
                )
            )
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.member.rank.changed.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
