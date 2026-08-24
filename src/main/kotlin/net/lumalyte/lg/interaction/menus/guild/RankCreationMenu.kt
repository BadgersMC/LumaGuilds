package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()
    
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
            player.sendMessage(lang.msg("menu.rank_creation.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.required_permission"))
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.rank_creation.title", "guild" to guild.name)))
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
            .name(lang.legacy("menu.rank_creation.name.name"))
            .lore(if (rankName.isNotEmpty()) lang.legacy("menu.rank_creation.name.current", "rank" to rankName) else lang.legacy("menu.rank_creation.name.not_set"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.rank_creation.name.requirements"))
            .lore(lang.legacy("menu.rank_edit.input.name.length"))
            .lore(lang.legacy("menu.rank_edit.input.name.characters"))
            .lore(lang.legacy("menu.rank_creation.name.unique"))
            .lore(lang.legacy("menu.common.blank"))

        if (inputMode == "name") {
            nameItem.name(lang.legacy("menu.rank_edit.info.waiting_name"))
                .lore(lang.legacy("menu.rank_creation.name.type_name"))
                .lore(lang.legacy("menu.rank_edit.info.cancel_hint"))
        } else {
            nameItem.lore(lang.legacy("menu.rank_creation.name.click"))
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.already_waiting_name"))
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (rankIcon == Material.AIR) Material.DIAMOND_SWORD else rankIcon
        val iconItem = ItemStack.of(displayIcon)
            .name(lang.legacy("menu.rank_edit.icon.name"))
            .lore(lang.legacy("menu.rank_edit.icon.current", "material" to if (rankIcon == Material.AIR) lang.raw("menu.rank_edit.icon.not_set") else rankIcon.name))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.rank_edit.icon.examples"))
            .lore(lang.legacy("menu.rank_creation.icon.example_diamond"))
            .lore(lang.legacy("menu.rank_creation.icon.example_gold"))
            .lore(lang.legacy("menu.rank_creation.icon.example_sword"))
            .lore(lang.legacy("menu.rank_creation.icon.example_emerald"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.rank_edit.icon.link_hint"))

        if (inputMode == "icon") {
            iconItem.name(lang.legacy("menu.rank_edit.icon.waiting"))
                .lore(lang.legacy("menu.rank_edit.icon.type_material"))
                .lore(lang.legacy("menu.rank_edit.icon.example_short"))
                .lore(lang.legacy("menu.rank_edit.info.cancel_hint"))
        } else {
            iconItem.lore(lang.legacy("menu.rank_creation.icon.click"))
        }

        val iconGuiItem = GuiItem(iconItem) {
            if (inputMode != "icon") {
                startIconInput()
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.already_waiting_icon"))
            }
        }
        pane.addItem(iconGuiItem, 3, 0)

        // Permission count
        val countItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.rank_creation.summary.name"))
            .lore(lang.legacy("menu.rank_creation.summary.count", "count" to selectedPermissions.size))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.rank_creation.summary.select_below"))

        pane.addItem(GuiItem(countItem), 7, 0)
    }

    private fun addPermissionTemplates(pane: StaticPane) {
        // Template presets for common roles
        val baseTemplates = mutableMapOf(
            "Banker" to setOf(
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS
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
                .name(lang.legacy("menu.rank_creation.template.name", "template" to localizedTemplateName(templateName)))
                .lore(lang.legacy("menu.rank_creation.template.description", "template" to localizedTemplateName(templateName)))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.rank_creation.template.includes"))

            permissions.forEach { permission ->
                val permissionKey = "permission.${permission.name.lowercase().replace("_", ".")}"
                templateItem.lore(lang.legacy("menu.rank_creation.template.permission", "permission" to lang.raw(permissionKey)))
            }

            templateItem.lore(lang.legacy("menu.common.blank"))
            templateItem.lore(lang.legacy("menu.rank_creation.template.click"))

            val templateGuiItem = GuiItem(templateItem) {
                selectedPermissions.clear()
                selectedPermissions.addAll(permissions)
                if (rankName.isEmpty()) {
                    rankName = templateName
                }
                player.sendMessage(lang.msg("menu.rank_creation.feedback.template_applied", "template" to localizedTemplateName(templateName)))
                player.sendMessage(lang.msg("menu.rank_creation.feedback.permissions_selected", "count" to permissions.size))
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
                RankPermission.VIEW_BANK_TRANSACTIONS,
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
            ).name(lang.legacy("menu.rank_edit.category.name", "category" to localizedCategoryName(categoryName)))
                .lore(lang.legacy("menu.rank_creation.category.count", "enabled" to enabledCount, "total" to permissions.size))
                .lore(lang.legacy("menu.common.blank"))

            if (hasAnyPermission) {
                categoryItem.lore(lang.legacy("menu.rank_creation.category.some_selected"))
            } else {
                categoryItem.lore(lang.legacy("menu.rank_creation.category.none_selected"))
            }

            categoryItem.lore(lang.legacy("menu.common.blank"))
            categoryItem.lore(lang.legacy("menu.rank_creation.category.toggle", "category" to localizedCategoryName(categoryName)))

            val categoryGuiItem = GuiItem(categoryItem) {
                openPermissionCategorySelection(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    private fun addPreviewSection(pane: StaticPane) {
        val previewItem = ItemStack.of(if (rankIcon == Material.AIR) Material.DIAMOND_SWORD else rankIcon)
            .name(lang.legacy("menu.rank_creation.preview.name"))
            .lore(if (rankName.isNotEmpty()) lang.legacy("menu.rank_creation.preview.rank_name", "rank" to rankName) else lang.legacy("menu.rank_creation.preview.name_not_set"))
            .lore(lang.legacy("menu.rank_creation.preview.icon", "icon" to rankIcon.name))
            .lore(lang.legacy("menu.rank_creation.preview.priority", "priority" to rankPriority))
            .lore(lang.legacy("menu.rank_creation.preview.permissions", "count" to selectedPermissions.size))
            .lore(lang.legacy("menu.common.blank"))

        if (selectedPermissions.isNotEmpty()) {
            previewItem.lore(lang.legacy("menu.rank_creation.preview.selected"))
            val grouped = groupPermissionsByCategory(selectedPermissions)
            grouped.forEach { (category, perms) ->
                if (perms.isNotEmpty()) {
                    previewItem.lore(lang.legacy("menu.rank_creation.preview.category", "category" to localizedCategoryName(category), "count" to perms.size))
                }
            }
        } else {
            previewItem.lore(lang.legacy("menu.rank_creation.category.none_selected"))
        }

        pane.addItem(GuiItem(previewItem), 4, 4)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create rank
        val canCreate = rankName.isNotEmpty() && selectedPermissions.isNotEmpty()
        val createItem = ItemStack.of(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(if (canCreate) lang.legacy("menu.rank_creation.action.create.name") else lang.legacy("menu.rank_creation.action.create.cannot"))
            .lore(lang.legacy("menu.rank_creation.action.create.description"))

        if (canCreate) {
            createItem.lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.rank_creation.action.create.ready"))
                .lore(lang.legacy("menu.rank_creation.action.create.click"))
        } else {
            createItem.lore(lang.legacy("menu.common.blank"))
            if (rankName.isEmpty()) createItem.lore(lang.legacy("menu.rank_creation.action.create.missing_name"))
            if (selectedPermissions.isEmpty()) createItem.lore(lang.legacy("menu.rank_creation.action.create.missing_permissions"))
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

                    player.sendMessage(lang.msg("menu.rank_creation.feedback.created", "rank" to rankName))
                    player.sendMessage(lang.msg("menu.rank_creation.feedback.rank_permissions", "count" to selectedPermissions.size))
                    if (iconString != null) {
                        player.sendMessage(lang.msg("menu.rank_creation.feedback.rank_icon", "icon" to iconString))
                    }
                } else {
                    player.sendMessage(lang.msg("menu.rank_creation.feedback.create_failed"))
                }

                menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.rank_creation.feedback.missing_requirements"))
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.rank_creation.action.clear.name"))
            .lore(lang.legacy("menu.rank_creation.action.clear.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.rank_creation.action.clear.click"))

        val clearGuiItem = GuiItem(clearItem) {
            rankName = ""
            rankIcon = Material.AIR
            selectedPermissions.clear()
            inputMode = ""
            player.sendMessage(lang.msg("menu.rank_creation.feedback.cleared"))
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Cancel
        val cancelItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.rank_creation.action.cancel.name"))
            .lore(lang.legacy("menu.rank_creation.action.cancel.description"))

        val cancelGuiItem = GuiItem(cancelItem) {
            player.sendMessage(lang.msg("menu.rank_creation.feedback.cancelled"))
            menuNavigator.openMenu(GuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, 7, 5)
    }

    private fun openPermissionCategorySelection(categoryName: String, permissions: List<RankPermission>) {
        // Toggle individual permissions for this category during rank creation
        val allSelected = permissions.all { it in selectedPermissions }
        if (allSelected) {
            // Remove all — toggle them off one by one
            permissions.forEach { perm ->
                selectedPermissions.remove(perm)
                player.sendMessage(lang.msg("menu.rank_creation.feedback.permission_removed", "permission" to localizedPermissionName(perm)))
            }
            player.sendMessage(lang.msg("menu.rank_creation.feedback.category_removed", "category" to localizedCategoryName(categoryName)))
        } else {
            // Add only the ones not yet selected
            val added = permissions.filter { it !in selectedPermissions }
            added.forEach { perm ->
                selectedPermissions.add(perm)
                player.sendMessage(lang.msg("menu.rank_creation.feedback.permission_added", "permission" to localizedPermissionName(perm)))
            }
            if (added.size < permissions.size) {
                player.sendMessage(lang.msg("menu.rank_creation.feedback.some_enabled", "category" to localizedCategoryName(categoryName)))
            }
        }
        open() // Refresh the creation menu
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
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES,
                RankPermission.USE_ALLY_HOMES -> "Diplomacy"
                
                RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
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

    private fun localizedPermissionName(permission: RankPermission): String {
        val key = "permission.${permission.name.lowercase().replace("_", ".")}"
        return lang.raw(key)
    }

    private fun localizedCategoryName(categoryName: String): String = when (categoryName) {
        "Guild Management" -> lang.raw("menu.rank_edit.category.guild_management")
        "Banking" -> lang.raw("menu.rank_edit.category.banking")
        "Diplomacy" -> lang.raw("menu.rank_edit.category.diplomacy")
        "Communication" -> lang.raw("menu.rank_edit.category.communication")
        "Administrative" -> lang.raw("menu.rank_edit.category.administrative")
        "Claims" -> lang.raw("menu.rank_edit.category.claims")
        else -> categoryName
    }

    private fun localizedTemplateName(templateName: String): String = when (templateName) {
        "Banker" -> lang.raw("menu.rank_creation.template.banker")
        "Envoy" -> lang.raw("menu.rank_creation.template.envoy")
        "Builder" -> lang.raw("menu.rank_creation.template.builder")
        "Moderator" -> lang.raw("menu.rank_creation.template.moderator")
        else -> templateName
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage(lang.msg("menu.rank_creation.input.name.header"))
        player.sendMessage(lang.msg("menu.rank_creation.input.name.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.examples"))
        player.sendMessage(lang.msg("menu.rank_creation.input.name.example_one"))
        player.sendMessage(lang.msg("menu.rank_creation.input.name.example_two"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.requirements"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.length"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.characters"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.rank_creation.input.footer"))
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage(lang.msg("menu.rank_creation.input.icon.header"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.examples"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_basic"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_tools"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_blocks"))
        player.sendMessage(lang.msg("menu.common.blank"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.valid_material"))
        player.sendMessage(lang.msg("menu.common.blank"))
        
        // Create clickable link using Adventure API
        val linkText = Component.text(lang.raw("menu.rank_edit.input.icon.link_prefix"))
            .color(NamedTextColor.YELLOW)
            .append(
                Component.text(lang.raw("menu.rank_edit.input.icon.link_action"))
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl("https://jd.papermc.io/paper/1.21.8/org/bukkit/Material.html"))
            )
        player.sendMessage(linkText)
        
        player.sendMessage(lang.msg("menu.common.blank"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.rank_creation.input.footer"))
    }

    private fun validateRankName(name: String): String? {
        if (name.length !in 1..24) {
            return lang.legacy("menu.rank_edit.validation.length", "length" to name.length)
        }
        if (!name.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
            return lang.legacy("menu.rank_edit.validation.characters")
        }
        // Check if name is unique in guild
        val existingRank = rankService.getRankByName(guild.id, name)
        if (existingRank != null) {
            return lang.legacy("menu.rank_edit.validation.duplicate")
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
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.invalid_name", "error" to error))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.try_again"))
                    // Keep input mode active and reopen menu for retry
                } else {
                    rankName = input
                    inputMode = ""
                    player.sendMessage(lang.msg("menu.rank_creation.feedback.name_set", "rank" to input))
                }
            }
            "icon" -> {
                val material = validateMaterial(input)
                if (material == null) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.invalid_material", "material" to input))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.material_examples"))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.try_again"))
                    // Keep input mode active and reopen menu for retry
                } else {
                    rankIcon = material
                    inputMode = ""
                    player.sendMessage(lang.msg("menu.rank_creation.feedback.icon_set", "material" to material.name))
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
        player.sendMessage(lang.msg("menu.rank_edit.feedback.input_cancelled"))

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

