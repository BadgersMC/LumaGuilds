package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.interaction.menus.guild.GuildBannerMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
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

        // Secure check: Use custom holder instead of deprecated title
        val holder = event.inventory?.holder
        if (holder !is GuildBannerMenu.BannerMenuHolder) return

        // Only handle clicks in the top inventory (menu)
        if (event.clickedInventory?.type != InventoryType.CHEST) return

        val slot = event.slot

        // Check if clicked slot is the banner placement slot (position 0,0 in StaticPane)
        // In a 9-slot wide inventory: slot = y * 9 + x = 0 * 9 + 0 = 0
        if (slot != 0) return

        // Check if player is placing a banner
        val cursorItem = event.cursor
        val bannerItem = event.currentItem

        // Handle both manual placement (with cursor) and shift-click placement (direct to slot)
        val bannerToPlace = cursorItem ?: bannerItem

        // If slot contains the placeholder or is empty, and player is placing a banner
        val isPlaceholderSlot = bannerItem?.type == Material.LIGHT_GRAY_STAINED_GLASS_PANE
        val isEmptySlot = bannerItem?.type == Material.AIR

        if ((isPlaceholderSlot || isEmptySlot) && bannerToPlace != null && isBanner(bannerToPlace.type)) {
            event.isCancelled = true

            // Get guild information from secure holder
            val guildName = holder.guildName
            val playerGuilds = guildService.getPlayerGuilds(player.uniqueId)
            val guild = playerGuilds.firstOrNull { it.name == guildName } ?: return

            // Set the banner (pass the entire ItemStack to preserve patterns)
            val success = guildService.setBanner(guild.id, bannerToPlace, player.uniqueId)

            if (success) {
                player.sendMessage("§a✅ Guild banner set to ${bannerToPlace.type.name.lowercase().replace("_", " ")}!")

                // Handle item consumption based on placement method
                if (cursorItem != null) {
                    // Manual placement: consume from cursor
                    if (cursorItem.amount > 1) {
                        cursorItem.amount = cursorItem.amount - 1
                        player.setItemOnCursor(cursorItem)
                    } else {
                        player.setItemOnCursor(null)
                    }
                } else if (bannerItem != null && bannerItem.type.name.endsWith("_BANNER")) {
                    // Shift-click placement: consume from slot
                    if (bannerItem.amount > 1) {
                        bannerItem.amount = bannerItem.amount - 1
                        event.currentItem = bannerItem
                    } else {
                        event.currentItem = ItemStack(Material.AIR)
                    }
                }

                // Close and reopen menu to refresh
                player.closeInventory()
                // Note: Menu will be reopened by the menu system if needed
            } else {
                player.sendMessage("§c❌ Failed to set banner. Check permissions.")
            }
        }
        // If player is trying to take something from the slot, prevent it
        else if (bannerItem?.type != Material.AIR && bannerItem?.type != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
            event.isCancelled = true
        }
    }

    private fun isBanner(material: Material): Boolean {
        return material.name.endsWith("_BANNER")
    }
}
