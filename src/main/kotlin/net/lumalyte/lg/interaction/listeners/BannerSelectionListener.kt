package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.interaction.menus.guild.GuildBannerMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
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

        // Identify the banner menu via the per-player tracker — InventoryFramework owns
        // the inventory holder, so a custom InventoryHolder cannot be attached.
        if (!GuildBannerMenu.activeViewers.contains(player.uniqueId)) return

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
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        GuildBannerMenu.activeViewers.remove(player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        GuildBannerMenu.activeViewers.remove(event.player.uniqueId)
    }

    private fun isBanner(material: Material): Boolean {
        return material.name.endsWith("_BANNER")
    }
}
