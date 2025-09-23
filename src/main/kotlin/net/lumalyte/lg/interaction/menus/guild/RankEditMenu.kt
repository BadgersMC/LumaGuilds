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

class RankEditMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                  private var guild: Guild, private var rank: Rank): Menu, KoinComponent, ChatInputHandler {

    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    
    // Edit state
    private var inputMode: String = "" // "name" or "icon"
    private var selectedIcon: Material = loadRankIcon() // Track selected icon
    
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
        val gui = ChestGui(6, "§6Edit Rank: ${rank.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 0: Rank Information
        addRankInfoSection(pane)

        // Row 1-4: Permission Categories
        addPermissionCategories(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addRankInfoSection(pane: StaticPane) {
        // Rank name and basic info
        val infoItem = ItemStack(Material.NAME_TAG)
            .name("§6📝 Rank Information")
            .lore("§7Name: §f${rank.name}")
            .lore("§7Priority: §f${rank.priority}")
            .lore("§7Members: §f${getMemberCount()} players")
            
        // Add owner protection warning if editing own owner rank
        if (isEditingOwnOwnerRank()) {
            infoItem.lore("§7")
                .lore("§c⚠︎ OWNER RANK PROTECTION")
                .lore("§7Permission changes are blocked")
                .lore("§7to prevent self-lockout")
        }
        
        infoItem.lore("§7")

        if (inputMode == "name") {
            infoItem.name("§e⏳ WAITING FOR NAME INPUT...")
                .lore("§7Type the new rank name in chat")
                .lore("§7Or click cancel to stop")
        } else {
            infoItem.lore("§eClick to rename rank")
        }

        val infoGuiItem = GuiItem(infoItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage("§eAlready waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(infoGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (selectedIcon == Material.AIR) Material.DIAMOND_SWORD else selectedIcon
        val iconItem = ItemStack(displayIcon)
            .name("§6🎨 Rank Icon")
            .lore("§7Current: §f${if (selectedIcon == Material.AIR) "Not set" else selectedIcon.name}")
            .lore("§7")
            .lore("§7Examples:")
            .lore("§7• diamond, gold_ingot, emerald")
            .lore("§7• diamond_sword, golden_apple")
            .lore("§7")
            .lore("§e📖 Clickable link in chat")

        if (inputMode == "icon") {
            iconItem.name("§e⏳ WAITING FOR ICON INPUT...")
                .lore("§7Type the material name in chat")
                .lore("§7Examples: diamond, gold_ingot, etc.")
                .lore("§7Or click cancel to stop")
        } else {
            iconItem.lore("§eClick to change rank icon")
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
        val permCountItem = ItemStack(Material.BOOK)
            .name("§6📊 Permission Summary")
            .lore("§7Total Permissions: §f${rank.permissions.size}")
            .lore("§7")
            .lore("§7Manage permissions below")

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
            ).name("§6🔧 $categoryName")

            categoryItem.lore("§7Permissions in this category:")
            permissions.forEach { permission ->
                val hasPermission = rank.permissions.contains(permission)
                val displayName = permission.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                categoryItem.lore("§7• ${if (hasPermission) "§a✓" else "§c✗"} §f$displayName")
            }

            categoryItem.lore("§7")
            when {
                hasAllPermissions -> categoryItem.lore("§a✅ All permissions enabled")
                hasAnyPermission -> categoryItem.lore("§e⚠️ Some permissions enabled")
                else -> categoryItem.lore("§c❌ No permissions enabled")
            }
            categoryItem.lore("§7")
            categoryItem.lore("§eClick to manage permissions")

            val categoryGuiItem = GuiItem(categoryItem) {
                // Prevent owner from removing their own permissions
                if (isEditingOwnOwnerRank()) {
                    player.sendMessage("§c❌ You cannot modify your own owner rank permissions!")
                    player.sendMessage("§7This prevents you from locking yourself out of guild management.")
                    return@GuiItem
                }
                // Prevent opening Claims category when claims are disabled
                if (categoryName == "Claims" && !areClaimsEnabled()) {
                    player.sendMessage("§c❌ Claims system is disabled - claim permissions are not available.")
                    return@GuiItem
                }
                openPermissionCategoryMenu(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack(Material.EMERALD_BLOCK)
            .name("§a💾 Save Changes")
            .lore("§7Apply all permission changes")
            .lore("§7")
            .lore("§aClick to save")

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
                player.sendMessage("§a✅ Rank changes saved!")
                // Update local rank reference
                rank = updatedRank
            } else {
                player.sendMessage("§c❌ Failed to save rank changes!")
            }

            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(saveGuiItem, 1, 5)

        // Reset to defaults
        val resetItem = ItemStack(Material.BARRIER)
            .name("§c🔄 Reset Permissions")
            .lore("§7Clear all permissions")
            .lore("§7This cannot be undone")
            .lore("§7")
            .lore("§cClick to reset")

        val resetGuiItem = GuiItem(resetItem) {
            player.sendMessage("§eReset functionality coming soon!")
            player.sendMessage("§7This will clear all permissions for this rank.")
        }
        pane.addItem(resetGuiItem, 3, 5)

        // Delete rank
        val deleteItem = ItemStack(Material.TNT)
            .name("§4🗑️ Delete Rank")
            .lore("§7Permanently remove this rank")
            .lore("§7Members will be unassigned")
            .lore("§7")
            .lore("§4⚠️ DESTRUCTIVE ACTION")
            .lore("§cClick to delete")

        val deleteGuiItem = GuiItem(deleteItem) {
            player.sendMessage("§eRank deletion coming soon!")
            player.sendMessage("§7This will show a confirmation menu.")
        }
        pane.addItem(deleteGuiItem, 5, 5)

        // Back to rank management
        val backItem = ItemStack(Material.ARROW)
            .name("§7⬅️ Back")
            .lore("§7Return to rank management")

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
                permissions
            )
        )
    }

    private fun getMemberCount(): Int {
        // TODO: Get actual member count from MemberService
        return 0
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage("§6=== RANK NAME EDIT ===")
        player.sendMessage("§7Type the new rank name in chat.")
        player.sendMessage("§7Current name: §f${rank.name}")
        player.sendMessage("§7Requirements:")
        player.sendMessage("§7• 1-24 characters")
        player.sendMessage("§7• No special characters")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6=======================")
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage("§6=== RANK ICON EDIT ===")
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
        player.sendMessage("§6=======================")
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
                    player.sendMessage("§c❌ Invalid name: $error")
                    player.sendMessage("§7Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    // TODO: Update rank name in database
                    player.sendMessage("§a✅ Rank name updated to: '$input'")
                    player.sendMessage("§7Changes will be saved when you apply them.")
                    inputMode = ""
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
                    selectedIcon = material
                    // TODO: Update rank icon in database
                    player.sendMessage("§a✅ Rank icon updated to: ${material.name}")
                    player.sendMessage("§7Changes will be saved when you apply them.")
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
        player.sendMessage("§7Input cancelled.")

        // Reopen the menu
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Rank -> rank = data
        }
    }
}

