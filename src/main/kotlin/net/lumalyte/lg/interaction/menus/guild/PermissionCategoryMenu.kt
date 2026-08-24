package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.badgersmc.nexus.i18n.LangService

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
    private val lang: LangService by inject()
    private var modifiedPermissions = rank.permissions.toMutableSet()

    private val selfEditDeniedMsg get() = lang.msg("menu.permission_category.feedback.self_edit_denied")
    
    // Check if the player is editing their own rank (any rank, not just owner)
    private fun isEditingOwnRank(): Boolean {
        return rankService.isPlayerRank(player.uniqueId, guild.id, rank.id)
    }

    override fun open() {
        // Security check: Only players with MANAGE_RANKS permission can edit ranks
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.required_permission"))
            menuNavigator.openMenu(RankEditMenu(menuNavigator, player, guild, rank))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.permission_category.title", "category" to localizedCategoryName(), "rank" to rank.name)))
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

        val infoItem = ItemStack.of(categoryIcon)
            .name(lang.gui("menu.rank_edit.category.name", "category" to localizedCategoryName()))
            .lore(lang.gui("menu.permission_category.info.rank", "rank" to rank.name))
            .lore(lang.gui("menu.permission_category.info.category", "category" to localizedCategoryName()))
            .lore(lang.gui("menu.permission_category.info.total", "count" to categoryPermissions.size))
            
        // Add protection warning if editing own rank
        if (isEditingOwnRank()) {
            infoItem.lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.permission_category.info.protection"))
                .lore(lang.gui("menu.rank_edit.info.permission_changes_blocked"))
                .lore(lang.gui("menu.permission_category.info.prevent_lockout"))
        }

        pane.addItem(GuiItem(infoItem), 1, 0)

        // Enable all button
        val enableAllItem = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.permission_category.enable_all.name"))
            .lore(lang.gui("menu.permission_category.enable_all.description", "category" to localizedCategoryName()))
            .lore(lang.gui("menu.permission_category.enable_all.rank"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.enable_all.click"))

        val enableAllGuiItem = GuiItem(enableAllItem) {
            if (isEditingOwnRank()) {
                player.sendMessage(selfEditDeniedMsg)
                player.sendMessage(lang.msg("menu.permission_category.feedback.prevent_lockout"))
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.add(permission)
            }
            player.sendMessage(lang.msg("menu.permission_category.feedback.enabled_all", "category" to localizedCategoryName()))
            open() // Refresh the menu
        }
        pane.addItem(enableAllGuiItem, 3, 0)

        // Disable all button
        val disableAllItem = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.permission_category.disable_all.name"))
            .lore(lang.gui("menu.permission_category.disable_all.description", "category" to localizedCategoryName()))
            .lore(lang.gui("menu.permission_category.disable_all.rank"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.disable_all.click"))

        val disableAllGuiItem = GuiItem(disableAllItem) {
            if (isEditingOwnRank()) {
                player.sendMessage(selfEditDeniedMsg)
                player.sendMessage(lang.msg("menu.permission_category.feedback.prevent_lockout"))
                return@GuiItem
            }
            categoryPermissions.forEach { permission ->
                modifiedPermissions.remove(permission)
            }
            player.sendMessage(lang.msg("menu.permission_category.feedback.disabled_all", "category" to localizedCategoryName()))
            open() // Refresh the menu
        }
        pane.addItem(disableAllGuiItem, 5, 0)

        // Permission count
        val enabledCount = categoryPermissions.count { modifiedPermissions.contains(it) }
        val countItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.permission_category.status.name"))
            .lore(lang.gui("menu.permission_category.status.count", "enabled" to enabledCount, "total" to categoryPermissions.size))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.status.hint"))

        pane.addItem(GuiItem(countItem), 7, 0)
    }

    private fun addPermissionButtons(pane: StaticPane) {
        categoryPermissions.forEachIndexed { index, permission ->
            val row = 1 + (index / 7) // 7 permissions per row
            val col = 1 + (index % 7)

            if (row > 4) return@forEachIndexed // Don't overflow into action row

            val hasPermission = modifiedPermissions.contains(permission)
            val permissionKey = "permission.${permission.name.lowercase().replace("_", ".")}"
            val displayName = lang.gui(permissionKey)
            val chatDisplayName = lang.raw(permissionKey)

            val permissionItem = ItemStack.of(
                if (hasPermission) Material.LIME_CONCRETE_POWDER else Material.RED_CONCRETE_POWDER
            )
                .name(if (hasPermission) lang.gui("menu.rank_edit.category.permission_enabled", "permission" to displayName) else lang.gui("menu.rank_edit.category.permission_disabled", "permission" to displayName))
                .lore(lang.gui("menu.permission_category.permission.identifier", "permission" to permission.name))
                .lore(if (hasPermission) lang.gui("menu.permission_category.permission.enabled") else lang.gui("menu.permission_category.permission.disabled"))
                .lore(lang.gui("menu.common.blank"))

            // Add description based on permission
            permissionItem.lore(getPermissionDescription(permission))
            permissionItem.lore(lang.gui("menu.common.blank"))
            permissionItem.lore(if (hasPermission) lang.gui("menu.permission_category.permission.disable") else lang.gui("menu.permission_category.permission.enable"))

            val permissionGuiItem = GuiItem(permissionItem) {
                if (isEditingOwnRank()) {
                    player.sendMessage(selfEditDeniedMsg)
                    player.sendMessage(lang.msg("menu.permission_category.feedback.prevent_lockout"))
                    return@GuiItem
                }
                if (hasPermission) {
                    modifiedPermissions.remove(permission)
                    player.sendMessage(lang.msg("menu.permission_category.feedback.disabled", "permission" to chatDisplayName, "rank" to rank.name))
                } else {
                    modifiedPermissions.add(permission)
                    player.sendMessage(lang.msg("menu.permission_category.feedback.enabled", "permission" to chatDisplayName, "rank" to rank.name))
                }
                open() // Refresh the menu
            }
            pane.addItem(permissionGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Save changes
        val saveItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name(lang.gui("menu.rank_edit.action.save.name"))
            .lore(lang.gui("menu.permission_category.action.save.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.action.save.click"))

        val saveGuiItem = GuiItem(saveItem) {
            // Update the rank with modified permissions
            val updatedRank = rank.copy(permissions = modifiedPermissions)
            val success = rankService.updateRank(updatedRank, player.uniqueId)
            if (success) {
                rank = updatedRank // Update local reference
                player.sendMessage(lang.msg("menu.permission_category.feedback.saved", "rank" to rank.name))
            } else {
                player.sendMessage(lang.msg("menu.permission_category.feedback.save_failed"))
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
            .name(lang.gui("menu.permission_category.action.cancel.name"))
            .lore(lang.gui("menu.permission_category.action.cancel.description"))
            .lore(lang.gui("menu.permission_category.action.return"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.action.cancel.click"))

        val cancelGuiItem = GuiItem(cancelItem) {
            player.sendMessage(lang.msg("menu.permission_category.feedback.discarded"))
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
            .name(lang.gui("menu.permission_category.action.reset.name"))
            .lore(lang.gui("menu.permission_category.action.reset.description"))
            .lore(lang.gui("menu.permission_category.action.reset.category"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.permission_category.action.reset.click"))

        val resetGuiItem = GuiItem(resetItem) {
            // Reset modified permissions to original for this category
            categoryPermissions.forEach { permission ->
                if (rank.permissions.contains(permission)) {
                    modifiedPermissions.add(permission)
                } else {
                    modifiedPermissions.remove(permission)
                }
            }
            player.sendMessage(lang.msg("menu.permission_category.feedback.reset", "category" to localizedCategoryName()))
            open() // Refresh the menu
        }
        pane.addItem(resetGuiItem, 5, 5)

        // Back to rank edit
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_edit.action.back.name"))
            .lore(lang.gui("menu.permission_category.action.return"))
            .lore(lang.gui("menu.permission_category.action.back.saved"))

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

    private fun getPermissionDescription(permission: RankPermission): Component {
        return when (permission) {
            // Guild Management
            RankPermission.MANAGE_RANKS -> lang.gui("menu.permission_category.description.manage_ranks")
            RankPermission.MANAGE_MEMBERS -> lang.gui("menu.permission_category.description.manage_members")
            RankPermission.MANAGE_BANNER -> lang.gui("menu.permission_category.description.manage_banner")
            RankPermission.MANAGE_EMOJI -> lang.gui("menu.permission_category.description.manage_emoji")
            RankPermission.MANAGE_DESCRIPTION -> lang.gui("menu.permission_category.description.manage_description")
            RankPermission.MANAGE_HOME -> lang.gui("menu.permission_category.description.manage_home")
            RankPermission.MANAGE_MODE -> lang.gui("menu.permission_category.description.manage_mode")
            RankPermission.MANAGE_GUILD_SETTINGS -> lang.gui("menu.permission_category.description.manage_guild_settings")

            // Banking
            RankPermission.DEPOSIT_TO_BANK -> lang.gui("menu.permission_category.description.deposit_to_bank")
            RankPermission.WITHDRAW_FROM_BANK -> lang.gui("menu.permission_category.description.withdraw_from_bank")
            RankPermission.VIEW_BANK_TRANSACTIONS -> lang.gui("menu.permission_category.description.view_bank_transactions")
            RankPermission.MANAGE_BANK_SETTINGS -> lang.gui("menu.permission_category.description.manage_bank_settings")
            RankPermission.PLACE_VAULT -> lang.gui("menu.permission_category.description.place_vault")
            RankPermission.ACCESS_VAULT -> lang.gui("menu.permission_category.description.access_vault")
            RankPermission.DEPOSIT_TO_VAULT -> lang.gui("menu.permission_category.description.deposit_to_vault")
            RankPermission.WITHDRAW_FROM_VAULT -> lang.gui("menu.permission_category.description.withdraw_from_vault")
            RankPermission.MANAGE_VAULT -> lang.gui("menu.permission_category.description.manage_vault")
            RankPermission.BREAK_VAULT -> lang.gui("menu.permission_category.description.break_vault")
            RankPermission.ACCESS_SHOP_CHESTS -> lang.gui("menu.permission_category.description.access_shop_chests")
            RankPermission.EDIT_SHOP_STOCK -> lang.gui("menu.permission_category.description.edit_shop_stock")
            RankPermission.MODIFY_SHOP_PRICES -> lang.gui("menu.permission_category.description.modify_shop_prices")

            // Diplomacy
            RankPermission.MANAGE_RELATIONS -> lang.gui("menu.permission_category.description.manage_relations")
            RankPermission.DECLARE_WAR -> lang.gui("menu.permission_category.description.declare_war")
            RankPermission.ACCEPT_ALLIANCES -> lang.gui("menu.permission_category.description.accept_alliances")
            RankPermission.MANAGE_PARTIES -> lang.gui("menu.permission_category.description.manage_parties")
            RankPermission.SEND_PARTY_REQUESTS -> lang.gui("menu.permission_category.description.send_party_requests")
            RankPermission.ACCEPT_PARTY_INVITES -> lang.gui("menu.permission_category.description.accept_party_invites")
            RankPermission.USE_ALLY_HOMES -> lang.gui("menu.permission_category.description.use_ally_homes")

            // Claims
            RankPermission.MANAGE_CLAIMS -> lang.gui("menu.permission_category.description.manage_claims")
            RankPermission.MANAGE_FLAGS -> lang.gui("menu.permission_category.description.manage_flags")
            RankPermission.MANAGE_PERMISSIONS -> lang.gui("menu.permission_category.description.manage_permissions")
            RankPermission.CREATE_CLAIMS -> lang.gui("menu.permission_category.description.create_claims")
            RankPermission.DELETE_CLAIMS -> lang.gui("menu.permission_category.description.delete_claims")

            // Communication
            RankPermission.SEND_ANNOUNCEMENTS -> lang.gui("menu.permission_category.description.send_announcements")
            RankPermission.SEND_PINGS -> lang.gui("menu.permission_category.description.send_pings")
            RankPermission.MODERATE_CHAT -> lang.gui("menu.permission_category.description.moderate_chat")

            // Administrative
            RankPermission.ACCESS_ADMIN_COMMANDS -> lang.gui("menu.permission_category.description.access_admin_commands")
            RankPermission.BYPASS_RESTRICTIONS -> lang.gui("menu.permission_category.description.bypass_restrictions")
            RankPermission.VIEW_AUDIT_LOGS -> lang.gui("menu.permission_category.description.view_audit_logs")
            RankPermission.MANAGE_INTEGRATIONS -> lang.gui("menu.permission_category.description.manage_integrations")
        }
    }

    private fun localizedCategoryName(): Component = when (categoryName) {
        "Guild Management" -> lang.gui("menu.rank_edit.category.guild_management")
        "Banking" -> lang.gui("menu.rank_edit.category.banking")
        "Diplomacy" -> lang.gui("menu.rank_edit.category.diplomacy")
        "Communication" -> lang.gui("menu.rank_edit.category.communication")
        "Administrative" -> lang.gui("menu.rank_edit.category.administrative")
        "Claims" -> lang.gui("menu.rank_edit.category.claims")
        else -> GuiTextStyler.style(Component.text(categoryName))
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Rank -> rank = data
        }
    }
}
