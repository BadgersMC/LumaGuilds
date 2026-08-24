package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild rank list menu using Cumulus SimpleForm
 * Displays all ranks in the guild with their permissions and allows viewing details
 */
class BedrockGuildRankListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val rankService: RankService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()

    /** Permissions that are irrelevant when the claims system is disabled. */
    private val claimsPermissions = setOf(
        RankPermission.MANAGE_CLAIMS,
        RankPermission.MANAGE_FLAGS,
        RankPermission.MANAGE_PERMISSIONS,
        RankPermission.CREATE_CLAIMS,
        RankPermission.DELETE_CLAIMS
    )

    /** Returns only the permissions that are meaningful given the current config. */
    private fun visiblePermissions(rank: Rank): Set<RankPermission> {
        val claimsEnabled = configService.loadConfig().claimsEnabled
        return if (claimsEnabled) rank.permissions
        else rank.permissions.filterNotTo(mutableSetOf()) { it in claimsPermissions }
    }

    override fun getForm(): Form {
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankCount = ranks.size

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.rank_list.title", "guild" to guild.name))
            .content(buildRankListContent(rankCount))
            .apply {
                if (ranks.isEmpty()) {
                    button(lang.raw("bedrock.rank_list.no_ranks"))
                } else {
                    ranks.forEach { rank ->
                        val permissionCount = visiblePermissions(rank).size
                        val buttonText = buildRankButtonText(rank, permissionCount)
                        button(buttonText)
                    }
                }
            }
            .validResultHandler { response ->
                val clickedButton = response.clickedButtonId()
                handleRankSelection(clickedButton, ranks)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildRankListContent(rankCount: Int): String {
        return lang.legacy("bedrock.rank_list.content", "count" to rankCount)
    }

    private fun buildRankButtonText(rank: Rank, permissionCount: Int): String {
        return lang.legacy(
            "bedrock.rank_list.rank_button",
            "rank" to rank.name,
            "priority" to rank.priority,
            "permission_count" to permissionCount
        )
    }

    private fun handleRankSelection(buttonIndex: Int, ranks: List<Rank>) {
        if (ranks.isEmpty() || buttonIndex >= ranks.size) {
            bedrockNavigator.goBack()
            return
        }

        val selectedRank = ranks[buttonIndex]
        showRankDetails(selectedRank)
    }

    private fun showRankDetails(rank: Rank) {
        val detailForm = SimpleForm.builder()
            .title(lang.legacy("bedrock.rank_list.details.title", "rank" to rank.name))
            .content(buildRankDetailsContent(rank))
            .button(lang.raw("bedrock.rank_list.details.back"))
            .validResultHandler { _ ->
                // Re-show the rank list
                bedrockNavigator.openMenu(BedrockGuildRankListMenu(menuNavigator, player, guild, logger))
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()

        // Since we can't directly show a new form from here, we'll need to use a different approach
        // For now, let's just show the details in chat and go back
        showRankDetailsInChat(rank)
        bedrockNavigator.goBack()
    }

    private fun buildRankDetailsContent(rank: Rank): String {
        val visible = visiblePermissions(rank)
        val permissions = if (visible.isEmpty()) {
            lang.legacy("bedrock.rank_list.details.no_permissions")
        } else {
            visible.joinToString("\n") { permission ->
                lang.legacy("bedrock.rank_list.details.permission_row", "permission" to getLocalizedPermissionName(permission))
            }
        }

        return lang.legacy(
            "bedrock.rank_list.details.content",
            "rank" to rank.name,
            "priority" to rank.priority,
            "permissions" to permissions
        )
    }

    private fun getLocalizedPermissionName(permission: RankPermission): String {
        val key = "permission.${permission.name.lowercase().replace("_", ".")}"
        return lang.raw(key)
    }

    private fun showRankDetailsInChat(rank: Rank) {
        player.sendMessage(lang.msg("bedrock.rank_list.chat.title", "rank" to rank.name))
        player.sendMessage(lang.msg("bedrock.rank_list.chat.name", "rank" to rank.name))
        player.sendMessage(lang.msg("bedrock.rank_list.chat.priority", "priority" to rank.priority))
        player.sendMessage(lang.msg("bedrock.rank_list.chat.permissions"))

        val visible = visiblePermissions(rank)
        if (visible.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.rank_list.chat.no_permissions"))
        } else {
            visible.forEach { permission ->
                player.sendMessage(lang.msg("bedrock.rank_list.chat.permission_row", "permission" to getLocalizedPermissionName(permission)))
            }
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
