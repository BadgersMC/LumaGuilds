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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild rank list menu using Cumulus SimpleForm
 * Displays all ranks in the guild with their permissions and allows viewing details
 */
class BedrockGuildRankListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

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
            |${bedrockLocalization.getBedrockString(player, "guild.rank.list.description")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.rank.list.total", rankCount)}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.rank.list.priority")}: ${bedrockLocalization.getBedrockString(player, "guild.rank.list.permissions")}
        """.trimMargin()
    }

    private fun buildRankButtonText(rank: Rank, permissionCount: Int): String {
        val permissionText = bedrockLocalization.getBedrockString(player, "guild.rank.list.permission.count", permissionCount)
        return "${rank.name} (${rank.priority}) - $permissionText"
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
                bedrockNavigator.openMenu(BedrockGuildRankListMenu(menuNavigator, player, guild, logger, messageService))
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
            bedrockLocalization.getBedrockString(player, "guild.rank.details.no.permissions")
        } else {
            rank.permissions.joinToString("\n• ") { permission ->
                "• ${getLocalizedPermissionName(permission)}"
            }
        }

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.rank.details.name")}: ${rank.name}
            |${bedrockLocalization.getBedrockString(player, "guild.rank.details.priority")}: ${rank.priority}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.rank.details.permissions")}:
            |$permissions
        """.trimMargin()
    }

    private fun getLocalizedPermissionName(permission: RankPermission): String {
        return when (permission) {
            // Guild management
            RankPermission.MANAGE_RANKS -> bedrockLocalization.getBedrockString(player, "permission.manage.ranks")
            RankPermission.MANAGE_MEMBERS -> "Manage Members"
            RankPermission.MANAGE_BANNER -> "Manage Banner"
            RankPermission.MANAGE_EMOJI -> "Manage Emoji"
            RankPermission.MANAGE_GUILD_NAME -> "Manage Guild Name"
            RankPermission.MANAGE_BANK_SECURITY -> "Manage Bank Security"
            RankPermission.ACTIVATE_EMERGENCY_FREEZE -> "Activate Emergency Freeze"
            RankPermission.DEACTIVATE_EMERGENCY_FREEZE -> "Deactivate Emergency Freeze"
            RankPermission.VIEW_SECURITY_AUDITS -> "View Security Audits"
            RankPermission.MANAGE_BUDGETS -> "Manage Budgets"
            RankPermission.MANAGE_DESCRIPTION -> "Manage Description"
            RankPermission.MANAGE_HOME -> "Manage Home"
            RankPermission.MANAGE_MODE -> "Manage Mode"
            RankPermission.MANAGE_GUILD_SETTINGS -> "Manage Guild Settings"

            // Relations & Diplomacy
            RankPermission.MANAGE_RELATIONS -> bedrockLocalization.getBedrockString(player, "permission.manage.relations")
            RankPermission.DECLARE_WAR -> bedrockLocalization.getBedrockString(player, "permission.declare.war")
            RankPermission.ACCEPT_ALLIANCES -> "Accept Alliances"
            RankPermission.MANAGE_PARTIES -> "Manage Parties"
            RankPermission.SEND_PARTY_REQUESTS -> "Send Party Requests"
            RankPermission.ACCEPT_PARTY_INVITES -> "Accept Party Invites"

            // Banking & Economy
            RankPermission.DEPOSIT_TO_BANK -> "Deposit to Bank"
            RankPermission.WITHDRAW_FROM_BANK -> "Withdraw from Bank"
            RankPermission.VIEW_BANK_TRANSACTIONS -> "View Bank Transactions"
            RankPermission.EXPORT_BANK_DATA -> "Export Bank Data"
            RankPermission.MANAGE_BANK_SETTINGS -> "Manage Bank Settings"

            // Communication
            RankPermission.SEND_ANNOUNCEMENTS -> "Send Announcements"
            RankPermission.SEND_PINGS -> "Send Pings"
            RankPermission.MODERATE_CHAT -> "Moderate Chat"

            // Claims & Territory
            RankPermission.MANAGE_CLAIMS -> bedrockLocalization.getBedrockString(player, "permission.manage.claims")
            RankPermission.MANAGE_FLAGS -> "Manage Flags"
            RankPermission.MANAGE_PERMISSIONS -> "Manage Permissions"
            RankPermission.CREATE_CLAIMS -> "Create Claims"
            RankPermission.DELETE_CLAIMS -> "Delete Claims"

            // Special Roles
            RankPermission.ACCESS_ADMIN_COMMANDS -> "Access Admin Commands"
            RankPermission.BYPASS_RESTRICTIONS -> "Bypass Restrictions"
            RankPermission.VIEW_AUDIT_LOGS -> "View Audit Logs"
            RankPermission.MANAGE_INTEGRATIONS -> "Manage Integrations"
            
            // Additional permissions
            RankPermission.DEPOSIT_MONEY -> "Deposit Money"
            RankPermission.WITHDRAW_MONEY -> "Withdraw Money" 
            RankPermission.VIEW_BANK_HISTORY -> "View Bank History"
            RankPermission.USE_CHAT -> "Use Chat"
            RankPermission.MANAGE_CHAT_SETTINGS -> "Manage Chat Settings"
            RankPermission.CLAIM_LAND -> "Claim Land"
            RankPermission.UNCLAIM_LAND -> "Unclaim Land"
            
            // Security
            RankPermission.OVERRIDE_PROTECTION -> "Override Protection"
            RankPermission.BYPASS_COOLDOWNS -> "Bypass Cooldowns"
            RankPermission.MANAGE_SECURITY -> "Manage Security"

            // Fallback for any unhandled permissions
            else -> permission.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun showRankDetailsInChat(rank: Rank) {
        val title = bedrockLocalization.getBedrockString(player, "guild.rank.details.title")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>$title: <white>${rank.name}")

        val name = bedrockLocalization.getBedrockString(player, "guild.rank.details.name")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>$name: <white>${rank.name}")

        val priority = bedrockLocalization.getBedrockString(player, "guild.rank.details.priority")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>$priority: <white>${rank.priority}")

        val permissions = bedrockLocalization.getBedrockString(player, "guild.rank.details.permissions")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>$permissions:")

        if (rank.permissions.isEmpty()) {
            val noPermissions = bedrockLocalization.getBedrockString(player, "guild.rank.details.no.permissions")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>• <white>$noPermissions")
        } else {
            rank.permissions.forEach { permission ->
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>• <white>${getLocalizedPermissionName(permission)}")
            }
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}

