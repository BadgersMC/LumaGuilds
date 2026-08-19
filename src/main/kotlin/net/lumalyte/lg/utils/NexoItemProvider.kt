package net.lumalyte.lg.utils

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/**
 * Provides ItemStacks from Nexo's custom item registry with a vanilla fallback.
 *
 * Uses the official Nexo API instead of reflection:
 *   - [NexoItems.exists] to check if an item ID is registered
 *   - [NexoItems.itemFromId] → [com.nexomc.nexo.items.ItemBuilder.build] for ItemStacks
 *
 * Nexo loads items asynchronously. Items are NOT available during plugin [onEnable] —
 * we listen for [NexoItemsLoadedEvent] and set a ready flag. Before the event fires,
 * all items fall back to vanilla.
 *
 * Unlike the old reflection-based approach, the compile-time dependency means:
 *   - No Class.forName / Method.invoke boilerplate
 *   - Type-safe calls to the real Nexo API
 *   - No silent failures from null receivers or wrong method signatures
 */
object NexoItemProvider : Listener {

    private val logger = LoggerFactory.getLogger(NexoItemProvider::class.java)

    /**
     * Whether Nexo has finished loading its custom items.
     * False during startup — all calls return fallbacks until this is true.
     */
    @Volatile
    var itemsLoaded: Boolean = false
        private set

    /**
     * Registers the [NexoItemsLoadedEvent] listener. Call once from the plugin's [onEnable].
     */
    fun register(plugin: Plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        logger.info("NexoItemProvider registered — waiting for NexoItemsLoadedEvent")
    }

    /**
     * True when Nexo is installed AND its items have finished loading.
     */
    fun isAvailable(): Boolean = itemsLoaded

    /**
     * Gets a Nexo custom item by its item ID (e.g. "lg_nav_info").
     *
     * @param itemId The Nexo item identifier.
     * @return The ItemStack from Nexo, or null if Nexo is unavailable or the ID is not found.
     */
    fun getItemStack(itemId: String): ItemStack? {
        if (!itemsLoaded) return null
        return try {
            NexoItems.itemFromId(itemId)?.build()
        } catch (e: Exception) {
            logger.debug("Nexo getItemStack failed for '$itemId': ${e.message}")
            null
        }
    }

    /**
     * Gets a Nexo item with a vanilla fallback.
     *
     * @param itemId  The Nexo item ID (e.g. "lg_nav_info").
     * @param fallback A lambda producing the fallback ItemStack when Nexo is unavailable.
     * @return The Nexo ItemStack if available, otherwise the fallback.
     */
    fun getItemStackOrFallback(itemId: String, fallback: () -> ItemStack): ItemStack {
        return getItemStack(itemId) ?: fallback()
    }

    // ─── event handler ────────────────────────────────────────────────

    @EventHandler
    fun onNexoItemsLoaded(event: NexoItemsLoadedEvent) {
        itemsLoaded = true
        logger.info("Nexo items loaded — NexoItemProvider is ready")
    }
}