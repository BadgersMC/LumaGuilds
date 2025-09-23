package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
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
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(4, "§6Rank Management - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Get all ranks for the guild
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }

        // Display existing ranks (up to 8 ranks in the grid)
        ranks.take(8).forEachIndexed { index, rank ->
            val row = index / 4
            val col = index % 4
            addRankButton(pane, rank, col, row)
        }

        // Add new rank button
        val createRankItem = ItemStack(Material.EMERALD)
            .name("§aCreate New Rank")
            .lore("§7Add a new rank to your guild")
            .lore("§7Maximum 10 ranks per guild")
        val guiCreateItem = GuiItem(createRankItem) {
            menuNavigator.openMenu(menuFactory.createRankCreationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiCreateItem, 4, 3)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§7← Back to Control Panel")
        val guiBackItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiBackItem, 8, 3)

        gui.show(player)
    }

    private fun addRankButton(pane: StaticPane, rank: Rank, x: Int, y: Int) {
        // Use rank's icon if available, otherwise default to DIAMOND_SWORD
        val iconMaterial = try {
            rank.icon?.let { Material.valueOf(it) } ?: Material.DIAMOND_SWORD
        } catch (e: IllegalArgumentException) {
            // If the stored icon name is invalid, fallback to default
            Material.DIAMOND_SWORD
        }

        val rankItem = ItemStack(iconMaterial)
            .name("§6${rank.name}")
            .lore("§7Priority: §f${rank.priority}")
            .lore("§7Members: §f${getMemberCount(rank.id)} players")
            .lore("§7")

        // Add formatted permissions with proper line breaks
        if (rank.permissions.isNotEmpty()) {
            rankItem.lore("§e⚙️ Permissions:")
            
            // Group permissions by category for better readability
            val permissionsByCategory = groupPermissionsByCategory(rank.permissions)
            
            permissionsByCategory.forEach { (category, perms) ->
                if (perms.isNotEmpty()) {
                    rankItem.lore("§7▶ §f$category:")
                    perms.forEach { permission ->
                        val displayName = permission.name.replace("_", " ").lowercase()
                            .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        rankItem.lore("§7  • §a$displayName")
                    }
                }
            }
        } else {
            rankItem.lore("§c❌ No permissions assigned")
            rankItem.lore("§7This rank cannot perform any actions")
        }
        
        rankItem.lore("§7")
        rankItem.lore("§eClick to edit this rank")

        val guiItem = GuiItem(rankItem) {
            openRankEditMenu(rank)
        }
        pane.addItem(guiItem, x, y)
    }

    private fun groupPermissionsByCategory(permissions: Set<net.lumalyte.lg.domain.entities.RankPermission>): Map<String, List<net.lumalyte.lg.domain.entities.RankPermission>> {
        return permissions.groupBy { permission ->
            when (permission) {
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_MEMBERS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_BANNER,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_EMOJI,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_DESCRIPTION,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_MODE,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS -> "Guild Management"
                
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS,
                net.lumalyte.lg.domain.entities.RankPermission.DECLARE_WAR,
                net.lumalyte.lg.domain.entities.RankPermission.ACCEPT_ALLIANCES,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PARTIES,
                net.lumalyte.lg.domain.entities.RankPermission.SEND_PARTY_REQUESTS,
                net.lumalyte.lg.domain.entities.RankPermission.ACCEPT_PARTY_INVITES -> "Diplomacy"
                
                net.lumalyte.lg.domain.entities.RankPermission.DEPOSIT_TO_BANK,
                net.lumalyte.lg.domain.entities.RankPermission.WITHDRAW_FROM_BANK,
                net.lumalyte.lg.domain.entities.RankPermission.VIEW_BANK_TRANSACTIONS,
                net.lumalyte.lg.domain.entities.RankPermission.EXPORT_BANK_DATA,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_BANK_SETTINGS -> "Banking"
                
                net.lumalyte.lg.domain.entities.RankPermission.SEND_ANNOUNCEMENTS,
                net.lumalyte.lg.domain.entities.RankPermission.SEND_PINGS,
                net.lumalyte.lg.domain.entities.RankPermission.MODERATE_CHAT -> "Communication"
                
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_CLAIMS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_FLAGS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_PERMISSIONS,
                net.lumalyte.lg.domain.entities.RankPermission.CREATE_CLAIMS,
                net.lumalyte.lg.domain.entities.RankPermission.DELETE_CLAIMS -> "Claims"
                
                net.lumalyte.lg.domain.entities.RankPermission.ACCESS_ADMIN_COMMANDS,
                net.lumalyte.lg.domain.entities.RankPermission.BYPASS_RESTRICTIONS,
                net.lumalyte.lg.domain.entities.RankPermission.VIEW_AUDIT_LOGS,
                net.lumalyte.lg.domain.entities.RankPermission.MANAGE_INTEGRATIONS -> "Administrative"
            }
        }
    }

    private fun getMemberCount(rankId: UUID): Int {
        // TODO: Get actual member count from MemberService
        return 0
    }

    private fun openRankEditMenu(rank: Rank) {
        menuNavigator.openMenu(menuFactory.createRankEditMenu(menuNavigator, player, guild, rank))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

