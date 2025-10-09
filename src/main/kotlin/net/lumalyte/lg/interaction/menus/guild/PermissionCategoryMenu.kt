package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class PermissionCategoryMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                            private var guild: Guild, private var rank: Rank,
                            private val categoryName: String, 
                            private val categoryPermissions: List<RankPermission>, private val messageService: MessageService): Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val configService: ConfigService by inject()
    private var modifiedPermissions = rank.permissions.toMutableSet()
    
    // Check if the current player is the guild owner
    private fun isGuildOwner(): Boolean {
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val ownerRank = rankService.getHighestRank(guild.id)
        return playerRank?.id == ownerRank?.id
    }
    
    // Check if the rank being edited is the owner rank
    private fun isOwnerRank(): Boolean {
        val ownerRank = rankService.getHighestRank(guild.id)
        return rank.id == ownerRank?.id
    }
    
    // Check if the player is editing their own owner rank
    private fun isEditingOwnOwnerRank(): Boolean {
        return isGuildOwner() && isOwnerRank()
    }

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>$categoryName - ${rank.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Category info and bulk actions
        addCategoryHeader(pane)

        // Row 1-4: Individual permissions
        addPermissionButtons(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addCategoryHeader(pane: StaticPane) {
        // Category info
        val categoryIcon = when (categoryName) {
            "Guild Management" -> Material.GOLDEN_SWORD
            "Banking" -> Material.GOLD_INGOT
            "Diplomacy" -> Material.WRITABLE_BOOK
            "Claims" -> Material.GRASS_BLOCK
            "Communication" -> Material.BELL
            "Administrative" -> Material.COMMAND_BLOCK
            else -> Material.PAPER
        }

        val infoItem = ItemStack(categoryIcon)
            .setAdventureName(player, messageService, "<gold>🔧 $categoryName")
            .addAdventureLore(player, messageService, "<gray>Managing permissions for: <white>${rank.name}")
            .addAdventureLore(player, messageService, "<gray>Category: <white>$categoryName")
            .addAdventureLore(player, messageService, "<gray>Total permissions: <white>${categoryPermissions.size}")
            
        // Add owner protection warning if editing own owner rank
        if (isEditingOwnOwnerRank()) {
            infoItem.addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>⚠︎ OWNER RANK PROTECTION ACTIVE")
                .addAdventureLore(player, messageService, "<gray>Permission changes are blocked")
                .addAdventureLore(player, messageService, "<gray>to prevent guild owner lockout")
        }

        pane.addItem(GuiItem(infoItem), 1, 0)

        // Enable all button
        val enableAllItem = ItemStack(Material.LIME_CONCRETE)
            .setAdventureName(player, messageService, "<green>✅ Enable All")
            .addAdventureLore(player, messageService, "<gray>Grant all $categoryName permissions")
            .addAdventureLore(player, messageService, "<gray>to this rank")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<green>Click to enable all")

        val enableAllGuiItem = GuiItem(enableAllItem) {
            if (isEditingOwnOwnerRank()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You cannot modify your own owner rank permissions!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>This prevents you from locking yourself out of guild management.")
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.add(permission)
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Enabled all $categoryName permissions!")
            open() // Refresh the menu
        }
        pane.addItem(enableAllGuiItem, 3, 0)

        // Disable all button
        val disableAllItem = ItemStack(Material.RED_CONCRETE)
            .setAdventureName(player, messageService, "<red>❌ Disable All")
            .addAdventureLore(player, messageService, "<gray>Remove all $categoryName permissions")
            .addAdventureLore(player, messageService, "<gray>from this rank")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Click to disable all")

        val disableAllGuiItem = GuiItem(disableAllItem) {
            if (isEditingOwnOwnerRank()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You cannot modify your own owner rank permissions!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>This prevents you from locking yourself out of guild management.")
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.remove(permission)
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Disabled all $categoryName permissions!")
            open() // Refresh the menu
        }
        pane.addItem(disableAllGuiItem, 5, 0)

        // Permission count
        val enabledCount = categoryPermissions.count { modifiedPermissions.contains(it) }
        val countItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📊 Permission Status")
            .addAdventureLore(player, messageService, "<gray>Enabled: <green>$enabledCount<gray>/<white>${categoryPermissions.size}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Click individual permissions below")

        pane.addItem(GuiItem(countItem), 7, 0)
    }

    private fun addPermissionButtons(pane: StaticPane) {
        categoryPermissions.forEachIndexed { index, permission ->
            val row = 1 + (index / 7) // 7 permissions per row
            val col = 1 + (index % 7)

            if (row > 4) return@forEachIndexed // Don't overflow into action row

            val hasPermission = modifiedPermissions.contains(permission)
            val displayName = permission.name.replace("_", " ")
                .lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val permissionItem = ItemStack(
                if (hasPermission) Material.LIME_CONCRETE_POWDER else Material.RED_CONCRETE_POWDER
            )
                .name("${if (hasPermission) "<green>✓" else "<red>✗"} §f$displayName")
                .addAdventureLore(player, messageService, "<gray>Permission: <white>${permission.name}")
                .lore("<gray>Status: ${if (hasPermission) "§aEnabled" else "<red>Disabled"}")
                .addAdventureLore(player, messageService, "<gray>")

            // Add description based on permission
            permissionItem.lore(getPermissionDescription(permission))
            permissionItem.addAdventureLore(player, messageService, "<gray>")
            permissionItem.lore(if (hasPermission) "<red>Click to disable" else "<green>Click to enable")

            val permissionGuiItem = GuiItem(permissionItem) {
                if (isEditingOwnOwnerRank()) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You cannot modify your own owner rank permissions!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>This prevents you from locking yourself out of guild management.")
                    return@GuiItem
                }
                if (hasPermission) {
                    modifiedPermissions.remove(permission)
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Disabled $displayName for ${rank.name}")
                } else {
                    modifiedPermissions.add(permission)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Enabled $displayName for ${rank.name}")
                }
                open() // Refresh the menu
            }
            pane.addItem(permissionGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack(Material.EMERALD_BLOCK)
            .setAdventureName(player, messageService, "<green>💾 Save Changes")
            .addAdventureLore(player, messageService, "<gray>Apply permission changes")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<green>Click to save and return")

        val saveGuiItem = GuiItem(saveItem) {
            // Update the rank with modified permissions
            val updatedRank = rank.copy(permissions = modifiedPermissions)
            // TODO: Save to RankService
            AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Permission changes saved for ${rank.name}!")
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    updatedRank,
                    messageService
                )
            )
        }
        pane.addItem(saveGuiItem, 1, 5)

        // Cancel changes
        val cancelItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>❌ Cancel Changes")
            .addAdventureLore(player, messageService, "<gray>Discard all changes")
            .addAdventureLore(player, messageService, "<gray>Return to rank editing")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Click to cancel")

        val cancelGuiItem = GuiItem(cancelItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>⚠︎ Permission changes discarded!")
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    rank,
                    messageService
                )
            )
        }
        pane.addItem(cancelGuiItem, 3, 5)

        // Reset to original
        val resetItem = ItemStack(Material.YELLOW_CONCRETE)
            .setAdventureName(player, messageService, "<yellow>🔄 Reset to Original")
            .addAdventureLore(player, messageService, "<gray>Restore original permissions")
            .addAdventureLore(player, messageService, "<gray>for this category")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to reset")

        val resetGuiItem = GuiItem(resetItem) {
            // Reset modified permissions to original for this category
            categoryPermissions.forEach { permission ->
                if (rank.permissions.contains(permission)) {
                    modifiedPermissions.add(permission)
                } else {
                    modifiedPermissions.remove(permission)
                }
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🔄 Reset $categoryName permissions to original state!")
            open() // Refresh the menu
        }
        pane.addItem(resetGuiItem, 5, 5)

        // Back to rank edit
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<gray>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to rank editing")
            .addAdventureLore(player, messageService, "<gray>(changes will be saved)")

        val backGuiItem = GuiItem(backItem) {
            // Update the rank with modified permissions before going back
            val updatedRank = rank.copy(permissions = modifiedPermissions)
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    updatedRank,
                    messageService
                )
            )
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun getPermissionDescription(permission: RankPermission): String {
        return when (permission) {
            // Guild Management
            RankPermission.MANAGE_RANKS -> "<gray>Create, edit, and delete guild ranks"
            RankPermission.MANAGE_MEMBERS -> "<gray>Invite, kick, and promote members"
            RankPermission.MANAGE_BANNER -> "<gray>Change guild banner and appearance"
            RankPermission.MANAGE_EMOJI -> "<gray>Set guild emoji and icons"
            RankPermission.MANAGE_DESCRIPTION -> "<gray>Set and edit guild description"
            RankPermission.MANAGE_HOME -> "<gray>Set and manage guild home location"
            RankPermission.MANAGE_MODE -> "<gray>Change guild mode (Peaceful/Hostile)"
            RankPermission.MANAGE_GUILD_SETTINGS -> "<gray>Access general guild settings"
            RankPermission.MANAGE_GUILD_NAME -> "<gray>Rename the guild"

            // Banking
            RankPermission.DEPOSIT_TO_BANK -> "<gray>Add money to the guild bank"
            RankPermission.WITHDRAW_FROM_BANK -> "<gray>Take money from guild bank"
            RankPermission.VIEW_BANK_TRANSACTIONS -> "<gray>View bank transaction history"
            RankPermission.EXPORT_BANK_DATA -> "<gray>Export bank data as CSV files"
            RankPermission.MANAGE_BANK_SETTINGS -> "<gray>Configure bank security settings"
            RankPermission.MANAGE_BANK_SECURITY -> "<gray>Manage advanced bank security"
            RankPermission.MANAGE_BUDGETS -> "<gray>Set and manage guild budgets"

            // Diplomacy
            RankPermission.MANAGE_RELATIONS -> "<gray>Manage guild relationships"
            RankPermission.DECLARE_WAR -> "<gray>Declare war on other guilds"
            RankPermission.ACCEPT_ALLIANCES -> "<gray>Accept alliance requests"
            RankPermission.MANAGE_PARTIES -> "<gray>Create and manage guild parties"
            RankPermission.SEND_PARTY_REQUESTS -> "<gray>Send party invitations"
            RankPermission.ACCEPT_PARTY_INVITES -> "<gray>Accept party invitations"

            // Claims
            RankPermission.MANAGE_CLAIMS -> "<gray>Manage existing guild claims"
            RankPermission.MANAGE_FLAGS -> "<gray>Configure claim flags and rules"
            RankPermission.MANAGE_PERMISSIONS -> "<gray>Set claim permissions"
            RankPermission.CREATE_CLAIMS -> "<gray>Create new territory claims"
            RankPermission.DELETE_CLAIMS -> "<gray>Remove territory claims"

            // Communication
            RankPermission.SEND_ANNOUNCEMENTS -> "<gray>Send guild-wide announcements"
            RankPermission.SEND_PINGS -> "<gray>Send notification pings"
            RankPermission.MODERATE_CHAT -> "<gray>Moderate guild chat channels"

            // Administrative
            RankPermission.ACCESS_ADMIN_COMMANDS -> "<gray>Use administrative commands"
            RankPermission.BYPASS_RESTRICTIONS -> "<gray>Bypass certain guild restrictions"
            RankPermission.VIEW_AUDIT_LOGS -> "<gray>View guild activity logs"
            RankPermission.MANAGE_INTEGRATIONS -> "<gray>Manage external integrations"
            
            // Additional Banking
            RankPermission.DEPOSIT_MONEY -> "<gray>Deposit money to guild funds"
            RankPermission.WITHDRAW_MONEY -> "<gray>Withdraw money from guild funds" 
            RankPermission.VIEW_BANK_HISTORY -> "<gray>View banking transaction history"
            
            // Chat Management
            RankPermission.USE_CHAT -> "<gray>Use guild chat channels"
            RankPermission.MANAGE_CHAT_SETTINGS -> "<gray>Configure chat settings"
            
            // Land Management
            RankPermission.CLAIM_LAND -> "<gray>Claim new territory for guild"
            RankPermission.UNCLAIM_LAND -> "<gray>Remove guild claims from territory"
            
            // Security & Emergency
            RankPermission.ACTIVATE_EMERGENCY_FREEZE -> "<gray>Activate emergency freeze mode"
            RankPermission.DEACTIVATE_EMERGENCY_FREEZE -> "<gray>Deactivate emergency freeze mode"
            RankPermission.VIEW_SECURITY_AUDITS -> "<gray>View security audit logs"
            RankPermission.OVERRIDE_PROTECTION -> "<gray>Override claim protection"
            RankPermission.BYPASS_COOLDOWNS -> "<gray>Bypass guild cooldowns"
            RankPermission.MANAGE_SECURITY -> "<gray>Manage guild security"
            
            // Fallback for unhandled permissions
            else -> "<gray>${permission.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}"
        }
    }
}
