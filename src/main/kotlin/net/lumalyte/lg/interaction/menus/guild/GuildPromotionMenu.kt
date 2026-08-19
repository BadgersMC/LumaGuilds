package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.bukkit.plugin.Plugin

class GuildPromotionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val plugin: Plugin by inject()

    override fun open() {
        // Check permission first
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)
        if (!hasPermission) {
            player.sendMessage("§c❌ You don't have permission to manage ranks!")
            player.sendMessage("§7Required permission: §fMANAGE_RANKS")
            menuNavigator.goBack()
            return
        }

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankById = ranks.associateBy { it.id }
        val members = memberService.getGuildMembers(guild.id).sortedBy { rankById[it.rankId]?.priority ?: Int.MAX_VALUE }

        if (members.isEmpty()) {
            player.sendMessage("§cNo members in this guild.")
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, "§6Members — ${guild.name}")
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }

        // Paginated member grid (9x5 = 45 per page) + static nav row
        val paginatedPane = PaginatedPane(0, 0, 9, 5)
        val staticPane = StaticPane(0, 5, 9, 1)

        val memberItems = members.map { member ->
            val rank = rankById[member.rankId]
            val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: member.playerId.toString().take(8)
            val isOnline = Bukkit.getPlayer(member.playerId)?.isOnline == true

            val item = ItemStack.of(if (isOnline) Material.PLAYER_HEAD else Material.SKELETON_SKULL)
                .name("§e${playerName}")
                .lore("§7Rank: §f${rank?.name ?: "Unknown"}")
                .lore("§7Status: ${if (isOnline) "§aOnline" else "§7Offline"}")
                .lore("")
                .lore("§7Left-click to promote")
                .lore("§7Right-click to demote")

            GuiItem(item) {
                val rankIdx = ranks.indexOf(rank)
                if (it.click == ClickType.LEFT) {
                    // Promote
                    if (rankIdx >= 0 && rankIdx > 0) {
                        val success = memberService.promoteMember(member.playerId, guild.id, player.uniqueId)
                        if (success) {
                            // Fetch the exact rank the service assigned
                            val updatedMember = memberService.getMember(member.playerId, guild.id)
                            val newRankName = updatedMember?.let { m -> rankById[m.rankId]?.name } ?: ranks[rankIdx - 1].name
                            player.sendMessage("§a§f$playerName §apromoted to §f$newRankName§a.")
                            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                            reloadSafely()
                        } else {
                            player.sendMessage("§cFailed to promote $playerName.")
                        }
                    } else {
                        player.sendMessage("§c$playerName is already at the highest rank.")
                    }
                } else if (it.click == ClickType.RIGHT) {
                    // Demote
                    if (rankIdx >= 0 && rankIdx < ranks.size - 1) {
                        val success = memberService.demoteMember(member.playerId, guild.id, player.uniqueId)
                        if (success) {
                            // Fetch the exact rank the service assigned
                            val updatedMember = memberService.getMember(member.playerId, guild.id)
                            val newRankName = updatedMember?.let { m -> rankById[m.rankId]?.name } ?: ranks[rankIdx + 1].name
                            player.sendMessage("§e§f$playerName §edemoted to §f$newRankName§e.")
                            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                            reloadSafely()
                        } else {
                            player.sendMessage("§cFailed to demote $playerName.")
                        }
                    } else {
                        player.sendMessage("§c$playerName is already at the lowest rank.")
                    }
                }
            }
        }

        paginatedPane.populateWithGuiItems(memberItems)

        // Navigation row (y=5)
        if (paginatedPane.pages > 1) {
            // Previous page
            val prevItem = ItemStack.of(Material.ARROW)
                .name("§e⬅ Previous Page")
                .lore("§7Page ${paginatedPane.page + 1} of ${paginatedPane.pages}")
            staticPane.addItem(GuiItem(prevItem) {
                if (paginatedPane.page > 0) {
                    paginatedPane.page--
                    gui.update()
                }
            }, 0, 0)

            // Next page
            val nextItem = ItemStack.of(Material.ARROW)
                .name("§eNext Page ➡")
                .lore("§7Page ${paginatedPane.page + 1} of ${paginatedPane.pages}")
            staticPane.addItem(GuiItem(nextItem) {
                if (paginatedPane.page < paginatedPane.pages - 1) {
                    paginatedPane.page++
                    gui.update()
                }
            }, 8, 0)
        }

        // Member count display
        val infoItem = ItemStack.of(Material.PLAYER_HEAD)
            .name("§6Total Members: §f${members.size}")
            .lore("§7Guild: §f${guild.name}")
        staticPane.addItem(GuiItem(infoItem) { it.isCancelled = true }, 4, 0)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name("§e⬅ BACK")
            .lore("§7Return to guild settings")
        staticPane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, 7, 0)

        gui.addPane(paginatedPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    /**
     * Reopens the menu on the next tick to avoid desyncing the cursor
     * during InventoryClickEvent dispatch.
     */
    private fun reloadSafely() {
        Bukkit.getScheduler().runTask(plugin, Runnable { open() })
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}