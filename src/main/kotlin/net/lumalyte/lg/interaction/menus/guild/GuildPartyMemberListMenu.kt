package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildPartyMemberListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                              private val guild: Guild, private val party: Party, private val messageService: MessageService) : Menu, KoinComponent {

    override fun open() {
        val gui = ChestGui(5, "<green>Party Members - ${party.name ?: "Unnamed Party"}", )
        val pane = StaticPane(0, 0, 9, 5)

        // Add member list items
        addMemberList(pane)

        // Add navigation
        addNavigation(pane)

        gui.addPane(pane)
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }
        gui.show(player)
    }

    private fun addMemberList(pane: StaticPane) {
        // Placeholder implementation
        val placeholderItem = ItemStack(Material.PLAYER_HEAD)
        val meta = placeholderItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)!!
        meta.setDisplayName("<gray>Party Member")
        meta.lore = listOf("<dark_gray>Member information coming soon")
        placeholderItem.itemMeta = meta

        pane.addItem(GuiItem(placeholderItem), 4, 2)
    }

    private fun addNavigation(pane: StaticPane) {
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back")
        backMeta.lore = listOf("<gray>Return to party details")
        backItem.itemMeta = backMeta

        pane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 4)
    }
}
