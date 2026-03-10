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
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PermissionCategoryMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                            private var guild: Guild, private var rank: Rank,
                            private val categoryName: String, 
                            private val categoryPermissions: List<RankPermission>): Menu, KoinComponent {

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
        val gui = ChestGui(6, "§6$categoryName - ${rank.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
            .name("§6🔧 $categoryName")
            .lore("§7Managing permissions for: §f${rank.name}")
            .lore("§7Category: §f$categoryName")
            .lore("§7Total permissions: §f${categoryPermissions.size}")
            
        // Add owner protection warning if editing own owner rank
        if (isEditingOwnOwnerRank()) {
            infoItem.lore("§7")
                .lore("§c⚠︎ OWNER RANK PROTECTION ACTIVE")
                .lore("§7Permission changes are blocked")
                .lore("§7to prevent guild owner lockout")
        }

        pane.addItem(GuiItem(infoItem), 1, 0)

        // Enable all button
        val enableAllItem = ItemStack.of(Material.LIME_CONCRETE)
            .name("§a✅ Enable All")
            .lore("§7Grant all $categoryName permissions")
            .lore("§7to this rank")
            .lore("§7")
            .lore("§aClick to enable all")

        val enableAllGuiItem = GuiItem(enableAllItem) {
            if (isEditingOwnOwnerRank()) {
                player.sendMessage("§c❌ You cannot modify your own owner rank permissions!")
                player.sendMessage("§7This prevents you from locking yourself out of guild management.")
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.add(permission)
            }
            player.sendMessage("§a✅ Enabled all $categoryName permissions!")
            open() // Refresh the menu
        }
        pane.addItem(enableAllGuiItem, 3, 0)

        // Disable all button
        val disableAllItem = ItemStack.of(Material.RED_CONCRETE)
            .name("§c❌ Disable All")
            .lore("§7Remove all $categoryName permissions")
            .lore("§7from this rank")
            .lore("§7")
            .lore("§cClick to disable all")

        val disableAllGuiItem = GuiItem(disableAllItem) {
            if (isEditingOwnOwnerRank()) {
                player.sendMessage("§c❌ You cannot modify your own owner rank permissions!")
                player.sendMessage("§7This prevents you from locking yourself out of guild management.")
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.remove(permission)
            }
            player.sendMessage("§c❌ Disabled all $categoryName permissions!")
            open() // Refresh the menu
        }
        pane.addItem(disableAllGuiItem, 5, 0)

        // Permission count
        val enabledCount = categoryPermissions.count { modifiedPermissions.contains(it) }
        val countItem = ItemStack.of(Material.BOOK)
            .name("§6📊 Permission Status")
            .lore("§7Enabled: §a$enabledCount§7/§f${categoryPermissions.size}")
            .lore("§7")
            .lore("§7Click individual permissions below")

        pane.addItem(GuiItem(countItem), 7, 0)
    }

    private fun addPermissionButtons(pane: StaticPane) {
        categoryPermissions.forEachIndexed { index, permission ->
            val row = 1 + (index / 7) // 7 permissions per row
            val col = 1 + (index % 7)

            if (row > 4) return@forEachIndexed // Don't overflow into action row

            val hasPermission = modifiedPermissions.contains(permission)
            val displayName = permission.name.replace("_", " ").lowercase()
                .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val permissionItem = ItemStack(
                if (hasPermission) Material.LIME_CONCRETE_POWDER else Material.RED_CONCRETE_POWDER
            )
                .name("${if (hasPermission) "§a✓" else "§c✗"} §f$displayName")
                .lore("§7Permission: §f${permission.name}")
                .lore("§7Status: ${if (hasPermission) "§aEnabled" else "§cDisabled"}")
                .lore("§7")

            // Add description based on permission
            permissionItem.lore(getPermissionDescription(permission))
            permissionItem.lore("§7")
            permissionItem.lore(if (hasPermission) "§cClick to disable" else "§aClick to enable")

            val permissionGuiItem = GuiItem(permissionItem) {
                if (isEditingOwnOwnerRank()) {
                    player.sendMessage("§c❌ You cannot modify your own owner rank permissions!")
                    player.sendMessage("§7This prevents you from locking yourself out of guild management.")
                    return@GuiItem
                }
                if (hasPermission) {
                    modifiedPermissions.remove(permission)
                    player.sendMessage("§c❌ Disabled $displayName for ${rank.name}")
                } else {
                    modifiedPermissions.add(permission)
                    player.sendMessage("§a✅ Enabled $displayName for ${rank.name}")
                }
                open() // Refresh the menu
            }
            pane.addItem(permissionGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name("§a💾 Save Changes")
            .lore("§7Apply permission changes")
            .lore("§7")
            .lore("§aClick to save and return")

        val saveGuiItem = GuiItem(saveItem) {
            // Update the rank with modified permissions
            val updatedRank = rank.copy(permissions = modifiedPermissions)
            val success = rankService.updateRank(updatedRank, player.uniqueId)
            if (success) {
                rank = updatedRank // Update local reference
                player.sendMessage("§a✅ Permission changes saved for ${rank.name}!")
            } else {
                player.sendMessage("§c❌ Failed to save permission changes")
            }
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    updatedRank
                )
            )
        }
        pane.addItem(saveGuiItem, 1, 5)

        // Cancel changes
        val cancelItem = ItemStack.of(Material.BARRIER)
            .name("§c❌ Cancel Changes")
            .lore("§7Discard all changes")
            .lore("§7Return to rank editing")
            .lore("§7")
            .lore("§cClick to cancel")

        val cancelGuiItem = GuiItem(cancelItem) {
            player.sendMessage("§e⚠︎ Permission changes discarded!")
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    rank
                )
            )
        }
        pane.addItem(cancelGuiItem, 3, 5)

        // Reset to original
        val resetItem = ItemStack.of(Material.YELLOW_CONCRETE)
            .name("§e🔄 Reset to Original")
            .lore("§7Restore original permissions")
            .lore("§7for this category")
            .lore("§7")
            .lore("§eClick to reset")

        val resetGuiItem = GuiItem(resetItem) {
            // Reset modified permissions to original for this category
            categoryPermissions.forEach { permission ->
                if (rank.permissions.contains(permission)) {
                    modifiedPermissions.add(permission)
                } else {
                    modifiedPermissions.remove(permission)
                }
            }
            player.sendMessage("§e🔄 Reset $categoryName permissions to original state!")
            open() // Refresh the menu
        }
        pane.addItem(resetGuiItem, 5, 5)

        // Back to rank edit
        val backItem = ItemStack.of(Material.ARROW)
            .name("§7⬅ Back")
            .lore("§7Return to rank editing")
            .lore("§7(changes will be saved)")

        val backGuiItem = GuiItem(backItem) {
            // Update the rank with modified permissions before going back
            val updatedRank = rank.copy(permissions = modifiedPermissions)
            menuNavigator.openMenu(
                net.lumalyte.lg.interaction.menus.guild.RankEditMenu(
                    menuNavigator,
                    player,
                    guild,
                    updatedRank
                )
            )
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun getPermissionDescription(permission: RankPermission): String {
        return when (permission) {
            // Guild Management
            RankPermission.MANAGE_RANKS -> "§7Create, edit, and delete guild ranks"
            RankPermission.MANAGE_MEMBERS -> "§7Invite, kick, and promote members"
            RankPermission.MANAGE_BANNER -> "§7Change guild banner and appearance"
            RankPermission.MANAGE_EMOJI -> "§7Set guild emoji and icons"
            RankPermission.MANAGE_DESCRIPTION -> "§7Set and edit guild description"
            RankPermission.MANAGE_HOME -> "§7Set and manage guild home location"
            RankPermission.MANAGE_MODE -> "§7Change guild mode (Peaceful/Hostile)"
            RankPermission.MANAGE_GUILD_SETTINGS -> "§7Access general guild settings"

            // Banking
            RankPermission.DEPOSIT_TO_BANK -> "§7Add money to the guild bank"
            RankPermission.WITHDRAW_FROM_BANK -> "§7Take money from guild bank"
            RankPermission.VIEW_BANK_TRANSACTIONS -> "§7View bank transaction history"
            RankPermission.EXPORT_BANK_DATA -> "§7Export bank data as CSV files"
            RankPermission.MANAGE_BANK_SETTINGS -> "§7Configure bank settings and fees"
            RankPermission.PLACE_VAULT -> "§7Place the physical guild vault chest"
            RankPermission.ACCESS_VAULT -> "§7Open and view vault contents"
            RankPermission.DEPOSIT_TO_VAULT -> "§7Deposit items to physical vault"
            RankPermission.WITHDRAW_FROM_VAULT -> "§7Withdraw items from physical vault"
            RankPermission.MANAGE_VAULT -> "§7Full vault management access"
            RankPermission.BREAK_VAULT -> "§7Break and move the vault chest"
            RankPermission.ACCESS_SHOP_CHESTS -> "§7Open chests in guild shop regions"
            RankPermission.EDIT_SHOP_STOCK -> "§7Modify inventory in shop chests"
            RankPermission.MODIFY_SHOP_PRICES -> "§7Change prices on shop signs"

            // Diplomacy
            RankPermission.MANAGE_RELATIONS -> "§7Manage guild relationships"
            RankPermission.DECLARE_WAR -> "§7Declare war on other guilds"
            RankPermission.ACCEPT_ALLIANCES -> "§7Accept alliance requests"
            RankPermission.MANAGE_PARTIES -> "§7Create and manage guild parties"
            RankPermission.SEND_PARTY_REQUESTS -> "§7Send party invitations"
            RankPermission.ACCEPT_PARTY_INVITES -> "§7Accept party invitations"

            // Claims
            RankPermission.MANAGE_CLAIMS -> "§7Manage existing guild claims"
            RankPermission.MANAGE_FLAGS -> "§7Configure claim flags and rules"
            RankPermission.MANAGE_PERMISSIONS -> "§7Set claim permissions"
            RankPermission.CREATE_CLAIMS -> "§7Create new territory claims"
            RankPermission.DELETE_CLAIMS -> "§7Remove territory claims"

            // Communication
            RankPermission.SEND_ANNOUNCEMENTS -> "§7Send guild-wide announcements"
            RankPermission.SEND_PINGS -> "§7Send notification pings"
            RankPermission.MODERATE_CHAT -> "§7Moderate guild chat channels"

            // Administrative
            RankPermission.ACCESS_ADMIN_COMMANDS -> "§7Use administrative commands"
            RankPermission.BYPASS_RESTRICTIONS -> "§7Bypass certain guild restrictions"
            RankPermission.VIEW_AUDIT_LOGS -> "§7View guild activity logs"
            RankPermission.MANAGE_INTEGRATIONS -> "§7Manage external integrations"
        }
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Rank -> rank = data
        }
    }
}
