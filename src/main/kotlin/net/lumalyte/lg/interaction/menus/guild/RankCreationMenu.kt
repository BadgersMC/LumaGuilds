package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.utils.MenuUtils
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
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
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Permission categories for organizing permissions in the UI
 */
enum class PermissionCategory(val displayName: String, val icon: Material, val description: String) {
    ALL("All Permissions", Material.CHEST, "View all available permissions"),
    GUILD_MANAGEMENT("Guild Management", Material.DIAMOND_SWORD, "Guild creation, settings, and administration"),
    MEMBER_MANAGEMENT("Member Management", Material.PLAYER_HEAD, "Member invitations, ranks, and moderation"),
    BANKING("Banking & Economy", Material.GOLD_INGOT, "Bank transactions and financial management"),
    DIPLOMACY("Diplomacy & Relations", Material.SHIELD, "Alliances, wars, and diplomatic actions"),
    COMMUNICATION("Communication", Material.BOOK, "Announcements, messaging, and chat"),
    CLAIMS("Claims & Territory", Material.GRASS_BLOCK, "Claim management and permissions"),
    SECURITY("Security & Audit", Material.IRON_DOOR, "Security settings and audit logs")
}

/**
 * Predefined permission presets for common roles
 */
data class PermissionPreset(
    val name: String,
    val description: String,
    val permissions: Set<RankPermission>,
    val icon: Material
) {
    companion object {
        val PRESETS = listOf(
            PermissionPreset(
                "Member",
                "Basic member permissions",
                setOf(
                    RankPermission.DEPOSIT_TO_BANK,
                    RankPermission.WITHDRAW_FROM_BANK
                ),
                Material.WHITE_WOOL
            ),
            PermissionPreset(
                "Moderator",
                "Moderation and member management",
                setOf(
                    RankPermission.MANAGE_MEMBERS,
                    RankPermission.MODERATE_CHAT,
                    RankPermission.SEND_ANNOUNCEMENTS
                ),
                Material.YELLOW_WOOL
            ),
            PermissionPreset(
                "Officer",
                "Guild leadership and management",
                setOf(
                    RankPermission.MANAGE_MEMBERS,
                    RankPermission.MANAGE_RANKS,
                    RankPermission.MANAGE_GUILD_SETTINGS,
                    RankPermission.SEND_ANNOUNCEMENTS,
                    RankPermission.MODERATE_CHAT
                ),
                Material.ORANGE_WOOL
            ),
            PermissionPreset(
                "Banker",
                "Financial management and security",
                setOf(
                    RankPermission.DEPOSIT_TO_BANK,
                    RankPermission.WITHDRAW_FROM_BANK,
                    RankPermission.VIEW_BANK_TRANSACTIONS,
                    RankPermission.EXPORT_BANK_DATA,
                    RankPermission.MANAGE_BANK_SECURITY,
                    RankPermission.MANAGE_BUDGETS
                ),
                Material.GOLD_INGOT
            ),
            PermissionPreset(
                "Diplomat",
                "Diplomacy and international relations",
                setOf(
                    RankPermission.MANAGE_RELATIONS,
                    RankPermission.DECLARE_WAR,
                    RankPermission.ACCEPT_ALLIANCES
                ),
                Material.SHIELD
            ),
            PermissionPreset(
                "Admin",
                "Full administrative access",
                RankPermission.values().toSet(),
                Material.DIAMOND
            )
        )
    }
}

class RankCreationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                      private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

    private val rankService: RankService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    
    // Creation state
    private var rankName: String = ""
    private var rankPriority: Int = 100 // Default low priority
    private var selectedPermissions: MutableSet<RankPermission> = mutableSetOf()
    private var rankIcon: Material = Material.AIR // Default icon (TODO: Load from rank entity when icon field is added)
    private var inputMode: String = "" // "name" or "icon"
    private var selectedCategory: PermissionCategory = PermissionCategory.ALL
    private var presetMode: Boolean = false

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Create New Rank - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Basic rank setup
        addBasicSetupSection(pane)

        // Row 1: Permission presets
        addPermissionPresets(pane)

        // Row 2-3: Permission categories
        addPermissionCategories(pane)

        // Row 4: Preview and status
        addPreviewSection(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addBasicSetupSection(pane: StaticPane) {
        // Rank name input
        val nameItem = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<gold>📝 Rank Name")
            .lore("<gray>Current: ${if (rankName.isNotEmpty()) "§f$rankName" else "<red>Not set"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Requirements:")
            .addAdventureLore(player, messageService, "<gray>• 1-24 characters")
            .addAdventureLore(player, messageService, "<gray>• No special characters")
            .addAdventureLore(player, messageService, "<gray>• Must be unique")
            .addAdventureLore(player, messageService, "<gray>")

        if (inputMode == "name") {
            nameItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR NAME INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type the rank name in chat")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            nameItem.addAdventureLore(player, messageService, "<yellow>Click to set rank name")
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Already waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (rankIcon == Material.AIR) Material.DIAMOND_SWORD else rankIcon
        val iconItem = ItemStack(displayIcon)
            .setAdventureName(player, messageService, "<gold>🎨 Rank Icon")
            .lore("<gray>Current: <white>${if (rankIcon == Material.AIR) "Not set" else rankIcon.name}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Examples:")
            .addAdventureLore(player, messageService, "<gray>• diamond (Diamond)")
            .addAdventureLore(player, messageService, "<gray>• gold_ingot (Gold Ingot)")
            .addAdventureLore(player, messageService, "<gray>• diamond_sword (Diamond Sword)")
            .addAdventureLore(player, messageService, "<gray>• emerald_block (Emerald Block)")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>📖 Clickable link in chat")

        if (inputMode == "icon") {
            iconItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR ICON INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type the material name in chat")
                .addAdventureLore(player, messageService, "<gray>Examples: diamond, gold_ingot, etc.")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            iconItem.addAdventureLore(player, messageService, "<yellow>Click to set rank icon")
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
        val countItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📊 Selected Permissions")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${selectedPermissions.size}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Select permissions below")

        pane.addItem(GuiItem(countItem), 7, 0)
    }

    /**
     * Add permission presets section
     */
    private fun addPermissionPresets(pane: StaticPane) {
        // Presets button
        val presetsItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gold>🎭 Permission Presets")
            .addAdventureLore(player, messageService, "<gray>Quick role templates")
            .lore("<gray>Current: ${if (presetMode) "§aApplied" else "<gray>Not applied"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Presets available:")
            .addAdventureLore(player, messageService, "<gray>• Member, Moderator, Officer")
            .addAdventureLore(player, messageService, "<gray>• Banker, Diplomat, Admin")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to browse presets")

        val presetsGuiItem = GuiItem(presetsItem) { event ->
            event.isCancelled = true
            openPresetMenu()
        }
        pane.addItem(presetsGuiItem, 0, 1)

        // Categories button
        val categoriesItem = ItemStack(Material.CHEST)
            .setAdventureName(player, messageService, "<gold>📂 Permission Categories")
            .addAdventureLore(player, messageService, "<gray>Organized by function")
            .addAdventureLore(player, messageService, "<gray>Current: ${selectedCategory.displayName}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Categories:")
            .addAdventureLore(player, messageService, "<gray>• Guild Management")
            .addAdventureLore(player, messageService, "<gray>• Member Management")
            .addAdventureLore(player, messageService, "<gray>• Banking & Security")
            .addAdventureLore(player, messageService, "<gray>• Diplomacy & Claims")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to browse categories")

        val categoriesGuiItem = GuiItem(categoriesItem) { event ->
            event.isCancelled = true
            openCategoryMenu()
        }
        pane.addItem(categoriesGuiItem, 1, 1)
    }

    private fun addPermissionTemplates(pane: StaticPane) {
        // Template presets for common roles
        val baseTemplates = mutableMapOf(
            "Banker" to setOf(
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS, RankPermission.EXPORT_BANK_DATA
            ),
            "Envoy" to setOf(
                RankPermission.MANAGE_RELATIONS, RankPermission.ACCEPT_ALLIANCES,
                RankPermission.MANAGE_PARTIES, RankPermission.SEND_PARTY_REQUESTS,
                RankPermission.ACCEPT_PARTY_INVITES
            ),
            "Moderator" to setOf(
                RankPermission.MODERATE_CHAT, RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.VIEW_AUDIT_LOGS
            )
        )

        // Only add Builder template if claims are enabled
        if (configService.loadConfig().claimsEnabled) {
            baseTemplates["Builder"] = setOf(
                RankPermission.MANAGE_CLAIMS, RankPermission.CREATE_CLAIMS,
                RankPermission.MANAGE_FLAGS
            )
        }

        val templates = baseTemplates

        templates.entries.forEachIndexed { index, (templateName, permissions) ->
            val col = index * 2 + 1

            val templateItem = ItemStack(
                when (templateName) {
                    "Banker" -> Material.GOLD_INGOT
                    "Envoy" -> Material.WRITABLE_BOOK
                    "Builder" -> Material.BRICKS
                    "Moderator" -> Material.BELL
                    else -> Material.PAPER
                }
            )
                .setAdventureName(player, messageService, "<green>🎯 $templateName Template")
                .addAdventureLore(player, messageService, "<gray>Quick setup for $templateName role")
                .addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<gray>Includes permissions:")

            permissions.forEach { permission ->
                val displayName = permission.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                templateItem.addAdventureLore(player, messageService, "<gray>• <green>$displayName")
            }

            templateItem.addAdventureLore(player, messageService, "<gray>")
            templateItem.addAdventureLore(player, messageService, "<yellow>Click to apply template")

            val templateGuiItem = GuiItem(templateItem) {
                selectedPermissions.clear()
                selectedPermissions.addAll(permissions)
                if (rankName.isEmpty()) {
                    rankName = templateName
                }
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Applied $templateName template!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Selected ${permissions.size} permissions.")
                open() // Refresh menu
            }
            pane.addItem(templateGuiItem, col, 1)
        }
    }

    private fun addPermissionCategories(pane: StaticPane) {
        val baseCategories = mutableMapOf(
            "Guild Management" to listOf(
                RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_HOME, RankPermission.MANAGE_MODE,
                RankPermission.MANAGE_GUILD_SETTINGS
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
            )
        )

        // Only add Claims category if claims are enabled
        if (configService.loadConfig().claimsEnabled) {
            baseCategories["Claims"] = listOf(
                RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS
            )
        }

        val categories = baseCategories

        categories.entries.forEachIndexed { index, (categoryName, permissions) ->
            val row = 2
            val col = index * 3 + 1

            val hasAnyPermission = permissions.any { selectedPermissions.contains(it) }
            val enabledCount = permissions.count { selectedPermissions.contains(it) }

            val categoryItem = ItemStack(
                when (categoryName) {
                    "Guild Management" -> Material.GOLDEN_SWORD
                    "Banking" -> Material.GOLD_INGOT
                    "Diplomacy" -> Material.WRITABLE_BOOK
                    "Claims" -> Material.GRASS_BLOCK
                    else -> Material.PAPER
                }
            ).setAdventureName(player, messageService, "<gold>🔧 $categoryName")
                .addAdventureLore(player, messageService, "<gray>Permissions: <white>$enabledCount<gray>/<white>${permissions.size}")
                .addAdventureLore(player, messageService, "<gray>")

            if (hasAnyPermission) {
                categoryItem.addAdventureLore(player, messageService, "<green>✓ Some permissions selected")
            } else {
                categoryItem.addAdventureLore(player, messageService, "<red>✗ No permissions selected")
            }

            categoryItem.addAdventureLore(player, messageService, "<gray>")
            categoryItem.addAdventureLore(player, messageService, "<yellow>Click to manage permissions")

            val categoryGuiItem = GuiItem(categoryItem) {
                openPermissionCategorySelection(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    private fun addPreviewSection(pane: StaticPane) {
        val previewItem = ItemStack(rankIcon)
            .setAdventureName(player, messageService, "<gold>🔍 Rank Preview")
            .lore("<gray>Name: ${if (rankName.isNotEmpty()) "§f$rankName" else "<red>Not set"}")
            .addAdventureLore(player, messageService, "<gray>Icon: <white>${rankIcon.name}")
            .addAdventureLore(player, messageService, "<gray>Priority: <white>$rankPriority")
            .addAdventureLore(player, messageService, "<gray>Permissions: <white>${selectedPermissions.size}")
            .addAdventureLore(player, messageService, "<gray>")
            .lore("<gray>Mode: ${if (presetMode) "§aPreset Applied" else "<gray>Manual Selection"}")
            .addAdventureLore(player, messageService, "<gray>Category: <white>${selectedCategory.displayName}")
            .addAdventureLore(player, messageService, "<gray>")

        if (selectedPermissions.isNotEmpty()) {
            previewItem.addAdventureLore(player, messageService, "<yellow>⚙️ Selected Permissions:")
            val grouped = groupPermissionsByCategory(selectedPermissions)
            grouped.forEach { (category, perms) ->
                if (perms.isNotEmpty()) {
                    previewItem.addAdventureLore(player, messageService, "<gray>▶ <white>$category: <green>${perms.size}")
                }
            }
        } else {
            previewItem.addAdventureLore(player, messageService, "<red>❌ No permissions selected")
        }

        pane.addItem(GuiItem(previewItem), 4, 4)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create rank
        val canCreate = rankName.isNotEmpty() && selectedPermissions.isNotEmpty()
        val createItem = ItemStack(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(if (canCreate) "<green>✅ Create Rank" else "<red>❌ Cannot Create")
            .addAdventureLore(player, messageService, "<gray>Create the new rank")

        if (canCreate) {
            createItem.addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<green>Ready to create!")
                .addAdventureLore(player, messageService, "<gray>Click to confirm")
        } else {
            createItem.addAdventureLore(player, messageService, "<gray>")
            if (rankName.isEmpty()) createItem.addAdventureLore(player, messageService, "<red>• Missing rank name")
            if (selectedPermissions.isEmpty()) createItem.addAdventureLore(player, messageService, "<red>• No permissions selected")
        }

        val createGuiItem = GuiItem(createItem) {
            if (canCreate) {
                // Create the rank with the selected icon
                val iconString = if (rankIcon == Material.AIR) null else rankIcon.name
                val createdRank = rankService.addRank(guild.id, rankName, selectedPermissions, player.uniqueId)

                if (createdRank != null) {
                    // Update the rank with the icon if one was selected
                    if (iconString != null) {
                        val rankWithIcon = createdRank.copy(icon = iconString)
                        rankService.updateRank(rankWithIcon)
                    }

                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Created rank '$rankName' successfully!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Rank has ${selectedPermissions.size} permissions.")
                    if (iconString != null) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Rank icon: <white>$iconString")
                    }
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to create rank!")
                }

                menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Cannot create rank - missing requirements!")
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>🗑️ Clear All")
            .addAdventureLore(player, messageService, "<gray>Reset all selections")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Click to clear")

        val clearGuiItem = GuiItem(clearItem) {
            rankName = ""
            rankIcon = Material.AIR
            selectedPermissions.clear()
            inputMode = ""
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🗑️ Cleared all selections!")
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Cancel
        val cancelItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<gray>❌ Cancel")
            .addAdventureLore(player, messageService, "<gray>Return without creating")

        val cancelGuiItem = GuiItem(cancelItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>⚠️ Rank creation cancelled.")
            menuNavigator.openMenu(GuildRankManagementMenu(menuNavigator, player, guild, messageService))
        }
        pane.addItem(cancelGuiItem, 7, 5)
    }

    private fun openPermissionCategorySelection(categoryName: String, permissions: List<RankPermission>) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue><blue>Select $categoryName Permissions"))

        val mainPane = StaticPane(0, 0, 9, 5)
        val navigationPane = StaticPane(0, 5, 9, 1)

        // Add permission items
        var slot = 0
        permissions.forEach { permission ->
            if (slot >= 45) return@forEach // Prevent overflow

            val item = when (permission) {
                RankPermission.MANAGE_RANKS -> ItemStack(Material.COMMAND_BLOCK).setAdventureName(player, messageService, "<gold>Manage Ranks").addAdventureLore(player, messageService, "<gray>Allow creating, editing, and deleting ranks")
                RankPermission.MANAGE_MEMBERS -> ItemStack(Material.PLAYER_HEAD).setAdventureName(player, messageService, "<gold>Manage Members").addAdventureLore(player, messageService, "<gray>Allow inviting, kicking, and managing members")
                RankPermission.MANAGE_BANNER -> ItemStack(Material.WHITE_BANNER).setAdventureName(player, messageService, "<gold>Manage Banner").addAdventureLore(player, messageService, "<gray>Allow changing guild banner")
                RankPermission.MANAGE_EMOJI -> ItemStack(Material.FIREWORK_STAR).setAdventureName(player, messageService, "<gold>Manage Emoji").addAdventureLore(player, messageService, "<gray>Allow changing guild emoji")
                RankPermission.MANAGE_DESCRIPTION -> ItemStack(Material.BOOK).setAdventureName(player, messageService, "<gold>Manage Description").addAdventureLore(player, messageService, "<gray>Allow editing guild description")
                RankPermission.MANAGE_GUILD_NAME -> ItemStack(Material.NAME_TAG).setAdventureName(player, messageService, "<gold>Manage Guild Name").addAdventureLore(player, messageService, "<gray>Allow renaming the guild")
                RankPermission.MANAGE_HOME -> ItemStack(Material.COMPASS).setAdventureName(player, messageService, "<gold>Manage Home").addAdventureLore(player, messageService, "<gray>Allow setting guild home location")
                RankPermission.MANAGE_MODE -> ItemStack(Material.LEVER).setAdventureName(player, messageService, "<gold>Manage Mode").addAdventureLore(player, messageService, "<gray>Allow changing guild mode (Peaceful/War)")
                RankPermission.MANAGE_GUILD_SETTINGS -> ItemStack(Material.REDSTONE).setAdventureName(player, messageService, "<gold>Manage Guild Settings").addAdventureLore(player, messageService, "<gray>Allow changing guild settings")

                // Banking permissions
                RankPermission.DEPOSIT_TO_BANK -> ItemStack(Material.GOLD_INGOT).setAdventureName(player, messageService, "<gold>Deposit to Bank").addAdventureLore(player, messageService, "<gray>Allow depositing money to guild bank")
                RankPermission.WITHDRAW_FROM_BANK -> ItemStack(Material.GOLD_BLOCK).setAdventureName(player, messageService, "<gold>Withdraw from Bank").addAdventureLore(player, messageService, "<gray>Allow withdrawing money from guild bank")
                RankPermission.VIEW_BANK_TRANSACTIONS -> ItemStack(Material.BOOK).setAdventureName(player, messageService, "<gold>View Bank Transactions").addAdventureLore(player, messageService, "<gray>Allow viewing bank transaction history")
                RankPermission.EXPORT_BANK_DATA -> ItemStack(Material.WRITABLE_BOOK).setAdventureName(player, messageService, "<gold>Export Bank Data").addAdventureLore(player, messageService, "<gray>Allow exporting bank transaction data")
                RankPermission.MANAGE_BANK_SECURITY -> ItemStack(Material.IRON_DOOR).setAdventureName(player, messageService, "<gold>Manage Bank Security").addAdventureLore(player, messageService, "<gray>Allow managing bank security settings")
                RankPermission.ACTIVATE_EMERGENCY_FREEZE -> ItemStack(Material.PACKED_ICE).setAdventureName(player, messageService, "<gold>Activate Emergency Freeze").addAdventureLore(player, messageService, "<gray>Allow activating emergency bank freeze")
                RankPermission.DEACTIVATE_EMERGENCY_FREEZE -> ItemStack(Material.FIRE_CHARGE).setAdventureName(player, messageService, "<gold>Deactivate Emergency Freeze").addAdventureLore(player, messageService, "<gray>Allow deactivating emergency bank freeze")
                RankPermission.VIEW_SECURITY_AUDITS -> ItemStack(Material.SPYGLASS).setAdventureName(player, messageService, "<gold>View Security Audits").addAdventureLore(player, messageService, "<gray>Allow viewing security audit logs")

                RankPermission.MANAGE_RELATIONS -> ItemStack(Material.SHIELD).setAdventureName(player, messageService, "<gold>Manage Relations").addAdventureLore(player, messageService, "<gray>Allow managing diplomatic relations")
                RankPermission.DECLARE_WAR -> ItemStack(Material.IRON_SWORD).setAdventureName(player, messageService, "<gold>Declare War").addAdventureLore(player, messageService, "<gray>Allow declaring wars on other guilds")
                RankPermission.ACCEPT_ALLIANCES -> ItemStack(Material.GREEN_BANNER).setAdventureName(player, messageService, "<gold>Accept Alliances").addAdventureLore(player, messageService, "<gray>Allow accepting alliance requests")
                RankPermission.MANAGE_PARTIES -> ItemStack(Material.CHEST).setAdventureName(player, messageService, "<gold>Manage Parties").addAdventureLore(player, messageService, "<gray>Allow managing guild parties")
                RankPermission.SEND_PARTY_REQUESTS -> ItemStack(Material.PAPER).setAdventureName(player, messageService, "<gold>Send Party Requests").addAdventureLore(player, messageService, "<gray>Allow sending party invitations")
                RankPermission.ACCEPT_PARTY_INVITES -> ItemStack(Material.WRITTEN_BOOK).setAdventureName(player, messageService, "<gold>Accept Party Invites").addAdventureLore(player, messageService, "<gray>Allow accepting party invitations")

                RankPermission.DEPOSIT_MONEY -> ItemStack(Material.GOLD_INGOT).setAdventureName(player, messageService, "<gold>Deposit Money").addAdventureLore(player, messageService, "<gray>Allow depositing money into guild bank")
                RankPermission.WITHDRAW_MONEY -> ItemStack(Material.GOLD_NUGGET).setAdventureName(player, messageService, "<gold>Withdraw Money").addAdventureLore(player, messageService, "<gray>Allow withdrawing money from guild bank")
                RankPermission.MANAGE_BANK_SETTINGS -> ItemStack(Material.IRON_INGOT).setAdventureName(player, messageService, "<gold>Manage Bank Settings").addAdventureLore(player, messageService, "<gray>Allow managing bank security and settings")
                RankPermission.VIEW_BANK_HISTORY -> ItemStack(Material.BOOK).setAdventureName(player, messageService, "<gold>View Bank History").addAdventureLore(player, messageService, "<gray>Allow viewing bank transaction history")

                RankPermission.CLAIM_LAND -> ItemStack(Material.GRASS_BLOCK).setAdventureName(player, messageService, "<gold>Claim Land").addAdventureLore(player, messageService, "<gray>Allow claiming land for the guild")
                RankPermission.MANAGE_CLAIMS -> ItemStack(Material.DIAMOND_PICKAXE).setAdventureName(player, messageService, "<gold>Manage Claims").addAdventureLore(player, messageService, "<gray>Allow managing guild claims and partitions")
                RankPermission.UNCLAIM_LAND -> ItemStack(Material.STONE).setAdventureName(player, messageService, "<gold>Unclaim Land").addAdventureLore(player, messageService, "<gray>Allow unclaiming guild land")

                RankPermission.USE_CHAT -> ItemStack(Material.OAK_SIGN).setAdventureName(player, messageService, "<gold>Use Chat").addAdventureLore(player, messageService, "<gray>Allow using guild chat")
                RankPermission.MANAGE_CHAT_SETTINGS -> ItemStack(Material.LEVER).setAdventureName(player, messageService, "<gold>Manage Chat Settings").addAdventureLore(player, messageService, "<gray>Allow managing chat settings")
                RankPermission.SEND_ANNOUNCEMENTS -> ItemStack(Material.BELL).setAdventureName(player, messageService, "<gold>Send Announcements").addAdventureLore(player, messageService, "<gray>Allow sending guild announcements")

                RankPermission.MANAGE_RANKS -> ItemStack(Material.PAPER).setAdventureName(player, messageService, "<gold>Manage Ranks").addAdventureLore(player, messageService, "<gray>Allow managing member ranks")
                RankPermission.VIEW_AUDIT_LOGS -> ItemStack(Material.BOOK).setAdventureName(player, messageService, "<gold>View Audit Logs").addAdventureLore(player, messageService, "<gray>Allow viewing audit logs")
                RankPermission.MANAGE_PERMISSIONS -> ItemStack(Material.COMMAND_BLOCK).setAdventureName(player, messageService, "<gold>Manage Permissions").addAdventureLore(player, messageService, "<gray>Allow managing permissions")

                RankPermission.OVERRIDE_PROTECTION -> ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<gold>Override Protection").addAdventureLore(player, messageService, "<gray>Allow overriding claim protection")
                RankPermission.BYPASS_COOLDOWNS -> ItemStack(Material.CLOCK).setAdventureName(player, messageService, "<gold>Bypass Cooldowns").addAdventureLore(player, messageService, "<gray>Allow bypassing cooldowns")
                RankPermission.MANAGE_SECURITY -> ItemStack(Material.IRON_DOOR).setAdventureName(player, messageService, "<gold>Manage Security").addAdventureLore(player, messageService, "<gray>Allow managing security settings")
                
                // Additional missing permissions
                RankPermission.MANAGE_BUDGETS -> ItemStack(Material.EMERALD).setAdventureName(player, messageService, "<gold>Manage Budgets").addAdventureLore(player, messageService, "<gray>Allow managing guild budgets")
                RankPermission.SEND_PINGS -> ItemStack(Material.BELL).setAdventureName(player, messageService, "<gold>Send Pings").addAdventureLore(player, messageService, "<gray>Allow sending notification pings")
                RankPermission.MODERATE_CHAT -> ItemStack(Material.PAPER).setAdventureName(player, messageService, "<gold>Moderate Chat").addAdventureLore(player, messageService, "<gray>Allow moderating chat channels")
                RankPermission.MANAGE_FLAGS -> ItemStack(Material.WHITE_BANNER).setAdventureName(player, messageService, "<gold>Manage Flags").addAdventureLore(player, messageService, "<gray>Allow managing guild flags")
                RankPermission.CREATE_CLAIMS -> ItemStack(Material.GRASS_BLOCK).setAdventureName(player, messageService, "<gold>Create Claims").addAdventureLore(player, messageService, "<gray>Allow creating new claims")
                RankPermission.DELETE_CLAIMS -> ItemStack(Material.STONE).setAdventureName(player, messageService, "<gold>Delete Claims").addAdventureLore(player, messageService, "<gray>Allow deleting existing claims")
                RankPermission.ACCESS_ADMIN_COMMANDS -> ItemStack(Material.COMMAND_BLOCK).setAdventureName(player, messageService, "<gold>Admin Commands").addAdventureLore(player, messageService, "<gray>Allow access to admin commands")
                RankPermission.BYPASS_RESTRICTIONS -> ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<gold>Bypass Restrictions").addAdventureLore(player, messageService, "<gray>Allow bypassing guild restrictions")
                RankPermission.MANAGE_INTEGRATIONS -> ItemStack(Material.REDSTONE).setAdventureName(player, messageService, "<gold>Manage Integrations").addAdventureLore(player, messageService, "<gray>Allow managing external integrations")
                
                // Fallback for any unhandled permissions
                else -> ItemStack(Material.PAPER).name("<gold>${permission.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}").addAdventureLore(player, messageService, "<gray>Permission: ${permission.name}")
            }

            val isSelected = selectedPermissions.contains(permission)
            if (isSelected) {
                item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1)
                item.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
                item.addAdventureLore(player, messageService, "<green>✓ Selected")
            } else {
                item.addAdventureLore(player, messageService, "<gray>Click to select")
            }

            val guiItem = GuiItem(item) { event ->
                event.isCancelled = true
                if (selectedPermissions.contains(permission)) {
                    selectedPermissions.remove(permission)
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Deselected <yellow>${permission.name}")
                } else {
                    selectedPermissions.add(permission)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected <yellow>${permission.name}")
                }
                // Refresh the menu
                openPermissionCategorySelection(categoryName, permissions)
            }

            val x = slot % 9
            val y = slot / 9
            mainPane.addItem(guiItem, x, y)
            slot++
        }

        // Add navigation items
        val backItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<red>Back").addAdventureLore(player, messageService, "<gray>Return to rank creation")
        val backGuiItem = GuiItem(backItem) {
            open() // Return to main creation menu
        }
        navigationPane.addItem(backGuiItem, 0, 0)

        val doneItem = ItemStack(Material.EMERALD_BLOCK).setAdventureName(player, messageService, "<green>Done").addAdventureLore(player, messageService, "<gray>Finish selecting permissions")
        val doneGuiItem = GuiItem(doneItem) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected ${selectedPermissions.size} permissions for this rank")
            open() // Return to main creation menu
        }
        navigationPane.addItem(doneGuiItem, 8, 0)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)
        gui.show(player)
    }

    private fun groupPermissionsByCategory(permissions: Set<RankPermission>): Map<String, List<RankPermission>> {
        return permissions.groupBy { permission ->
            when (permission) {
                RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_DESCRIPTION, RankPermission.MANAGE_GUILD_NAME,
                RankPermission.MANAGE_HOME, RankPermission.MANAGE_MODE,
                RankPermission.MANAGE_GUILD_SETTINGS -> "Guild Management"
                
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_PARTIES,
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES -> "Diplomacy"
                
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS, RankPermission.EXPORT_BANK_DATA,
                RankPermission.MANAGE_BANK_SETTINGS, RankPermission.MANAGE_BANK_SECURITY,
                RankPermission.MANAGE_BUDGETS -> "Banking"
                
                RankPermission.SEND_ANNOUNCEMENTS, RankPermission.SEND_PINGS,
                RankPermission.MODERATE_CHAT -> "Communication"
                
                RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS -> "Claims"
                
                RankPermission.ACCESS_ADMIN_COMMANDS, RankPermission.BYPASS_RESTRICTIONS,
                RankPermission.VIEW_AUDIT_LOGS, RankPermission.MANAGE_INTEGRATIONS,
                RankPermission.ACTIVATE_EMERGENCY_FREEZE, RankPermission.DEACTIVATE_EMERGENCY_FREEZE,
                RankPermission.VIEW_SECURITY_AUDITS -> "Administrative"
                
                RankPermission.DEPOSIT_MONEY, RankPermission.WITHDRAW_MONEY,
                RankPermission.VIEW_BANK_HISTORY -> "Banking Extended"
                
                RankPermission.USE_CHAT, RankPermission.MANAGE_CHAT_SETTINGS -> "Chat"
                
                RankPermission.CLAIM_LAND, RankPermission.UNCLAIM_LAND -> "Land Management"
                
                RankPermission.OVERRIDE_PROTECTION, RankPermission.BYPASS_COOLDOWNS,
                RankPermission.MANAGE_SECURITY -> "Security"
                
                else -> "Other"
            }
        }
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== RANK NAME INPUT ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type the rank name in chat.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Examples:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  Banker, Envoy, Officer, Builder")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>  Moderator, Treasurer, Ambassador")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Requirements:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• 1-24 characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>• No special characters")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>========================")
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== RANK ICON INPUT ===")
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
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>========================")
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
                    rankName = input
                    inputMode = ""
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Rank name set to: '$input'")
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
                    rankIcon = material
                    inputMode = ""
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Rank icon set to: ${material.name}")
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

    override fun passData(data: Any?) {
        if (data is String) {
            when (inputMode) {
                "name" -> {
                    val error = validateRankName(data)
                    if (error != null) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>$error")
                        return
                    }
                    rankName = data
                    inputMode = ""
                }
                "icon" -> {
                    try {
                        rankIcon = Material.valueOf(data.uppercase())
                        inputMode = ""
                    } catch (e: IllegalArgumentException) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Invalid material name! Please try again.")
                    }
                }
            }
            open()
        } else if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val updates = data as Map<String, Any>
            updates.forEach { (key, value) ->
                when (key) {
                    "category" -> selectedCategory = value as PermissionCategory
                    "preset" -> {
                        val preset = value as PermissionPreset
                        selectedPermissions = preset.permissions.toMutableSet()
                        presetMode = true
                    }
                }
            }
            open()
        } else if (data is Guild) {
            guild = data
        }
    }

    /**
     * Get permissions filtered by category
     */
    private fun getPermissionsByCategory(category: PermissionCategory): List<RankPermission> {
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
                RankPermission.MANAGE_INTEGRATIONS,
                RankPermission.MANAGE_BANK_SECURITY,
                RankPermission.MANAGE_BUDGETS
            )
        }
    }

    /**
     * Open category selection menu
     */
    private fun openCategoryMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Select Permission Category"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        var currentRow = 0
        var currentCol = 0

        PermissionCategory.values().forEach { category ->
            val item = MenuUtils.createMenuItem(
                category.icon,
                category.displayName,
                listOf(category.description, "Click to view permissions")
            )
            val guiItem = GuiItem(item) { event ->
                event.isCancelled = true
                menuNavigator.openMenu(RankPermissionSelectionMenu(
                    menuNavigator, player, guild, category, selectedPermissions, messageService
                ))
            }
            pane.addItem(guiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }

        // Back button
        val backItem = MenuUtils.createMenuItem(
            Material.ARROW,
            "Back to Rank Creation",
            listOf("Return to rank creation menu")
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
     * Open preset selection menu
     */
    private fun openPresetMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Select Permission Preset"))
        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        var currentRow = 0
        var currentCol = 0

        PermissionPreset.PRESETS.forEach { preset ->
            val item = MenuUtils.createMenuItem(
                preset.icon,
                preset.name,
                listOf(
                    preset.description,
                    "${preset.permissions.size} permissions",
                    "Click to apply preset"
                )
            )
            val guiItem = GuiItem(item) { event ->
                event.isCancelled = true
                // Apply preset and return to main menu
                selectedPermissions = preset.permissions.toMutableSet()
                presetMode = true
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Applied ${preset.name} preset (${preset.permissions.size} permissions)")
                menuNavigator.openMenu(this)
            }
            pane.addItem(guiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }

        // Back button
        val backItem = MenuUtils.createMenuItem(
            Material.ARROW,
            "Back to Rank Creation",
            listOf("Return to rank creation menu")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 3)

        gui.addPane(pane)
        gui.show(player)
    }
}

