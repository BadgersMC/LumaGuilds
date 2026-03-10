package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

class RankCreationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                      private var guild: Guild): Menu, KoinComponent, ChatInputHandler {

    private val rankService: RankService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    
    // Creation state
    private var rankName: String = ""
    private var rankPriority: Int = 100 // Default low priority
    private var selectedPermissions: MutableSet<RankPermission> = mutableSetOf()
    private var rankIcon: Material = Material.AIR // Default icon
    private var inputMode: String = "" // "name" or "icon"

    override fun open() {
        // Security check: Only players with MANAGE_RANKS permission can create ranks
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)
        if (!hasPermission) {
            player.sendMessage("§c❌ You don't have permission to create ranks!")
            player.sendMessage("§7Required permission: §fMANAGE_RANKS")
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(6, "§6Create New Rank - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 0: Basic rank setup
        addBasicSetupSection(pane)

        // Row 1: Quick permission templates
        addPermissionTemplates(pane)

        // Row 2-3: Permission categories
        addPermissionCategories(pane)

        // Row 4: Preview
        addPreviewSection(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addBasicSetupSection(pane: StaticPane) {
        // Rank name input
        val nameItem = ItemStack.of(Material.NAME_TAG)
            .name("§6📝 Rank Name")
            .lore("§7Current: ${if (rankName.isNotEmpty()) "§f$rankName" else "§cNot set"}")
            .lore("§7")
            .lore("§7Requirements:")
            .lore("§7• 1-24 characters")
            .lore("§7• No special characters")
            .lore("§7• Must be unique")
            .lore("§7")

        if (inputMode == "name") {
            nameItem.name("§e⏳ WAITING FOR NAME INPUT...")
                .lore("§7Type the rank name in chat")
                .lore("§7Or click cancel to stop")
        } else {
            nameItem.lore("§eClick to set rank name")
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage("§eAlready waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (rankIcon == Material.AIR) Material.DIAMOND_SWORD else rankIcon
        val iconItem = ItemStack.of(displayIcon)
            .name("§6🎨 Rank Icon")
            .lore("§7Current: §f${if (rankIcon == Material.AIR) "Not set" else rankIcon.name}")
            .lore("§7")
            .lore("§7Examples:")
            .lore("§7• diamond (Diamond)")
            .lore("§7• gold_ingot (Gold Ingot)")
            .lore("§7• diamond_sword (Diamond Sword)")
            .lore("§7• emerald_block (Emerald Block)")
            .lore("§7")
            .lore("§e📖 Clickable link in chat")

        if (inputMode == "icon") {
            iconItem.name("§e⏳ WAITING FOR ICON INPUT...")
                .lore("§7Type the material name in chat")
                .lore("§7Examples: diamond, gold_ingot, etc.")
                .lore("§7Or click cancel to stop")
        } else {
            iconItem.lore("§eClick to set rank icon")
        }

        val iconGuiItem = GuiItem(iconItem) {
            if (inputMode != "icon") {
                startIconInput()
            } else {
                player.sendMessage("§eAlready waiting for icon input. Type the material name or click cancel.")
            }
        }
        pane.addItem(iconGuiItem, 3, 0)

        // Permission count
        val countItem = ItemStack.of(Material.BOOK)
            .name("§6📊 Selected Permissions")
            .lore("§7Count: §f${selectedPermissions.size}")
            .lore("§7")
            .lore("§7Select permissions below")

        pane.addItem(GuiItem(countItem), 7, 0)
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

            val templateItem = ItemStack.of(
                when (templateName) {
                    "Banker" -> Material.GOLD_INGOT
                    "Envoy" -> Material.WRITABLE_BOOK
                    "Builder" -> Material.BRICKS
                    "Moderator" -> Material.BELL
                    else -> Material.PAPER
                }
            )
                .name("§a🎯 $templateName Template")
                .lore("§7Quick setup for $templateName role")
                .lore("§7")
                .lore("§7Includes permissions:")

            permissions.forEach { permission ->
                val displayName = permission.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                templateItem.lore("§7• §a$displayName")
            }

            templateItem.lore("§7")
            templateItem.lore("§eClick to apply template")

            val templateGuiItem = GuiItem(templateItem) {
                selectedPermissions.clear()
                selectedPermissions.addAll(permissions)
                if (rankName.isEmpty()) {
                    rankName = templateName
                }
                player.sendMessage("§a✅ Applied $templateName template!")
                player.sendMessage("§7Selected ${permissions.size} permissions.")
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

            val categoryItem = ItemStack.of(
                when (categoryName) {
                    "Guild Management" -> Material.GOLDEN_SWORD
                    "Banking" -> Material.GOLD_INGOT
                    "Diplomacy" -> Material.WRITABLE_BOOK
                    "Claims" -> Material.GRASS_BLOCK
                    else -> Material.PAPER
                }
            ).name("§6🔧 $categoryName")
                .lore("§7Permissions: §f$enabledCount§7/§f${permissions.size}")
                .lore("§7")

            if (hasAnyPermission) {
                categoryItem.lore("§a✓ Some permissions selected")
            } else {
                categoryItem.lore("§c✗ No permissions selected")
            }

            categoryItem.lore("§7")
            categoryItem.lore("§eClick to manage permissions")

            val categoryGuiItem = GuiItem(categoryItem) {
                openPermissionCategorySelection(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    private fun addPreviewSection(pane: StaticPane) {
        val previewItem = ItemStack.of(rankIcon)
            .name("§6🔍 Rank Preview")
            .lore("§7Name: ${if (rankName.isNotEmpty()) "§f$rankName" else "§cNot set"}")
            .lore("§7Icon: §f${rankIcon.name}")
            .lore("§7Priority: §f$rankPriority")
            .lore("§7Permissions: §f${selectedPermissions.size}")
            .lore("§7")

        if (selectedPermissions.isNotEmpty()) {
            previewItem.lore("§e⚙ Selected Permissions:")
            val grouped = groupPermissionsByCategory(selectedPermissions)
            grouped.forEach { (category, perms) ->
                if (perms.isNotEmpty()) {
                    previewItem.lore("§7▶ §f$category: §a${perms.size}")
                }
            }
        } else {
            previewItem.lore("§c❌ No permissions selected")
        }

        pane.addItem(GuiItem(previewItem), 4, 4)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create rank
        val canCreate = rankName.isNotEmpty() && selectedPermissions.isNotEmpty()
        val createItem = ItemStack.of(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(if (canCreate) "§a✅ Create Rank" else "§c❌ Cannot Create")
            .lore("§7Create the new rank")

        if (canCreate) {
            createItem.lore("§7")
                .lore("§aReady to create!")
                .lore("§7Click to confirm")
        } else {
            createItem.lore("§7")
            if (rankName.isEmpty()) createItem.lore("§c• Missing rank name")
            if (selectedPermissions.isEmpty()) createItem.lore("§c• No permissions selected")
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
                        rankService.updateRank(rankWithIcon, player.uniqueId)
                    }

                    player.sendMessage("§a✅ Created rank '$rankName' successfully!")
                    player.sendMessage("§7Rank has ${selectedPermissions.size} permissions.")
                    if (iconString != null) {
                        player.sendMessage("§7Rank icon: §f$iconString")
                    }
                } else {
                    player.sendMessage("§c❌ Failed to create rank!")
                }

                menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage("§c❌ Cannot create rank - missing requirements!")
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack.of(Material.BARRIER)
            .name("§c🗑 Clear All")
            .lore("§7Reset all selections")
            .lore("§7")
            .lore("§cClick to clear")

        val clearGuiItem = GuiItem(clearItem) {
            rankName = ""
            rankIcon = Material.AIR
            selectedPermissions.clear()
            inputMode = ""
            player.sendMessage("§e🗑 Cleared all selections!")
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Cancel
        val cancelItem = ItemStack.of(Material.ARROW)
            .name("§7❌ Cancel")
            .lore("§7Return without creating")

        val cancelGuiItem = GuiItem(cancelItem) {
            player.sendMessage("§e⚠ Rank creation cancelled.")
            menuNavigator.openMenu(GuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, 7, 5)
    }

    private fun openPermissionCategorySelection(categoryName: String, permissions: List<RankPermission>) {
        // Create a simplified version of PermissionCategoryMenu for creation
        player.sendMessage("§e🔧 $categoryName permission selection coming soon!")
        player.sendMessage("§7You'll be able to select individual permissions.")
    }

    private fun groupPermissionsByCategory(permissions: Set<RankPermission>): Map<String, List<RankPermission>> {
        return permissions.groupBy { permission ->
            when (permission) {
                RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_DESCRIPTION, RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_MODE, RankPermission.MANAGE_GUILD_SETTINGS -> "Guild Management"
                
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_PARTIES,
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES -> "Diplomacy"
                
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS, RankPermission.EXPORT_BANK_DATA,
                RankPermission.MANAGE_BANK_SETTINGS, RankPermission.PLACE_VAULT,
                RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT,
                RankPermission.WITHDRAW_FROM_VAULT, RankPermission.MANAGE_VAULT,
                RankPermission.BREAK_VAULT, RankPermission.ACCESS_SHOP_CHESTS,
                RankPermission.EDIT_SHOP_STOCK, RankPermission.MODIFY_SHOP_PRICES -> "Banking"
                
                RankPermission.SEND_ANNOUNCEMENTS, RankPermission.SEND_PINGS,
                RankPermission.MODERATE_CHAT -> "Communication"
                
                RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS -> "Claims"
                
                RankPermission.ACCESS_ADMIN_COMMANDS, RankPermission.BYPASS_RESTRICTIONS,
                RankPermission.VIEW_AUDIT_LOGS, RankPermission.MANAGE_INTEGRATIONS -> "Administrative"
            }
        }
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage("§6=== RANK NAME INPUT ===")
        player.sendMessage("§7Type the rank name in chat.")
        player.sendMessage("§7Examples:")
        player.sendMessage("§7  Banker, Envoy, Officer, Builder")
        player.sendMessage("§7  Moderator, Treasurer, Ambassador")
        player.sendMessage("§7Requirements:")
        player.sendMessage("§7• 1-24 characters")
        player.sendMessage("§7• No special characters")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6========================")
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage("§6=== RANK ICON INPUT ===")
        player.sendMessage("§7Type the material name in chat.")
        player.sendMessage("§7Examples:")
        player.sendMessage("§7  diamond, gold_ingot, emerald")
        player.sendMessage("§7  diamond_sword, golden_apple")
        player.sendMessage("§7  iron_block, netherite_ingot")
        player.sendMessage("§7")
        player.sendMessage("§7Must be a valid Bukkit Material enum")
        player.sendMessage("§7")
        
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
        
        player.sendMessage("§7")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6========================")
    }

    private fun validateRankName(name: String): String? {
        if (name.length !in 1..24) {
            return "Name must be 1-24 characters (current: ${name.length})"
        }
        if (!name.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
            return "Name can only contain letters, numbers, and spaces"
        }
        // Check if name is unique in guild
        val existingRank = rankService.getRankByName(guild.id, name)
        if (existingRank != null) {
            return "A rank with this name already exists"
        }
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
                    player.sendMessage("§c❌ Invalid name: $error")
                    player.sendMessage("§7Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    rankName = input
                    inputMode = ""
                    player.sendMessage("§a✅ Rank name set to: '$input'")
                }
            }
            "icon" -> {
                val material = validateMaterial(input)
                if (material == null) {
                    player.sendMessage("§c❌ Invalid material: '$input'")
                    player.sendMessage("§7Examples: diamond, gold_ingot, emerald_block")
                    player.sendMessage("§7Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    rankIcon = material
                    inputMode = ""
                    player.sendMessage("§a✅ Rank icon set to: ${material.name}")
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
        player.sendMessage("§7Input cancelled.")

        // Reopen the menu
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

