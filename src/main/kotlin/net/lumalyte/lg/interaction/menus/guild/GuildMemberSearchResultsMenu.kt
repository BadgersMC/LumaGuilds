package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberSearchFilter
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Displays search results for member search queries.
 */
class GuildMemberSearchResultsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val results: List<Member>,
    private val searchFilter: MemberSearchFilter,
    private val messageService: MessageService
) : Menu {

    private lateinit var gui: ChestGui

    override fun open() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Search Results - ${results.size} found"))
        AntiDupeUtil.protect(gui)

        val pane = StaticPane(0, 0, 9, 6)

        results.take(45).forEachIndexed { index, member ->
            val row = index / 9
            val col = index % 9
            pane.addItem(createMemberItem(member), col, row)
        }

        // Back button
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Search").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildAdvancedMemberSearchMenu(menuNavigator, player, guild, messageService))
        }, 8, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun createMemberItem(member: Member): GuiItem {
        val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
        val item = ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(offlinePlayer.name ?: "Unknown").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("Click for details").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ))
            }
        }

        return GuiItem(item) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild, messageService))
        }
    }
}

