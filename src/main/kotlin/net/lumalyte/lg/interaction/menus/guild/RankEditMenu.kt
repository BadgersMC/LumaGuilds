package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.MenuUtils
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
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

class RankEditMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                  private var guild: Guild, private var rank: Rank, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    
    // Edit state
    private var inputMode: String = "" // "name" or "icon"
    private var selectedIcon: Material = loadRankIcon() // Track selected icon
    private var inheritanceMode: Boolean = false // Track inheritance settings

    // Cached properties for performance
    private val isOwnerRank: Boolean
        get() = rank.id == rankService.getHighestRank(guild.id)?.id

    private val isEditingOwnOwnerRank: Boolean
        get() = isOwnerRank && isGuildOwner()

    private val memberCount: Int
        get() = memberService.getMembersByRank(guild.id, rank.id).size
    
    // Check if the current player is the guild owner
    private fun isGuildOwner(): Boolean {
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val ownerRank = rankService.getHighestRank(guild.id)
        return playerRank?.id == ownerRank?.id
    }
    

    // Load the current rank's icon or default to AIR
    private fun loadRankIcon(): Material {
        return try {
            rank.icon?.let { Material.valueOf(it) } ?: Material.AIR
        } catch (e: IllegalArgumentException) {
            // If the stored icon name is invalid, default to AIR
            Material.AIR
        }
    }
    
    // Check if claims are enabled
    private fun areClaimsEnabled(): Boolean {
        return configService.loadConfig().claimsEnabled
    }
    
    // Get claim-related permissions
    private fun getClaimPermissions(): Set<RankPermission> {
        return setOf(
            RankPermission.MANAGE_CLAIMS,
            RankPermission.MANAGE_FLAGS,
            RankPermission.MANAGE_PERMISSIONS,
            RankPermission.CREATE_CLAIMS,
            RankPermission.DELETE_CLAIMS
        )
    }

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Edit Rank: ${rank.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Rank Information
        addRankInfoSection(pane)

        // Row 1-4: Permission Categories
        addPermissionCategories(pane)

        // Row 4: Advanced features
        addAdvancedFeatures(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addRankInfoSection(pane: StaticPane) {
        // Rank name and basic info
        val infoItem = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<gold>📝 Rank Information")
            .addAdventureLore(player, messageService, "<gray>Name: <white>${rank.name}")
            .addAdventureLore(player, messageService, "<gray>Priority: <white>${rank.priority}")
            .addAdventureLore(player, messageService, "<gray>Members: <white>${memberCount} players")
            
        // Add owner protection warning if editing own owner rank
        if (isEditingOwnOwnerRank) {
            infoItem.addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<red>⚠︎ OWNER RANK PROTECTION")
                .addAdventureLore(player, messageService, "<gray>Permission changes are blocked")
                .addAdventureLore(player, messageService, "<gray>to prevent self-lockout")
        }
        
        infoItem.addAdventureLore(player, messageService, "<gray>")

        if (inputMode == "name") {
            infoItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR NAME INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type the new rank name in chat")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            infoItem.addAdventureLore(player, messageService, "<yellow>Click to rename rank")
        }

        val infoGuiItem = GuiItem(infoItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Already waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(infoGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (selectedIcon == Material.AIR) Material.DIAMOND_SWORD else selectedIcon
        val iconItem = ItemStack(displayIcon)
            .setAdventureName(player, messageService, "<gold>🎨 Rank Icon")
            .lore("<gray>Current: <white>${if (selectedIcon == Material.AIR) "Not set" else selectedIcon.name}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Examples:")
            .addAdventureLore(player, messageService, "<gray>• diamond, gold_ingot, emerald")
            .addAdventureLore(player, messageService, "<gray>• diamond_sword, golden_apple")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>📖 Clickable link in chat")

        if (inputMode == "icon") {
            iconItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR ICON INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type the material name in chat")
                .addAdventureLore(player, messageService, "<gray>Examples: diamond, gold_ingot, etc.")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            iconItem.addAdventureLore(player, messageService, "<yellow>Click to change rank icon")
        }

        val iconGuiItem = GuiItem(iconItem) {
            if (inputMode != "icon") {
                startIconInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Already waiting for icon input. Type the material name or click cancel.")
            }
        }
        pane.addItem(iconGuiItem, 3, 0)

        // Permission count
        val permCountItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📊 Permission Summary")
            .addAdventureLore(player, messageService, "<gray>Total Permissions: <white>${rank.permissions.size}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Manage permissions below")

        pane.addItem(GuiItem(permCountItem), 7, 0)
    }

    private fun addPermissionCategories(pane: StaticPane) {
        val baseCategories = mutableMapOf(
            "Guild Management" to listOf(
                RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_DESCRIPTION, RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_MODE, RankPermission.MANAGE_GUILD_SETTINGS
            ),
            "Banking" to listOf(
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS, RankPermission.EXPORT_BANK_DATA,
                RankPermission.MANAGE_BANK_SETTINGS
            ),
            "Diplomacy" to listOf(
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_PARTIES,
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES
            ),
            "Communication" to listOf(
                RankPermission.SEND_ANNOUNCEMENTS, RankPermission.SEND_PINGS,
                RankPermission.MODERATE_CHAT
            ),
            "Administrative" to listOf(
                RankPermission.ACCESS_ADMIN_COMMANDS, RankPermission.BYPASS_RESTRICTIONS,
                RankPermission.VIEW_AUDIT_LOGS, RankPermission.MANAGE_INTEGRATIONS
            )
        )

        // Only add Claims category if claims are enabled
        if (areClaimsEnabled()) {
            baseCategories["Claims"] = listOf(
                RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS
            )
        }

        val categories = baseCategories

        categories.entries.forEachIndexed { index, (categoryName, permissions) ->
            val row = 1 + (index / 3)
            val col = (index % 3) * 3 + 1

            val hasAnyPermission = permissions.any { rank.permissions.contains(it) }
            val hasAllPermissions = permissions.all { rank.permissions.contains(it) }

            val categoryItem = ItemStack(
                when (categoryName) {
                    "Guild Management" -> Material.GOLDEN_SWORD
                    "Banking" -> Material.GOLD_INGOT
                    "Diplomacy" -> Material.WRITABLE_BOOK
                    "Claims" -> Material.GRASS_BLOCK
                    "Communication" -> Material.BELL
                    "Administrative" -> Material.COMMAND_BLOCK
                    else -> Material.PAPER
                }
            ).setAdventureName(player, messageService, "<gold>🔧 $categoryName")

            categoryItem.addAdventureLore(player, messageService, "<gray>Permissions in this category:")
            permissions.forEach { permission ->
                val hasPermission = rank.permissions.contains(permission)
                val displayName = permission.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                categoryItem.lore("<gray>• ${if (hasPermission) "§a✓" else "<red>✗"} §f$displayName")
            }

            categoryItem.addAdventureLore(player, messageService, "<gray>")
            when {
                hasAllPermissions -> categoryItem.addAdventureLore(player, messageService, "<green>✅ All permissions enabled")
                hasAnyPermission -> categoryItem.addAdventureLore(player, messageService, "<yellow>⚠️ Some permissions enabled")
                else -> categoryItem.addAdventureLore(player, messageService, "<red>❌ No permissions enabled")
            }
            categoryItem.addAdventureLore(player, messageService, "<gray>")
            categoryItem.addAdventureLore(player, messageService, "<yellow>Click to manage permissions")

            val categoryGuiItem = GuiItem(categoryItem) {
                // Prevent owner from removing their own permissions
                if (isEditingOwnOwnerRank) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You cannot modify your own owner rank permissions!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>This prevents you from locking yourself out of guild management.")
                    return@GuiItem
                }
                // Prevent opening Claims category when claims are disabled
                if (categoryName == "Claims" && !areClaimsEnabled()) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Claims system is disabled - claim permissions are not available.")
                    return@GuiItem
                }
                openPermissionCategoryMenu(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    /**
     * Add advanced features section (rank reset, deletion, inheritance)
     */
    private fun addAdvancedFeatures(pane: StaticPane) {
        // Rank reset button
        val resetItem = ItemStack(Material.REDSTONE)
            .setAdventureName(player, messageService, "<red>🔄 Reset Permissions")
            .addAdventureLore(player, messageService, "<gray>Clear all permissions from this rank")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>⚠️ This will remove ALL permissions!")
            .addAdventureLore(player, messageService, "<gray>Members will lose access to features")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to reset permissions")

        val resetGuiItem = GuiItem(resetItem) { event ->
            event.isCancelled = true
            openResetConfirmationMenu()
        }
        pane.addItem(resetGuiItem, 0, 4)

        // Rank deletion button (only for non-owner ranks)
        if (!isOwnerRank) {
            val deleteItem = ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>🗑️ Delete Rank")
                .addAdventureLore(player, messageService, "<gray>Permanently delete this rank")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<gray>⚠️ This action cannot be undone!")
                .addAdventureLore(player, messageService, "<gray>All members will need reassignment")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<yellow>Click to delete rank")

            val deleteGuiItem = GuiItem(deleteItem) { event ->
                event.isCancelled = true
                openDeleteConfirmationMenu()
            }
            pane.addItem(deleteGuiItem, 1, 4)
        } else {
            val cannotDeleteItem = ItemStack(Material.GRAY_WOOL)
                .setAdventureName(player, messageService, "<gray>Cannot Delete Owner Rank")
                .addAdventureLore(player, messageService, "<gray>Owner ranks cannot be deleted")
                .addAdventureLore(player, messageService, "<gray>This prevents guild lockout")
            pane.addItem(GuiItem(cannotDeleteItem), 1, 4)
        }

        // Inheritance configuration
        val inheritanceItem = ItemStack(Material.CHAIN)
            .setAdventureName(player, messageService, "<gold>🔗 Permission Inheritance")
            .addAdventureLore(player, messageService, "<gray>Configure which ranks this rank inherits from")
            .addAdventureLore(player, messageService, "<gray>")
            .lore("<gray>Current: ${if (inheritanceMode) "§aEnabled" else "<gray>Disabled"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to configure inheritance")

        val inheritanceGuiItem = GuiItem(inheritanceItem) { event ->
            event.isCancelled = true
            openInheritanceMenu()
        }
        pane.addItem(inheritanceGuiItem, 2, 4)

        // Bulk permission operations
        val bulkItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<gold>⚔️ Bulk Operations")
            .addAdventureLore(player, messageService, "<gray>Apply operations to all permissions")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>• Select/Deselect all")
            .addAdventureLore(player, messageService, "<gray>• Smart selection")
            .addAdventureLore(player, messageService, "<gray>• Category operations")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for bulk operations")

        val bulkGuiItem = GuiItem(bulkItem) { event ->
            event.isCancelled = true
            openBulkOperationsMenu()
        }
        pane.addItem(bulkGuiItem, 3, 4)
    }

    /**
     * Open rank reset confirmation menu
     */
    private fun openResetConfirmationMenu() {
        val confirmationMenu = object : Menu {
            override fun open() {
                val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red>Confirm Permission Reset"))
                AntiDupeUtil.protect(gui)

                val pane = StaticPane(0, 0, 9, 3)
                gui.addPane(pane)

                // Warning message
                val warningItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>⚠️ CONFIRM PERMISSION RESET")
                    .addAdventureLore(player, messageService, "<gray>This will remove ALL permissions from:")
                    .addAdventureLore(player, messageService, "<white>${rank.name}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Affected members: ${memberCount}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<red>This action cannot be undone!")
                    .addAdventureLore(player, messageService, "<gray>Members may lose access to features")

                pane.addItem(GuiItem(warningItem), 4, 0)

                // Confirm button
                val confirmItem = ItemStack(Material.GREEN_WOOL)
                    .setAdventureName(player, messageService, "<green>CONFIRM RESET")
                    .addAdventureLore(player, messageService, "<gray>Reset all permissions")

                val confirmGuiItem = GuiItem(confirmItem) { event ->
                    event.isCancelled = true
                    val success = resetRankPermissions()
                    if (success) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully reset all permissions for ${rank.name}!")
                        menuNavigator.openMenu(this@RankEditMenu)
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to reset permissions!")
                    }
                }
                pane.addItem(confirmGuiItem, 3, 2)

                // Cancel button
                val cancelItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>CANCEL")
                    .addAdventureLore(player, messageService, "<gray>Cancel the reset")

                val cancelGuiItem = GuiItem(cancelItem) { event ->
                    event.isCancelled = true
                    menuNavigator.openMenu(this@RankEditMenu)
                }
                pane.addItem(cancelGuiItem, 5, 2)

                gui.show(player)
            }

            override fun passData(data: Any?) = Unit
        }

        menuNavigator.openMenu(confirmationMenu)
    }

    /**
     * Open rank deletion confirmation menu
     */
    private fun openDeleteConfirmationMenu() {
        val confirmationMenu = object : Menu {
            override fun open() {
                val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red>Confirm Rank Deletion"))
                AntiDupeUtil.protect(gui)

                val pane = StaticPane(0, 0, 9, 4)
                gui.addPane(pane)

                // Warning message
                val warningItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>⚠️ CONFIRM RANK DELETION")
                    .addAdventureLore(player, messageService, "<gray>This will permanently delete:")
                    .addAdventureLore(player, messageService, "<white>${rank.name}")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<gray>Affected members: ${memberCount}")
                    .addAdventureLore(player, messageService, "<gray>Members will need reassignment")
                    .addAdventureLore(player, messageService, "<gray>")
                    .addAdventureLore(player, messageService, "<red>This action cannot be undone!")

                pane.addItem(GuiItem(warningItem), 4, 0)

                // Reassignment options
                val reassignLabel = ItemStack(Material.PAPER)
                    .setAdventureName(player, messageService, "<gold>Member Reassignment")
                    .addAdventureLore(player, messageService, "<gray>Select a rank to move members to:")

                pane.addItem(GuiItem(reassignLabel), 4, 1)

                // Get available ranks for reassignment
                val availableRanks = rankService.getRanksByGuild(guild.id)
                    .filter { it.id != rank.id } // Don't include the rank being deleted

                if (availableRanks.isNotEmpty()) {
                    availableRanks.toList().take(5).forEachIndexed { index: Int, targetRank: Rank ->
                        val rankItem = ItemStack(Material.BOOK)
                            .setAdventureName(player, messageService, "<white>${targetRank.name}")
                            .addAdventureLore(player, messageService, "<gray>Priority: ${targetRank.priority}")
                            .addAdventureLore(player, messageService, "<gray>Click to select for reassignment")

                        val rankGuiItem = GuiItem(rankItem) { event ->
                            event.isCancelled = true
                            val success = deleteRankWithReassignment(targetRank)
                            if (success) {
                                AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully deleted ${rank.name} and reassigned ${memberCount} members to ${targetRank.name}!")
                                player.closeInventory()
                            } else {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to delete rank!")
                            }
                        }
                        pane.addItem(rankGuiItem, index * 2, 2)
                    }
                } else {
                    val noRanksItem = ItemStack(Material.BARRIER)
                        .setAdventureName(player, messageService, "<red>No Other Ranks Available")
                        .addAdventureLore(player, messageService, "<gray>Cannot delete the only rank")
                    pane.addItem(GuiItem(noRanksItem), 4, 2)
                }

                // Cancel button
                val cancelItem = ItemStack(Material.RED_WOOL)
                    .setAdventureName(player, messageService, "<red>CANCEL")
                    .addAdventureLore(player, messageService, "<gray>Cancel the deletion")

                val cancelGuiItem = GuiItem(cancelItem) { event ->
                    event.isCancelled = true
                    menuNavigator.openMenu(this@RankEditMenu)
                }
                pane.addItem(cancelGuiItem, 8, 3)

                gui.show(player)
            }

            override fun passData(data: Any?) = Unit
        }

        menuNavigator.openMenu(confirmationMenu)
    }

    /**
     * Open inheritance configuration menu
     */
    private fun openInheritanceMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue><blue>Rank Inheritance Configuration"))

        val mainPane = StaticPane(0, 0, 9, 5)
        val navigationPane = StaticPane(0, 5, 9, 1)

        // Get all ranks for this guild
        val allRanks = rankService.listRanks(guild.id)

        // Current inheritance info
        val currentInheritance = rankService.getRankInheritance(rank.id)

        var slot = 0
        // Show current parent ranks
        mainPane.addItem(GuiItem(ItemStack(Material.GREEN_WOOL).setAdventureName(player, messageService, "<green>Parent Ranks").addAdventureLore(player, messageService, "<gray>Ranks this rank inherits from")), 0, 0)

        val parents = currentInheritance?.parents ?: emptySet()
        if (parents.isEmpty()) {
            mainPane.addItem(GuiItem(ItemStack(Material.GRAY_STAINED_GLASS_PANE).setAdventureName(player, messageService, "<gray>No Parent Ranks").addAdventureLore(player, messageService, "<gray>Click to add inheritance")), 1, 0)
        } else {
            var slot = 0
            parents.forEach { parentRank ->
                val parentItem = ItemStack(Material.LIME_WOOL)
                    .setAdventureName(player, messageService, "<green>${parentRank.name}")
                    .addAdventureLore(player, messageService, "<gray>Inherits permissions from this rank")
                    .addAdventureLore(player, messageService, "<red>Click to remove inheritance")

                val parentGuiItem = GuiItem(parentItem) { event ->
                    event.isCancelled = true
                    if (rankService.removeRankInheritance(rank.id, parentRank.id)) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Removed inheritance from ${parentRank.name}")
                        openInheritanceMenu() // Refresh
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to remove inheritance")
                    }
                }
                mainPane.addItem(parentGuiItem, slot % 7 + 1, slot / 7)
                slot++
            }
        }

        // Show available ranks to inherit from (excluding current rank and its children)
        val availableParents = allRanks.filter { it.id != rank.id && !isChildRank(it.id, rank.id) }
        if (availableParents.isNotEmpty()) {
            slot = 0
            mainPane.addItem(GuiItem(ItemStack(Material.BLUE_WOOL).setAdventureName(player, messageService, "<blue>Available Parent Ranks").addAdventureLore(player, messageService, "<gray>Ranks this rank can inherit from")), 0, 2)

            availableParents.forEach { availableRank ->
                val availableItem = ItemStack(Material.BLUE_WOOL)
                    .setAdventureName(player, messageService, "<blue>${availableRank.name}")
                    .addAdventureLore(player, messageService, "<gray>Click to add inheritance")

                val availableGuiItem = GuiItem(availableItem) { event ->
                    event.isCancelled = true
                    if (rankService.addRankInheritance(rank.id, availableRank.id)) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Added inheritance from ${availableRank.name}")
                        openInheritanceMenu() // Refresh
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to add inheritance")
                    }
                }

                val x = slot % 7 + 1
                val y = slot / 7 + 2
                if (y < 5) { // Keep within bounds
                    mainPane.addItem(availableGuiItem, x, y)
                    slot++
                }
            }
        }

        // Show child ranks (ranks that inherit from this rank)
        val childRanks = allRanks.filter { isChildRank(rank.id, it.id) }
        if (childRanks.isNotEmpty()) {
            slot = 0
            mainPane.addItem(GuiItem(ItemStack(Material.ORANGE_WOOL).setAdventureName(player, messageService, "<gold>Child Ranks").addAdventureLore(player, messageService, "<gray>Ranks that inherit from this rank")), 0, 4)

            childRanks.forEach { childRank ->
                val childItem = ItemStack(Material.ORANGE_WOOL)
                    .setAdventureName(player, messageService, "<gold>${childRank.name}")
                    .addAdventureLore(player, messageService, "<gray>Inherits permissions from this rank")

                mainPane.addItem(GuiItem(childItem), slot % 7 + 1, slot / 7 + 4)
                slot++
            }
        }

        // Navigation
        val backItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<red>Back").addAdventureLore(player, messageService, "<gray>Return to rank editing")
        val backGuiItem = GuiItem(backItem) {
            open() // Return to main edit menu
        }
        navigationPane.addItem(backGuiItem, 0, 0)

        val infoItem = ItemStack(Material.BOOK).setAdventureName(player, messageService, "<yellow>Inheritance Info").lore(
            "<gray>Parent ranks provide permissions to this rank",
            "<gray>Child ranks receive permissions from this rank",
            "<gray>Inheritance creates a hierarchy of permissions"
        )
        navigationPane.addItem(GuiItem(infoItem), 4, 0)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)
        gui.show(player)
    }

    /**
     * Check if rankA is a child of rankB (rankA inherits from rankB)
     */
    private fun isChildRank(parentRankId: java.util.UUID, childRankId: java.util.UUID): Boolean {
        val childInheritance = rankService.getRankInheritance(childRankId) ?: return false
        return childInheritance.parents.any { it.id == parentRankId }
    }

    /**
     * Open bulk operations menu
     */
    private fun openBulkOperationsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Bulk operations coming soon!")
    }

    /**
     * Reset all permissions for this rank
     */
    private fun resetRankPermissions(): Boolean {
        return try {
            val updatedRank = rank.copy(permissions = emptySet())
            rankService.updateRank(updatedRank)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete rank with member reassignment
     */
    private fun deleteRankWithReassignment(targetRank: Rank): Boolean {
        return try {
            // Reassign all members of this rank to the target rank
            val members = memberService.getMembersByRank(guild.id, rank.id)
            var successCount = 0

            members.forEach { member ->
                if (memberService.changeMemberRank(member.playerId, guild.id, targetRank.id, player.uniqueId)) {
                    successCount++
                }
            }

            // Delete the rank
            rankService.deleteRank(rank.id, player.uniqueId)
        } catch (e: Exception) {
            false
        }
    }


    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack(Material.EMERALD_BLOCK)
            .setAdventureName(player, messageService, "<green>💾 Save Changes")
            .addAdventureLore(player, messageService, "<gray>Apply all permission changes")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<green>Click to save")

        val saveGuiItem = GuiItem(saveItem) {
            // Create updated rank with current permissions and new icon
            val iconString = if (selectedIcon == Material.AIR) null else selectedIcon.name
            val updatedRank = rank.copy(
                permissions = rank.permissions,
                icon = iconString
            )

            // Save to database
            val success = rankService.updateRank(updatedRank)

            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Rank changes saved!")
                // Update local rank reference
                rank = updatedRank
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to save rank changes!")
            }

            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(saveGuiItem, 1, 5)

        // Reset to defaults
        val resetItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>🔄 Reset Permissions")
            .addAdventureLore(player, messageService, "<gray>Clear all permissions")
            .addAdventureLore(player, messageService, "<gray>This cannot be undone")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Click to reset")

        val resetGuiItem = GuiItem(resetItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Reset functionality coming soon!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>This will clear all permissions for this rank.")
        }
        pane.addItem(resetGuiItem, 3, 5)

        // Delete rank
        val deleteItem = ItemStack(Material.TNT)
            .setAdventureName(player, messageService, "<dark_red>🗑️ Delete Rank")
            .addAdventureLore(player, messageService, "<gray>Permanently remove this rank")
            .addAdventureLore(player, messageService, "<gray>Members will be unassigned")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<dark_red>⚠️ DESTRUCTIVE ACTION")
            .addAdventureLore(player, messageService, "<red>Click to delete")

        val deleteGuiItem = GuiItem(deleteItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Rank deletion coming soon!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>This will show a confirmation menu.")
        }
        pane.addItem(deleteGuiItem, 5, 5)

        // Back to rank management
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<gray>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to rank management")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun openPermissionCategoryMenu(categoryName: String, permissions: List<RankPermission>) {
        menuNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.guild.PermissionCategoryMenu(
                menuNavigator,
                player,
                guild,
                rank,
                categoryName,
                permissions,
                messageService
            )
        )
    }


    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== RANK NAME EDIT ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type the new rank name in chat.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Current name: <white>${rank.name}")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Requirements:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• 1-24 characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• No special characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=======================")
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== RANK ICON EDIT ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type the material name in chat.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Examples:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  diamond, gold_ingot, emerald")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  diamond_sword, golden_apple")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  iron_block, netherite_ingot")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Must be a valid Bukkit Material enum")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>")
        
        // Create clickable link using Adventure API
        val linkText = Component.text("📖 Full material list: ")
            .color(NamedTextColor.YELLOW)
            .append(
                Component.text("[CLICK HERE]")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl("https://jd.papermc.io/paper/1.21.8/org/bukkit/Material.html"))
            )
        player.sendMessage(linkText)
        
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=======================")
    }

    private fun validateRankName(name: String): String? {
        if (name.length !in 1..24) {
            return "Name must be 1-24 characters (current: ${name.length})"
        }
        if (!name.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
            return "Name can only contain letters, numbers, and spaces"
        }
        // TODO: Check if name is unique in guild
        return null
    }

    private fun validateMaterial(materialName: String): Material? {
        return try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "name" -> {
                val error = validateRankName(input)
                if (error != null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Invalid name: $error")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    // TODO: Update rank name in database
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Rank name updated to: '$input'")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Changes will be saved when you apply them.")
                    inputMode = ""
                }
            }
            "icon" -> {
                val material = validateMaterial(input)
                if (material == null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Invalid material: '$input'")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Examples: diamond, gold_ingot, emerald_block")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    selectedIcon = material
                    // TODO: Update rank icon in database
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Rank icon updated to: ${material.name}")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Changes will be saved when you apply them.")
                    inputMode = ""
                }
            }
        }

        // Reopen the menu
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    override fun onCancel(player: Player) {
        inputMode = ""
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Input cancelled.")

        // Reopen the menu
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }
}

