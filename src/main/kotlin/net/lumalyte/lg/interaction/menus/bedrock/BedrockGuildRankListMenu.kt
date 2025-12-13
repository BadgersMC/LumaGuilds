package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankCount = ranks.size

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.rank.list.title")} - ${guild.name}")
            .content(buildRankListContent(rankCount))
            .apply {
                if (ranks.isEmpty()) {
                    button(bedrockLocalization.getBedrockString(player, "guild.rank.list.no.ranks"))
                } else {
                    ranks.forEach { rank ->
                        val permissionCount = rank.permissions.size
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
        return """
            |§7${bedrockLocalization.getBedrockString(player, "guild.rank.list.description")}
            |
            |§6§l━━━ RANKS ━━━
            |§bTotal Ranks§7: §f$rankCount
            |
            |§7${bedrockLocalization.getBedrockString(player, "guild.rank.list.priority")}: ${bedrockLocalization.getBedrockString(player, "guild.rank.list.permissions")}
        """.trimMargin()
    }

    private fun buildRankButtonText(rank: Rank, permissionCount: Int): String {
        val permissionText = bedrockLocalization.getBedrockString(player, "guild.rank.list.permission.count", permissionCount)
        return "§6${rank.name} §7(${rank.priority}) §f- §b$permissionText"
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
            .title("${bedrockLocalization.getBedrockString(player, "guild.rank.details.title")} - ${rank.name}")
            .content(buildRankDetailsContent(rank))
            .button(bedrockLocalization.getBedrockString(player, "guild.rank.details.back"))
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
        val permissions = if (rank.permissions.isEmpty()) {
            "§7${bedrockLocalization.getBedrockString(player, "guild.rank.details.no.permissions")}"
        } else {
            rank.permissions.joinToString("\n") { permission ->
                "§b• §f${getLocalizedPermissionName(permission)}"
            }
        }

        return """
            |§6§l━━━ ${rank.name} §r§6━━━
            |§e${bedrockLocalization.getBedrockString(player, "guild.rank.details.priority")}§7: §f${rank.priority}
            |
            |§6${bedrockLocalization.getBedrockString(player, "guild.rank.details.permissions")}§7:
            |$permissions
        """.trimMargin()
    }

    private fun getLocalizedPermissionName(permission: RankPermission): String {
        return when (permission) {
            // Guild management
            RankPermission.MANAGE_RANKS -> bedrockLocalization.getBedrockString(player, "permission.manage.ranks")
            RankPermission.MANAGE_MEMBERS -> bedrockLocalization.getBedrockString(player, "permission.manage.members")
            RankPermission.MANAGE_BANNER -> bedrockLocalization.getBedrockString(player, "permission.manage.banner")
            RankPermission.MANAGE_EMOJI -> bedrockLocalization.getBedrockString(player, "permission.manage.emoji")
            RankPermission.MANAGE_DESCRIPTION -> bedrockLocalization.getBedrockString(player, "permission.manage.description")
            RankPermission.MANAGE_HOME -> bedrockLocalization.getBedrockString(player, "permission.manage.home")
            RankPermission.MANAGE_MODE -> bedrockLocalization.getBedrockString(player, "permission.manage.mode")
            RankPermission.MANAGE_GUILD_SETTINGS -> bedrockLocalization.getBedrockString(player, "permission.manage.guild.settings")

            // Relations & Diplomacy
            RankPermission.MANAGE_RELATIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.relations")
            RankPermission.DECLARE_WAR -> bedrockLocalization.getBedrockString(player, "permission.declare.war")
            RankPermission.ACCEPT_ALLIANCES -> bedrockLocalization.getBedrockString(player, "permission.accept.alliances")
            RankPermission.MANAGE_PARTIES -> bedrockLocalization.getBedrockString(player, "permission.manage.parties")
            RankPermission.SEND_PARTY_REQUESTS -> bedrockLocalization.getBedrockString(player, "permission.send.party.requests")
            RankPermission.ACCEPT_PARTY_INVITES -> bedrockLocalization.getBedrockString(player, "permission.accept.party.invites")

            // Banking & Economy
            RankPermission.DEPOSIT_TO_BANK -> bedrockLocalization.getBedrockString(player, "permission.deposit.bank")
            RankPermission.WITHDRAW_FROM_BANK -> bedrockLocalization.getBedrockString(player, "permission.withdraw.bank")
            RankPermission.VIEW_BANK_TRANSACTIONS -> bedrockLocalization.getBedrockString(player, "permission.view.bank.transactions")
            RankPermission.EXPORT_BANK_DATA -> bedrockLocalization.getBedrockString(player, "permission.export.bank.data")
            RankPermission.MANAGE_BANK_SETTINGS -> bedrockLocalization.getBedrockString(player, "permission.manage.bank.settings")
            RankPermission.PLACE_VAULT -> bedrockLocalization.getBedrockString(player, "permission.place.vault")
            RankPermission.ACCESS_VAULT -> bedrockLocalization.getBedrockString(player, "permission.access.vault")
            RankPermission.DEPOSIT_TO_VAULT -> bedrockLocalization.getBedrockString(player, "permission.deposit.vault")
            RankPermission.WITHDRAW_FROM_VAULT -> bedrockLocalization.getBedrockString(player, "permission.withdraw.vault")
            RankPermission.MANAGE_VAULT -> bedrockLocalization.getBedrockString(player, "permission.manage.vault")
            RankPermission.BREAK_VAULT -> bedrockLocalization.getBedrockString(player, "permission.break.vault")

            // Communication
            RankPermission.SEND_ANNOUNCEMENTS -> bedrockLocalization.getBedrockString(player, "permission.send.announcements")
            RankPermission.SEND_PINGS -> bedrockLocalization.getBedrockString(player, "permission.send.pings")
            RankPermission.MODERATE_CHAT -> bedrockLocalization.getBedrockString(player, "permission.moderate.chat")

            // Claims & Territory
            RankPermission.MANAGE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.manage.claims")
            RankPermission.MANAGE_FLAGS -> bedrockLocalization.getBedrockString(player, "permission.manage.flags")
            RankPermission.MANAGE_PERMISSIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.permissions")
            RankPermission.CREATE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.create.claims")
            RankPermission.DELETE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.delete.claims")

            // Special Roles
            RankPermission.ACCESS_ADMIN_COMMANDS -> bedrockLocalization.getBedrockString(player, "permission.access.admin.commands")
            RankPermission.BYPASS_RESTRICTIONS -> bedrockLocalization.getBedrockString(player, "permission.bypass.restrictions")
            RankPermission.VIEW_AUDIT_LOGS -> bedrockLocalization.getBedrockString(player, "permission.view.audit.logs")
            RankPermission.MANAGE_INTEGRATIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.integrations")
        }
    }

    private fun showRankDetailsInChat(rank: Rank) {
        val title = bedrockLocalization.getBedrockString(player, "guild.rank.details.title")
        player.sendMessage("§6$title: §f${rank.name}")

        val name = bedrockLocalization.getBedrockString(player, "guild.rank.details.name")
        player.sendMessage("§7$name: §f${rank.name}")

        val priority = bedrockLocalization.getBedrockString(player, "guild.rank.details.priority")
        player.sendMessage("§7$priority: §f${rank.priority}")

        val permissions = bedrockLocalization.getBedrockString(player, "guild.rank.details.permissions")
        player.sendMessage("§7$permissions:")

        if (rank.permissions.isEmpty()) {
            val noPermissions = bedrockLocalization.getBedrockString(player, "guild.rank.details.no.permissions")
            player.sendMessage("§7• §f$noPermissions")
        } else {
            rank.permissions.forEach { permission ->
                player.sendMessage("§7• §f${getLocalizedPermissionName(permission)}")
            }
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
