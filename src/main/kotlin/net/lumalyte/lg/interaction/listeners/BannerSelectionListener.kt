package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.interaction.menus.guild.GuildBannerMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Listener for handling banner placement in the Guild Banner Menu
 */
class BannerSelectionListener : Listener, KoinComponent {

    private val guildService: GuildService by inject()

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Verify this click is targeting the player's specific GuildBannerMenu inventory
        // instance, not some other chest a different plugin opened.
        val expectedInventory = GuildBannerMenu.activeMenus[player.uniqueId]
        if (expectedInventory == null || event.view.topInventory != expectedInventory) return

        // Only handle clicks in the top inventory (menu)
        if (event.clickedInventory?.type != InventoryType.CHEST) return

        val slot = event.slot

        // Check if clicked slot is the banner placement slot (position 2,1 in StaticPane)
        // In a 9-slot wide inventory: slot = y * 9 + x = 1 * 9 + 2 = 11
        if (slot != 11) return

        // Check if player is placing a banner
        val cursorItem = event.cursor
        val bannerItem = event.currentItem

        // Handle both manual placement (with cursor) and shift-click placement (direct to slot)
        val bannerToPlace = cursorItem ?: bannerItem

        // If slot contains the placeholder or is empty, and player is placing a banner
        val isPlaceholderSlot = bannerItem?.type == Material.LIGHT_GRAY_STAINED_GLASS_PANE
        val isEmptySlot = bannerItem?.type == Material.AIR
        val slotHasBanner = bannerItem != null && isBanner(bannerItem.type)

        if ((isPlaceholderSlot || isEmptySlot) && bannerToPlace != null && isBanner(bannerToPlace.type)) {
            event.isCancelled = true

            // Only allow placing exactly 1 banner (prevent stacks)
            val singleBanner = bannerToPlace.clone()
            singleBanner.amount = 1

            // Place the single banner in the slot
            event.currentItem = singleBanner

            // Remove 1 banner from cursor if that's where it came from
            if (cursorItem != null && cursorItem.type != Material.AIR) {
                if (cursorItem.amount > 1) {
                    cursorItem.amount = cursorItem.amount - 1
                    player.setItemOnCursor(cursorItem)
                } else {
                    player.setItemOnCursor(ItemStack.of(Material.AIR))
                }
            }

            player.sendMessage("§7📍 Banner placed! Click '§a⏳ APPLY CHANGES§7' to save.")
        }
        // If player is trying to take a banner from the slot, prevent it
        else if (slotHasBanner) {
            event.isCancelled = true
            player.sendMessage("§c❌ You cannot take banners from this slot! Use '§e📋 GET BANNER COPY§c' instead.")
        }
        // If player is trying to take the placeholder, prevent it
        else if (isPlaceholderSlot) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        // Verify this drag is targeting the player's specific GuildBannerMenu inventory
        val expectedInventory = GuildBannerMenu.activeMenus[player.uniqueId]
        if (expectedInventory == null || event.view.topInventory != expectedInventory) return

        val topInventory = event.view.topInventory
        val topSlots = event.rawSlots.filter { it < topInventory.size }

        // Drag doesn't touch the menu — ignore
        if (topSlots.isEmpty()) return

        // Block any drag touching multiple menu slots
        if (topSlots.size > 1) {
            event.isCancelled = true
            player.sendMessage("§c❌ Drag-clicking across multiple menu slots is disabled.")
            return
        }

        // Only allow single-slot drag into slot 11 with a banner
        val slot = topSlots.first()
        if (slot != 11) {
            event.isCancelled = true
            return
        }

        val draggedItem = event.oldCursor
        if (!isBanner(draggedItem.type)) {
            event.isCancelled = true
            return
        }

        val currentItem = topInventory.getItem(slot)
        val isPlaceholder = currentItem?.type == Material.LIGHT_GRAY_STAINED_GLASS_PANE
        val isEmpty = currentItem == null || currentItem.type == Material.AIR

        if (!isPlaceholder && !isEmpty) {
            event.isCancelled = true
            return
        }

        // Valid banner placement via drag — cancel vanilla behavior and handle manually
        event.isCancelled = true

        val singleBanner = draggedItem.clone()
        singleBanner.amount = 1
        topInventory.setItem(slot, singleBanner)

        // Remove 1 banner from cursor
        if (draggedItem.amount > 1) {
            val remaining = draggedItem.clone()
            remaining.amount = draggedItem.amount - 1
            player.setItemOnCursor(remaining)
        } else {
            player.setItemOnCursor(ItemStack.of(Material.AIR))
        }

        player.sendMessage("§7📍 Banner placed! Click '§a⏳ APPLY CHANGES§7' to save.")
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        GuildBannerMenu.activeMenus.remove(player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        GuildBannerMenu.activeMenus.remove(event.player.uniqueId)
    }

    private fun isBanner(material: Material): Boolean {
        return material.name.endsWith("_BANNER")
    }
}
