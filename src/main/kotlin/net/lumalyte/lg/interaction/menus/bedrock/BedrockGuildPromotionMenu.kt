package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild promotion menu using Cumulus CustomForm
 * Allows changing member ranks with dropdown selection
 */
class BedrockGuildPromotionMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val members = getPromotableMembers()
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }

        return CustomForm.builder()
            .title(lang.legacy("bedrock.promotion.title", "guild" to guild.name))
            .label(lang.raw("bedrock.promotion.description"))
            .dropdown(
                lang.raw("bedrock.promotion.select_member"),
                createMemberOptions(members),
                0
            )
            .dropdown(
                lang.raw("bedrock.promotion.select_rank"),
                createRankOptions(ranks),
                0
            )
            .validResultHandler { response ->
                handleFormResponse(response, members, ranks)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun getPromotableMembers(): List<Member> {
        return memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId } // Can't promote yourself
            .sortedBy { it.playerId }
    }

    private fun createMemberOptions(members: List<Member>): List<String> {
        if (members.isEmpty()) {
            return listOf(lang.raw("bedrock.promotion.no_members"))
        }

        return members.map { member ->
            val playerName = getPlayerName(member)
            val currentRank = rankService.getRank(member.rankId)?.name ?: lang.raw("bedrock.promotion.unknown_rank")
            lang.legacy("bedrock.promotion.member_option", "player" to playerName, "rank" to currentRank)
        }
    }

    private fun createRankOptions(ranks: List<Rank>): List<String> {
        if (ranks.isEmpty()) {
            return listOf(lang.raw("bedrock.promotion.no_ranks"))
        }

        return ranks.map { rank ->
            lang.legacy("bedrock.promotion.rank_option", "rank" to rank.name, "priority" to rank.priority)
        }
    }

    private fun getPlayerName(member: Member): String {
        return try {
            player.server.getOfflinePlayer(member.playerId).name ?: lang.raw("menu.common.unknown_player")
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            lang.raw("menu.common.unknown_player")
        }
    }

    private fun handleFormResponse(
        response: org.geysermc.cumulus.response.CustomFormResponse,
        members: List<Member>,
        ranks: List<Rank>
    ) {
        try {
            onFormResponseReceived()

            val memberIndex = response.next() as? Int ?: 0
            val rankIndex = response.next() as? Int ?: 0

            if (members.isEmpty() || ranks.isEmpty() ||
                memberIndex >= members.size || rankIndex >= ranks.size) {
                bedrockNavigator.goBack()
                return
            }

            val selectedMember = members[memberIndex]
            val selectedRank = ranks[rankIndex]

            // Check if member is already at this rank
            if (selectedMember.rankId == selectedRank.id) {
                player.sendMessage(lang.msg("bedrock.promotion.feedback.same_rank"))
                bedrockNavigator.goBack()
                return
            }

            // Check permissions
            val hasPermission = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)
            if (!hasPermission) {
                player.sendMessage(lang.msg("bedrock.promotion.feedback.no_permission"))
                bedrockNavigator.goBack()
                return
            }

            // Change member rank
            val success = memberService.changeMemberRank(
                selectedMember.playerId,
                guild.id,
                selectedRank.id,
                player.uniqueId
            )

            if (success) {
                player.sendMessage(lang.msg("bedrock.promotion.feedback.success"))
            } else {
                player.sendMessage(lang.msg("bedrock.promotion.feedback.error"))
            }

            bedrockNavigator.goBack()

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error handling promotion form response: ${e.message}")
            player.sendMessage(lang.msg("bedrock.promotion.feedback.error"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
