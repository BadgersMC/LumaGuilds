package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        val memberName = Bukkit.getOfflinePlayer(member.playerId).name ?: lang.bedrock("menu.common.unknown_player")
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankNames = ranks.map { it.name }
        val currentRank = rankService.getRank(member.rankId)
        val currentRankIndex = ranks.indexOfFirst { it.id == member.rankId }.coerceAtLeast(0)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.member_rank.title", "player" to memberName))
            .apply { rankIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.member_rank.description", "player" to memberName))
            .label(lang.bedrock("bedrock.member_rank.current", "rank" to (currentRank?.name ?: lang.bedrock("bedrock.member_rank.unknown_rank"))))
            .dropdown(
                lang.bedrock("bedrock.member_rank.new_rank"),
                rankNames,
                currentRankIndex
            )
            .validResultHandler { response ->
                val newRankIndex = response.asDropdown(2)
                val newRank = ranks.getOrNull(newRankIndex)

                if (newRank != null && newRank.id != member.rankId) {
                    handleRankChange(newRank.id, newRank.name)
                } else {
                    player.sendMessage(lang.msg("bedrock.member_rank.feedback.no_change"))
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
            val memberName = Bukkit.getOfflinePlayer(member.playerId).name ?: lang.bedrock("menu.common.unknown_player")
            player.sendMessage(lang.msg("bedrock.member_rank.feedback.changed", "player" to memberName, "rank" to newRankName))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.member_rank.feedback.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
