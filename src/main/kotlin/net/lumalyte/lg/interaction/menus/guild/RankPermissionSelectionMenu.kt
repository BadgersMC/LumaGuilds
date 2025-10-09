package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Menu for selecting permissions within a specific category with bulk operations.
 */
class RankPermissionSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val category: PermissionCategory,
    private var selectedPermissions: MutableSet<RankPermission>,
    private val messageService: MessageService
) : Menu, KoinComponent {

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    init {
        initializeGui()
    }

    override fun open() {
        initializeGui()
        gui.show(player)
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>${category.displayName} Permissions"))
        AntiDupeUtil.protect(gui)

        mainPane = StaticPane(0, 0, 9, 6)
        gui.addPane(mainPane)

        setupNavigation()
        setupPermissionList()
        setupBulkOperations()
        setupBackButton()
    }

    /**
     * Setup navigation and info section
     */
    private fun setupNavigation() {
        var row = 0

        // Category info
        val categoryItem = createMenuItem(
            category.icon,
            category.displayName,
            listOf(
                category.description,
                "Selected: ${selectedPermissions.size} permissions",
                "Total: ${getPermissionsByCategory().size} available"
            )
        )
        mainPane.addItem(GuiItem(categoryItem), 0, row)

        // Selection summary
        val selectionItem = createMenuItem(
            Material.BOOK,
            "Selection Summary",
            listOf(
                "Selected: ${selectedPermissions.size}/${getPermissionsByCategory().size}",
                "Click permissions below to toggle"
            )
        )
        mainPane.addItem(GuiItem(selectionItem), 1, row)

        // Bulk operations button
        val bulkItem = createMenuItem(
            Material.DIAMOND_SWORD,
            "Bulk Operations",
            listOf("Apply operations to all permissions")
        )
        val bulkGuiItem = GuiItem(bulkItem) { event ->
            event.isCancelled = true
            openBulkOperationsMenu()
        }
        mainPane.addItem(bulkGuiItem, 7, row)

        // Done button
        val doneItem = createMenuItem(
            Material.GREEN_WOOL,
            "Done",
            listOf("Return to rank creation")
        )
        val doneGuiItem = GuiItem(doneItem) { event ->
            event.isCancelled = true
            // Pass selected permissions back to rank creation menu
            val menu = RankCreationMenu(menuNavigator, player, guild, messageService)
            val data = mapOf("selectedPermissions" to selectedPermissions)
            menu.passData(data)
            menuNavigator.openMenu(menu)
        }
        mainPane.addItem(doneGuiItem, 8, row)
    }

    /**
     * Setup permission list display
     */
    private fun setupPermissionList() {
        val permissions = getPermissionsByCategory()
        var currentRow = 1
        var currentCol = 0

        permissions.forEach { permission ->
            val isSelected = selectedPermissions.contains(permission)
            val permissionItem = createPermissionItem(permission, isSelected)

            val guiItem = GuiItem(permissionItem) { event ->
                event.isCancelled = true
                // Toggle permission selection
                selectedPermissions = if (selectedPermissions.contains(permission)) {
                    (selectedPermissions - permission).toMutableSet()
                } else {
                    (selectedPermissions + permission).toMutableSet()
                }
                open() // Refresh menu
            }
            mainPane.addItem(guiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }
    }

    /**
     * Setup bulk operations section
     */
    private fun setupBulkOperations() {
        var row = 4

        // Select all in category
        val selectAllItem = createMenuItem(
            Material.GREEN_WOOL,
            "Select All",
            listOf("Select all permissions in this category")
        )
        val selectAllGuiItem = GuiItem(selectAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.SELECT_ALL)
        }
        mainPane.addItem(selectAllGuiItem, 0, row)

        // Deselect all in category
        val deselectAllItem = createMenuItem(
            Material.RED_WOOL,
            "Deselect All",
            listOf("Deselect all permissions in this category")
        )
        val deselectAllGuiItem = GuiItem(deselectAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.DESELECT_ALL)
        }
        mainPane.addItem(deselectAllGuiItem, 1, row)

        // Toggle all in category
        val toggleAllItem = createMenuItem(
            Material.YELLOW_WOOL,
            "Toggle All",
            listOf("Toggle selection for all permissions")
        )
        val toggleAllGuiItem = GuiItem(toggleAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.TOGGLE_ALL)
        }
        mainPane.addItem(toggleAllGuiItem, 2, row)

        // Smart selection based on category
        val smartSelectItem = createMenuItem(
            Material.LIGHT_BLUE_WOOL,
            "Smart Select",
            listOf("Select recommended permissions for ${category.displayName}")
        )
        val smartSelectGuiItem = GuiItem(smartSelectItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.SMART_SELECT)
        }
        mainPane.addItem(smartSelectGuiItem, 3, row)
    }

    /**
     * Setup back button
     */
    private fun setupBackButton() {
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Categories",
            listOf("Return to permission categories")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(RankCreationMenu(menuNavigator, player, guild, messageService))
        }
        mainPane.addItem(backGuiItem, 8, 5)
    }

    /**
     * Open bulk operations menu
     */
    private fun openBulkOperationsMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Bulk Permission Operations"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        var currentRow = 0

        // Select all
        val selectAllItem = createMenuItem(
            Material.GREEN_WOOL,
            "Select All in Category",
            listOf("Select every permission in ${category.displayName}")
        )
        val selectAllGuiItem = GuiItem(selectAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.SELECT_ALL)
        }
        pane.addItem(selectAllGuiItem, 0, currentRow)

        // Deselect all
        val deselectAllItem = createMenuItem(
            Material.RED_WOOL,
            "Deselect All in Category",
            listOf("Deselect every permission in ${category.displayName}")
        )
        val deselectAllGuiItem = GuiItem(deselectAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.DESELECT_ALL)
        }
        pane.addItem(deselectAllGuiItem, 2, currentRow)

        currentRow++

        // Toggle all
        val toggleAllItem = createMenuItem(
            Material.YELLOW_WOOL,
            "Toggle All in Category",
            listOf("Toggle selection for all permissions")
        )
        val toggleAllGuiItem = GuiItem(toggleAllItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.TOGGLE_ALL)
        }
        pane.addItem(toggleAllGuiItem, 4, currentRow)

        currentRow++

        // Smart selection
        val smartItem = createMenuItem(
            Material.LIGHT_BLUE_WOOL,
            "Smart Selection",
            listOf("Select recommended permissions")
        )
        val smartGuiItem = GuiItem(smartItem) { event ->
            event.isCancelled = true
            applyBulkOperation(BulkPermissionOperation.SMART_SELECT)
        }
        pane.addItem(smartGuiItem, 6, currentRow)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back",
            listOf("Return to permission selection")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Apply bulk operation to all permissions in category
     */
    private fun applyBulkOperation(operation: BulkPermissionOperation) {
        val permissions = getPermissionsByCategory()

        when (operation) {
            BulkPermissionOperation.SELECT_ALL -> {
                selectedPermissions.addAll(permissions)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected all permissions in ${category.displayName}")
            }
            BulkPermissionOperation.DESELECT_ALL -> {
                selectedPermissions.removeAll(permissions)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Deselected all permissions in ${category.displayName}")
            }
            BulkPermissionOperation.TOGGLE_ALL -> {
                val currentlySelected = permissions.count { it in selectedPermissions }
                if (currentlySelected >= permissions.size / 2) {
                    // More than half selected, deselect all
                    selectedPermissions.removeAll(permissions)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Deselected all permissions in ${category.displayName}")
                } else {
                    // Less than half selected, select all
                    selectedPermissions.addAll(permissions)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected all permissions in ${category.displayName}")
                }
            }
            BulkPermissionOperation.SMART_SELECT -> {
                // Smart selection based on category
                val smartPermissions = getSmartPermissionsForCategory()
                selectedPermissions.addAll(smartPermissions)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Applied smart selection for ${category.displayName}")
            }
        }

        updateDisplay()
        gui.update()
    }

    /**
     * Get smart permissions for a category (recommended defaults)
     */
    private fun getSmartPermissionsForCategory(): Set<RankPermission> {
        return when (category) {
            PermissionCategory.GUILD_MANAGEMENT -> setOf(
                RankPermission.MANAGE_GUILD_SETTINGS,
                RankPermission.MANAGE_DESCRIPTION
            )
            PermissionCategory.MEMBER_MANAGEMENT -> setOf(
                RankPermission.MANAGE_MEMBERS
            )
            PermissionCategory.BANKING -> setOf(
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS
            )
            PermissionCategory.DIPLOMACY -> setOf(
                RankPermission.MANAGE_RELATIONS
            )
            PermissionCategory.COMMUNICATION -> setOf(
                RankPermission.SEND_ANNOUNCEMENTS
            )
            PermissionCategory.CLAIMS -> setOf(
                RankPermission.CREATE_CLAIMS
            )
            PermissionCategory.SECURITY -> setOf(
                RankPermission.VIEW_AUDIT_LOGS
            )
            else -> emptySet()  // Includes ALL and any other categories
        }
    }

    /**
     * Create permission display item
     */
    private fun createPermissionItem(permission: RankPermission, isSelected: Boolean): ItemStack {
        val material = if (isSelected) Material.LIME_WOOL else Material.GRAY_WOOL
        val displayName = formatPermissionName(permission.name)
        val description = getPermissionDescription(permission)

        return createMenuItem(
            material,
            if (isSelected) "<green>✓ $displayName" else "<gray>○ $displayName",
            listOf(
                description,
                if (isSelected) "<green>Selected" else "<gray>Click to select"
            )
        )
    }

    /**
     * Format permission name for display
     */
    private fun formatPermissionName(permissionName: String): String {
        return permissionName.lowercase()
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    /**
     * Get permission description
     */
    private fun getPermissionDescription(permission: RankPermission): String {
        return when (permission) {
            RankPermission.MANAGE_RANKS -> "Create, edit, and delete guild ranks"
            RankPermission.MANAGE_MEMBERS -> "Invite, kick, and manage guild members"
            RankPermission.MANAGE_GUILD_SETTINGS -> "Modify guild settings and configuration"
            RankPermission.DEPOSIT_TO_BANK -> "Add money to guild bank"
            RankPermission.WITHDRAW_FROM_BANK -> "Remove money from guild bank"
            RankPermission.VIEW_BANK_TRANSACTIONS -> "View guild bank transaction history"
            RankPermission.EXPORT_BANK_DATA -> "Export guild bank data"
            RankPermission.MANAGE_BANK_SETTINGS -> "Configure guild bank settings"
            RankPermission.MANAGE_BANK_SECURITY -> "Configure bank security settings"
            RankPermission.MANAGE_BUDGETS -> "Set and manage guild budgets"
            RankPermission.ACTIVATE_EMERGENCY_FREEZE -> "Activate emergency bank freeze"
            RankPermission.DEACTIVATE_EMERGENCY_FREEZE -> "Deactivate emergency bank freeze"
            RankPermission.VIEW_SECURITY_AUDITS -> "View security audit logs"
            RankPermission.MANAGE_RELATIONS -> "Manage diplomatic relations"
            RankPermission.DECLARE_WAR -> "Declare war on other guilds"
            RankPermission.ACCEPT_ALLIANCES -> "Accept alliance requests"
            RankPermission.SEND_ANNOUNCEMENTS -> "Send announcements to guild"
            RankPermission.SEND_PINGS -> "Send pings to guild members"
            RankPermission.MODERATE_CHAT -> "Moderate guild chat"
            RankPermission.MANAGE_CLAIMS -> "Manage guild claims"
            RankPermission.MANAGE_FLAGS -> "Manage claim flags"
            RankPermission.MANAGE_PERMISSIONS -> "Manage claim permissions"
            RankPermission.CREATE_CLAIMS -> "Create new claims"
            RankPermission.DELETE_CLAIMS -> "Delete existing claims"
            RankPermission.ACCESS_ADMIN_COMMANDS -> "Access administrative commands"
            RankPermission.BYPASS_RESTRICTIONS -> "Bypass various restrictions"
            RankPermission.VIEW_AUDIT_LOGS -> "View audit logs"
            RankPermission.MANAGE_INTEGRATIONS -> "Manage external integrations"
            else -> "No description available"
        }
    }

    /**
     * Update display with current data
     */
    private fun updateDisplay() {
        // Update is handled by individual setup methods
    }

    /**
     * Get permissions for this category
     */
    private fun getPermissionsByCategory(): List<RankPermission> {
        return when (category) {
            PermissionCategory.ALL -> RankPermission.values().toList()
            PermissionCategory.GUILD_MANAGEMENT -> listOf(
                RankPermission.MANAGE_RANKS,
                RankPermission.MANAGE_GUILD_SETTINGS,
                RankPermission.MANAGE_BANNER,
                RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_DESCRIPTION,
                RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_MODE
            )
            PermissionCategory.MEMBER_MANAGEMENT -> listOf(
                RankPermission.MANAGE_MEMBERS,
                RankPermission.SEND_PARTY_REQUESTS,
                RankPermission.ACCEPT_PARTY_INVITES,
                RankPermission.MANAGE_PARTIES
            )
            PermissionCategory.BANKING -> listOf(
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.EXPORT_BANK_DATA,
                RankPermission.MANAGE_BANK_SETTINGS,
                RankPermission.MANAGE_BANK_SECURITY,
                RankPermission.MANAGE_BUDGETS
            )
            PermissionCategory.DIPLOMACY -> listOf(
                RankPermission.MANAGE_RELATIONS,
                RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES
            )
            PermissionCategory.COMMUNICATION -> listOf(
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.SEND_PINGS,
                RankPermission.MODERATE_CHAT
            )
            PermissionCategory.CLAIMS -> listOf(
                RankPermission.MANAGE_CLAIMS,
                RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS,
                RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS
            )
            PermissionCategory.SECURITY -> listOf(
                RankPermission.ACTIVATE_EMERGENCY_FREEZE,
                RankPermission.DEACTIVATE_EMERGENCY_FREEZE,
                RankPermission.VIEW_SECURITY_AUDITS,
                RankPermission.ACCESS_ADMIN_COMMANDS,
                RankPermission.BYPASS_RESTRICTIONS,
                RankPermission.VIEW_AUDIT_LOGS,
                RankPermission.MANAGE_INTEGRATIONS
            )
        }
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text(name)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))

        if (lore.isNotEmpty()) {
            val loreComponents = lore.map { line ->
                Component.text(line)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(loreComponents)
        }

        item.itemMeta = meta
        return item
    }

    override fun passData(data: Any?) {
        // No data handling needed for this menu
    }

    /**
     * Bulk permission operations
     */
    enum class BulkPermissionOperation {
        SELECT_ALL,
        DESELECT_ALL,
        TOGGLE_ALL,
        SMART_SELECT
    }
}
