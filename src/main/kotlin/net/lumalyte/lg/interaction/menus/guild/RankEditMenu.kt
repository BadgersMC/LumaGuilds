package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()
    
    // Edit state
    private var inputMode: String = "" // "name" or "icon"
    private var selectedIcon: Material = loadRankIcon() // Track selected icon
    
    // Check if the rank being edited is the owner rank
    private fun isOwnerRank(): Boolean {
        val ownerRank = rankService.getHighestRank(guild.id)
        return rank.id == ownerRank?.id
    }
    
    // Check if the player is editing their own rank (any rank, not just owner)
    private fun isEditingOwnRank(): Boolean {
        return rankService.isPlayerRank(player.uniqueId, guild.id, rank.id)
    }

    private fun canActorReorder(): Boolean {
        val actorRank = rankService.getPlayerRank(player.uniqueId, guild.id) ?: return false
        return actorRank.priority < rank.priority
    }

    private fun siblingAt(direction: net.lumalyte.lg.application.services.PriorityDirection): net.lumalyte.lg.domain.entities.Rank? {
        val siblings = rankService.listRanks(guild.id).sortedBy { it.priority }
        val idx = siblings.indexOfFirst { it.id == rank.id }
        val neighborIdx = when (direction) {
            net.lumalyte.lg.application.services.PriorityDirection.UP -> idx - 1
            net.lumalyte.lg.application.services.PriorityDirection.DOWN -> idx + 1
        }
        return siblings.getOrNull(neighborIdx)
    }

    private fun addPriorityButtons(pane: StaticPane) {
        val upNeighbor = siblingAt(net.lumalyte.lg.application.services.PriorityDirection.UP)
        val downNeighbor = siblingAt(net.lumalyte.lg.application.services.PriorityDirection.DOWN)
        val canUp = canActorReorder() && upNeighbor != null && !isOwnerRank()
        val canDown = canActorReorder() && downNeighbor != null && !isOwnerRank()

        val upItem = ItemStack.of(if (canUp) Material.SPECTRAL_ARROW else Material.BARRIER)
            .name(if (canUp) lang.gui("menu.rank_edit.priority.up") else lang.gui("menu.rank_edit.priority.up_locked"))
            .lore(lang.gui("menu.rank_edit.priority.current", "priority" to rank.priority))
            .lore(if (upNeighbor != null) lang.gui("menu.rank_edit.priority.above", "rank" to upNeighbor.name) else lang.gui("menu.rank_edit.priority.no_above"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (canUp) lang.gui("menu.rank_edit.priority.raise") else lang.gui("menu.rank_edit.priority.cannot_reorder"))
        pane.addItem(GuiItem(upItem) {
            if (!canUp) return@GuiItem
            val ok = rankService.moveRankPriority(rank.id, net.lumalyte.lg.application.services.PriorityDirection.UP, player.uniqueId)
            if (ok) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.moved_up"))
                rank = rankService.getRank(rank.id) ?: rank
                open()
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.move_failed"))
            }
        }, 0, 0)

        val downItem = ItemStack.of(if (canDown) Material.SPECTRAL_ARROW else Material.BARRIER)
            .name(if (canDown) lang.gui("menu.rank_edit.priority.down") else lang.gui("menu.rank_edit.priority.down_locked"))
            .lore(lang.gui("menu.rank_edit.priority.current", "priority" to rank.priority))
            .lore(if (downNeighbor != null) lang.gui("menu.rank_edit.priority.below", "rank" to downNeighbor.name) else lang.gui("menu.rank_edit.priority.no_below"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (canDown) lang.gui("menu.rank_edit.priority.lower") else lang.gui("menu.rank_edit.priority.cannot_reorder"))
        pane.addItem(GuiItem(downItem) {
            if (!canDown) return@GuiItem
            val ok = rankService.moveRankPriority(rank.id, net.lumalyte.lg.application.services.PriorityDirection.DOWN, player.uniqueId)
            if (ok) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.moved_down"))
                rank = rankService.getRank(rank.id) ?: rank
                open()
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.move_failed"))
            }
        }, 8, 0)
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
        // Security check: Only players with MANAGE_RANKS permission can edit ranks
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)
        if (!hasPermission) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.required_permission"))
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.rank_edit.title", "rank" to rank.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 0: Rank Information + priority buttons
        addPriorityButtons(pane)
        addRankInfoSection(pane)

        // Row 1-4: Permission Categories
        addPermissionCategories(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addRankInfoSection(pane: StaticPane) {
        // Rank name and basic info
        val infoItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.rank_edit.info.name"))
            .lore(lang.gui("menu.rank_edit.info.rank_name", "rank" to rank.name))
            .lore(lang.gui("menu.rank_edit.info.priority", "priority" to rank.priority))
            .lore(lang.gui("menu.rank_edit.info.members", "count" to getMemberCount()))
            
        // Add protection warning if editing own rank
        if (isEditingOwnRank()) {
            infoItem.lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.rank_edit.info.protection"))
                .lore(lang.gui("menu.rank_edit.info.permission_changes_blocked"))
                .lore(lang.gui("menu.rank_edit.info.own_rank"))
        }
        
        infoItem.lore(lang.gui("menu.common.blank"))

        if (inputMode == "name") {
            infoItem.name(lang.gui("menu.rank_edit.info.waiting_name"))
                .lore(lang.gui("menu.rank_edit.info.type_name"))
                .lore(lang.gui("menu.rank_edit.info.cancel_hint"))
        } else {
            infoItem.lore(lang.gui("menu.rank_edit.info.rename"))
        }

        val infoGuiItem = GuiItem(infoItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.already_waiting_name"))
            }
        }
        pane.addItem(infoGuiItem, 1, 0)

        // Rank icon selection
        val displayIcon = if (selectedIcon == Material.AIR) Material.DIAMOND_SWORD else selectedIcon
        val iconItem = ItemStack.of(displayIcon)
            .name(lang.gui("menu.rank_edit.icon.name"))
            .lore(lang.gui("menu.rank_edit.icon.current", "material" to if (selectedIcon == Material.AIR) lang.gui("menu.rank_edit.icon.not_set") else selectedIcon.name))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.icon.examples"))
            .lore(lang.gui("menu.rank_edit.icon.example_basic"))
            .lore(lang.gui("menu.rank_edit.icon.example_tools"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.icon.link_hint"))

        if (inputMode == "icon") {
            iconItem.name(lang.gui("menu.rank_edit.icon.waiting"))
                .lore(lang.gui("menu.rank_edit.icon.type_material"))
                .lore(lang.gui("menu.rank_edit.icon.example_short"))
                .lore(lang.gui("menu.rank_edit.info.cancel_hint"))
        } else {
            iconItem.lore(lang.gui("menu.rank_edit.icon.change"))
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
        val permCountItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.rank_edit.summary.name"))
            .lore(lang.gui("menu.rank_edit.summary.total", "count" to rank.permissions.size))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.summary.manage_below"))

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
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.MANAGE_BANK_SETTINGS,
                RankPermission.PLACE_VAULT, RankPermission.ACCESS_VAULT,
                RankPermission.DEPOSIT_TO_VAULT, RankPermission.WITHDRAW_FROM_VAULT,
                RankPermission.MANAGE_VAULT, RankPermission.BREAK_VAULT,
                RankPermission.ACCESS_SHOP_CHESTS, RankPermission.EDIT_SHOP_STOCK,
                RankPermission.MODIFY_SHOP_PRICES
            ),
            "Diplomacy" to listOf(
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_PARTIES,
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES,
                RankPermission.USE_ALLY_HOMES
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

            val categoryItem = ItemStack.of(
                when (categoryName) {
                    "Guild Management" -> Material.GOLDEN_SWORD
                    "Banking" -> Material.GOLD_INGOT
                    "Diplomacy" -> Material.WRITABLE_BOOK
                    "Claims" -> Material.GRASS_BLOCK
                    "Communication" -> Material.BELL
                    "Administrative" -> Material.COMMAND_BLOCK
                    else -> Material.PAPER
                }
            ).name(lang.gui("menu.rank_edit.category.name", "category" to localizedCategoryName(categoryName)))

            categoryItem.lore(lang.gui("menu.rank_edit.category.permissions"))
            permissions.forEach { permission ->
                val hasPermission = rank.permissions.contains(permission)
                val permissionKey = "permission.${permission.name.lowercase().replace("_", ".")}"
                val displayName = lang.gui(permissionKey)
                categoryItem.lore(if (hasPermission) {
                    lang.gui("menu.rank_edit.category.permission_enabled", "permission" to displayName)
                } else {
                    lang.gui("menu.rank_edit.category.permission_disabled", "permission" to displayName)
                })
            }

            categoryItem.lore(lang.gui("menu.common.blank"))
            when {
                hasAllPermissions -> categoryItem.lore(lang.gui("menu.rank_edit.category.all_enabled"))
                hasAnyPermission -> categoryItem.lore(lang.gui("menu.rank_edit.category.some_enabled"))
                else -> categoryItem.lore(lang.gui("menu.rank_edit.category.none_enabled"))
            }
            categoryItem.lore(lang.gui("menu.common.blank"))
            categoryItem.lore(lang.gui("menu.rank_edit.category.open"))

            val categoryGuiItem = GuiItem(categoryItem) {
                // Prevent owner from removing their own permissions
                if (isEditingOwnRank()) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_modify_own"))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.contact_higher_rank"))
                    return@GuiItem
                }
                // Prevent opening Claims category when claims are disabled
                if (categoryName == "Claims" && !areClaimsEnabled()) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.claims_disabled"))
                    return@GuiItem
                }
                openPermissionCategoryMenu(categoryName, permissions)
            }
            pane.addItem(categoryGuiItem, col, row)
        }
    }

    private fun localizedCategoryName(categoryName: String): Component = when (categoryName) {
        "Guild Management" -> lang.gui("menu.rank_edit.category.guild_management")
        "Banking" -> lang.gui("menu.rank_edit.category.banking")
        "Diplomacy" -> lang.gui("menu.rank_edit.category.diplomacy")
        "Communication" -> lang.gui("menu.rank_edit.category.communication")
        "Administrative" -> lang.gui("menu.rank_edit.category.administrative")
        "Claims" -> lang.gui("menu.rank_edit.category.claims")
        else -> net.lumalyte.lg.infrastructure.i18n.GuiTextStyler.style(Component.text(categoryName))
    }

    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name(lang.gui("menu.rank_edit.action.save.name"))
            .lore(lang.gui("menu.rank_edit.action.save.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.action.save.click"))

        val saveGuiItem = GuiItem(saveItem) {
            // Create updated rank with current permissions and new icon
            val iconString = if (selectedIcon == Material.AIR) null else selectedIcon.name
            val updatedRank = rank.copy(
                permissions = rank.permissions,
                icon = iconString
            )

            // Save to database
            val success = rankService.updateRank(updatedRank, player.uniqueId)

            if (success) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.saved"))
                // Update local rank reference
                rank = updatedRank
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.save_failed"))
            }

            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(saveGuiItem, 1, 5)

        // Reset to defaults
        val resetItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.rank_edit.action.reset.name"))
            .lore(lang.gui("menu.rank_edit.action.reset.description"))
            .lore(lang.gui("menu.rank_edit.action.irreversible"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.action.reset.click"))

        val resetGuiItem = GuiItem(resetItem) {
            // Prevent resetting the owner rank
            if (isOwnerRank()) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_reset_owner"))
                player.sendMessage(lang.msg("menu.rank_edit.feedback.owner_permissions_permanent"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return@GuiItem
            }
            // Prevent resetting your own rank
            if (isEditingOwnRank()) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_reset_own"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return@GuiItem
            }
            val success = rankService.setRankPermissions(rank.id, emptySet(), player.uniqueId)
            if (success) {
                // Refresh the local rank from the service
                rank = rankService.getRank(rank.id) ?: rank
                player.sendMessage(lang.msg("menu.rank_edit.feedback.reset"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                open() // Refresh
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.reset_failed"))
            }
        }
        pane.addItem(resetGuiItem, 3, 5)

        // Delete rank
        val deleteItem = ItemStack.of(Material.TNT)
            .name(lang.gui("menu.rank_edit.action.delete.name"))
            .lore(lang.gui("menu.rank_edit.action.delete.description"))
            .lore(lang.gui("menu.rank_edit.action.delete.members"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_edit.action.delete.warning"))
            .lore(lang.gui("menu.rank_edit.action.delete.click"))

        val deleteGuiItem = GuiItem(deleteItem) {
            // Prevent deleting the owner rank
            if (isOwnerRank()) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_owner"))
                player.sendMessage(lang.msg("menu.rank_edit.feedback.owner_permanent"))
                return@GuiItem
            }

            // Prevent deleting your own rank
            if (isEditingOwnRank()) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_own"))
                player.sendMessage(lang.msg("menu.rank_edit.feedback.lose_access"))
                return@GuiItem
            }

            // Prevent deleting the last remaining rank
            val allRanks = rankService.listRanks(guild.id)
            if (allRanks.size <= 1) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_last"))
                player.sendMessage(lang.msg("menu.rank_edit.feedback.rank_required"))
                return@GuiItem
            }

            // Migrate members to another rank if this rank is in use
            val membersWithRank = memberService.getMembersByRank(guild.id, rank.id)
            if (membersWithRank.isNotEmpty()) {
                // Find the lowest-priority rank that isn't this one
                val targetRank = allRanks
                    .filter { it.id != rank.id }
                    .maxByOrNull { it.priority }

                if (targetRank == null) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.no_target_rank"))
                    return@GuiItem
                }

                for (member in membersWithRank) {
                    memberService.changeMemberRank(member.playerId, guild.id, targetRank.id, player.uniqueId)
                }
                player.sendMessage(lang.msg("menu.rank_edit.feedback.members_moved", "count" to membersWithRank.size, "rank" to targetRank.name))
            }

            val result = rankService.deleteRank(rank.id, player.uniqueId)
            if (result) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.deleted", "rank" to rank.name))
            } else {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.delete_failed"))
            }

            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(deleteGuiItem, 5, 5)

        // Back to rank management
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_edit.action.back.name"))
            .lore(lang.gui("menu.rank_edit.action.back.description"))

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
        return memberService.getMembersByRank(guild.id, rank.id).size
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage(lang.msg("menu.rank_edit.input.name.header"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.current", "rank" to rank.name))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.requirements"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.length"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.characters"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.rank_edit.input.footer"))
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage(lang.msg("menu.rank_edit.input.icon.header"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.examples"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_basic"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_tools"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.example_blocks"))
        player.sendMessage(lang.msg("menu.common.blank"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.valid_material"))
        player.sendMessage(lang.msg("menu.common.blank"))
        
        // Create clickable link using Adventure API
        val linkText = lang.msg("menu.rank_edit.input.icon.link_prefix")
            .color(NamedTextColor.YELLOW)
            .append(
                lang.msg("menu.rank_edit.input.icon.link_action")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl("https://jd.papermc.io/paper/1.21.8/org/bukkit/Material.html"))
            )
        player.sendMessage(linkText)
        
        player.sendMessage(lang.msg("menu.common.blank"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.rank_edit.input.footer"))
    }

    private fun validateRankName(name: String): Component? {
        if (name.length !in 1..24) {
            return lang.msg("menu.rank_edit.validation.length", "length" to name.length)
        }
        if (!name.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
            return lang.msg("menu.rank_edit.validation.characters")
        }
        // Check if name is unique in guild (excluding current rank)
        val existingRank = rankService.getRankByName(guild.id, name)
        if (existingRank != null && existingRank.id != rank.id) {
            return lang.msg("menu.rank_edit.validation.duplicate")
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
                    // Update rank name in database
                    rank = rank.copy(name = input)
                    val success = rankService.updateRank(rank, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.name_updated", "rank" to input))
                    } else {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.name_update_failed"))
                    }
                    inputMode = ""
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
                    selectedIcon = material
                    // Update rank icon in database
                    rank = rank.copy(icon = material.name)
                    val success = rankService.updateRank(rank, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.icon_updated", "material" to material.name))
                    } else {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.icon_update_failed"))
                    }
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
        player.sendMessage(lang.msg("menu.rank_edit.feedback.input_cancelled"))

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

