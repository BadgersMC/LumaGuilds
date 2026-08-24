package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
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
import java.util.UUID

class GuildRankManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                              private var guild: Guild): Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val ranksPerPage = 12 // 4 columns × 3 rows (rows 0-2)

    override fun open() {
        // Security check: Only players with MANAGE_RANKS permission can access this menu
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)
        if (!hasPermission) {
            player.sendMessage(lang.msg("menu.rank_management.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_management.feedback.required_permission"))
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(5, MenuTitleBuilder.build(
            guild.guiTheme,
            5,
            lang.guiTitle("menu.rank_management.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 5)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Get all ranks for the guild
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }

        // Calculate pagination bounds
        val totalPages = maxOf(1, (ranks.size + ranksPerPage - 1) / ranksPerPage)
        if (currentPage >= totalPages) {
            currentPage = maxOf(0, totalPages - 1)
        }

        val startIndex = currentPage * ranksPerPage
        val endIndex = minOf(startIndex + ranksPerPage, ranks.size)
        val pageRanks = ranks.subList(startIndex, endIndex)

        // Display ranks in 4-column grid spanning rows 0-2
        pageRanks.forEachIndexed { index, rank ->
            val row = index / 4
            val col = index % 4
            addRankButton(pane, rank, col, row)
        }

        // Navigation buttons at row 3
        addNavigationButtons(pane, totalPages, ranks.size)

        // Add new rank button
        val createRankItem = ItemStack.of(Material.EMERALD)
            .name(lang.gui("menu.rank_management.item.create.name"))
            .lore(lang.gui("menu.rank_management.item.create.lore.description"))
            .lore(lang.gui("menu.rank_management.item.create.lore.limit"))
        val guiCreateItem = GuiItem(createRankItem) {
            menuNavigator.openMenu(menuFactory.createRankCreationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiCreateItem, 4, 4)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_management.item.back.name"))
        val guiBackItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiBackItem, 8, 4)

        gui.show(player)
    }

    private fun addNavigationButtons(pane: StaticPane, totalPages: Int, totalRanks: Int) {
        // Previous page button
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_management.item.previous.name"))
            .lore(lang.gui("menu.rank_management.item.pagination.lore", "current" to currentPage + 1, "total" to totalPages))

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                open()
            }
        }
        pane.addItem(prevGuiItem, 0, 3)

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.rank_management.item.page.name", "current" to currentPage + 1, "total" to totalPages))
            .lore(lang.gui("menu.rank_management.item.page.lore", "count" to totalRanks))

        pane.addItem(GuiItem(pageItem), 4, 3)

        // Next page button
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_management.item.next.name"))
            .lore(lang.gui("menu.rank_management.item.pagination.lore", "current" to currentPage + 1, "total" to totalPages))

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                open()
            }
        }
        pane.addItem(nextGuiItem, 8, 3)
    }

    private fun addRankButton(pane: StaticPane, rank: Rank, x: Int, y: Int) {
        // Use rank's icon if available, otherwise default to DIAMOND_SWORD
        val iconMaterial = try {
            rank.icon?.let { Material.valueOf(it) } ?: Material.DIAMOND_SWORD
        } catch (e: IllegalArgumentException) {
            // If the stored icon name is invalid, fallback to default
            Material.DIAMOND_SWORD
        }

        val rankItem = ItemStack.of(iconMaterial)
            .name(lang.gui("menu.rank_management.item.rank.name", "rank" to rank.name))
            .lore(lang.gui("menu.rank_management.item.rank.lore.priority", "priority" to rank.priority))
            .lore(lang.gui("menu.rank_management.item.rank.lore.members", "count" to getMemberCount(rank.id)))
            .lore(lang.gui("menu.common.blank"))

        // Add formatted permissions with proper line breaks
        if (rank.permissions.isNotEmpty()) {
            rankItem.lore(lang.gui("menu.rank_management.item.rank.lore.permissions"))
            
            // Group permissions by category for better readability
            val permissionsByCategory = groupPermissionsByCategory(rank.permissions)
            
            permissionsByCategory.forEach { (category, perms) ->
                if (perms.isNotEmpty()) {
                    rankItem.lore(lang.gui("menu.rank_management.item.rank.lore.category", "category" to category))
                    perms.forEach { permission ->
                        val permissionKey = "permission.${permission.name.lowercase().replace("_", ".")}"
                        rankItem.lore(lang.gui(
                            "menu.rank_management.item.rank.lore.permission",
                            "permission" to lang.raw(permissionKey),
                        ))
                    }
                }
            }
        } else {
            rankItem.lore(lang.gui("menu.rank_management.item.rank.lore.none"))
            rankItem.lore(lang.gui("menu.rank_management.item.rank.lore.none_description"))
        }
        
        rankItem.lore(lang.gui("menu.common.blank"))
        rankItem.lore(lang.gui("menu.rank_management.item.rank.lore.action"))

        val guiItem = GuiItem(rankItem) {
            openRankEditMenu(rank)
        }
        pane.addItem(guiItem, x, y)
    }

    private fun groupPermissionsByCategory(permissions: Set<net.lumalyte.lg.domain.entities.RankPermission>): Map<String, List<net.lumalyte.lg.domain.entities.RankPermission>> {
        val config = configService.loadConfig()
        val claimsEnabled = config.claimsEnabled

        // Define claims permissions to filter out
        val claimsPermissions = setOf(
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_CLAIMS,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_FLAGS,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PERMISSIONS,
            net.lumalyte.lg.domain.entities.RankPermission.CREATE_CLAIMS,
            net.lumalyte.lg.domain.entities.RankPermission.DELETE_CLAIMS
        )

        // Filter out claims permissions if claims are disabled
        val filteredPermissions = if (!claimsEnabled) {
            permissions.filterNot { it in claimsPermissions }
        } else {
            permissions.toList()
        }

        return filteredPermissions.groupBy { permission ->
            when (permission) {
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_MEMBERS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_BANNER,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_EMOJI,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_DESCRIPTION,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_MODE,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS -> lang.raw("menu.rank_management.category.guild_management")

                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS,
                net.lumalyte.lg.domain.entities.RankPermission.DECLARE_WAR,
                net.lumalyte.lg.domain.entities.RankPermission.ACCEPT_ALLIANCES,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PARTIES,
                net.lumalyte.lg.domain.entities.RankPermission.SEND_PARTY_REQUESTS,
                net.lumalyte.lg.domain.entities.RankPermission.ACCEPT_PARTY_INVITES,
                net.lumalyte.lg.domain.entities.RankPermission.USE_ALLY_HOMES -> lang.raw("menu.rank_management.category.diplomacy")

                net.lumalyte.lg.domain.entities.RankPermission.DEPOSIT_TO_BANK,
                net.lumalyte.lg.domain.entities.RankPermission.WITHDRAW_FROM_BANK,
                net.lumalyte.lg.domain.entities.RankPermission.VIEW_BANK_TRANSACTIONS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_BANK_SETTINGS,
                net.lumalyte.lg.domain.entities.RankPermission.PLACE_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.ACCESS_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.DEPOSIT_TO_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.WITHDRAW_FROM_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.BREAK_VAULT,
                net.lumalyte.lg.domain.entities.RankPermission.ACCESS_SHOP_CHESTS,
                net.lumalyte.lg.domain.entities.RankPermission.EDIT_SHOP_STOCK,
                net.lumalyte.lg.domain.entities.RankPermission.MODIFY_SHOP_PRICES -> lang.raw("menu.rank_management.category.banking")

                net.lumalyte.lg.domain.entities.RankPermission.SEND_ANNOUNCEMENTS,
                net.lumalyte.lg.domain.entities.RankPermission.SEND_PINGS,
                net.lumalyte.lg.domain.entities.RankPermission.MODERATE_CHAT -> lang.raw("menu.rank_management.category.communication")

                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_CLAIMS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_FLAGS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PERMISSIONS,
                net.lumalyte.lg.domain.entities.RankPermission.CREATE_CLAIMS,
                net.lumalyte.lg.domain.entities.RankPermission.DELETE_CLAIMS -> lang.raw("menu.rank_management.category.claims")

                net.lumalyte.lg.domain.entities.RankPermission.ACCESS_ADMIN_COMMANDS,
                net.lumalyte.lg.domain.entities.RankPermission.BYPASS_RESTRICTIONS,
                net.lumalyte.lg.domain.entities.RankPermission.VIEW_AUDIT_LOGS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_INTEGRATIONS -> lang.raw("menu.rank_management.category.administrative")
            }
        }
    }

    private fun getMemberCount(rankId: UUID): Int {
        return memberService.getMembersByRank(guild.id, rankId).size
    }

    private fun openRankEditMenu(rank: Rank) {
        menuNavigator.openMenu(menuFactory.createRankEditMenu(menuNavigator, player, guild, rank))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

