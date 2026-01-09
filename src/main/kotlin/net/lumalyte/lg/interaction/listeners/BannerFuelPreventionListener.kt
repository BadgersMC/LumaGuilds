package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.common.PluginKeys
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceBurnEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Prevents guild banners (marked with GUILD_BANNER_MARKER) from being used as furnace fuel.
 * This prevents players from getting infinite furnace fuel by obtaining free guild banner copies.
 */
class BannerFuelPreventionListener : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFurnaceBurn(event: FurnaceBurnEvent) {
        val fuel = event.fuel ?: return

        // Check if the fuel item is a banner
        if (!fuel.type.name.endsWith("_BANNER")) return

        // Check if it's marked as a guild banner
        val meta = fuel.itemMeta ?: return
        val isGuildBanner = meta.persistentDataContainer.has(
            PluginKeys.GUILD_BANNER_MARKER,
            PersistentDataType.BYTE
        )

        if (isGuildBanner) {
            // Cancel the event to prevent the banner from being used as fuel
            event.isCancelled = true
        }
    }
}
