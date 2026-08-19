package net.lumalyte.lg.utils

import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory

/**
 * Provides ItemStacks from Nexo's custom item registry with a fallback chain.
 *
 * Uses reflection to avoid a hard compile-time dependency on Nexo — mirrors
 * the pattern established by [net.lumalyte.lg.infrastructure.services.NexoEmojiService].
 *
 * When Nexo is present, items render with the custom texture from the resource
 * pack. When absent, the fallback lambda is used (typically a vanilla ItemStack).
 */
object NexoItemProvider {

    private val logger = LoggerFactory.getLogger(NexoItemProvider::class.java)

    private var checked = false
    private var nexoApiClass: Class<*>? = null

    /**
     * Returns true if the Nexo plugin is loaded and its API is accessible.
     */
    fun isAvailable(): Boolean {
        if (!checked) {
            nexoApiClass = try {
                Class.forName("com.nexomc.nexo.api.NexoAPI")
            } catch (_: ClassNotFoundException) {
                null
            }
            checked = true
        }
        return nexoApiClass != null
    }

    /**
     * Attempts to get a Nexo custom item by its item ID (e.g. "lg_nav_info").
     *
     * @param itemId The Nexo item identifier (without the "lg_" prefix — pass the full ID).
     * @return The ItemStack from Nexo, or null if Nexo is unavailable or the ID is not found.
     */
    fun getItemStack(itemId: String): ItemStack? {
        val api = nexoApiClass ?: return null
        return try {
            val method = api.getMethod("getItemStack", String::class.java)
            method.invoke(null, itemId) as? ItemStack
        } catch (e: Exception) {
            logger.debug("Nexo getItemStack failed for '$itemId': ${e.message}")
            null
        }
    }

    /**
     * Gets a Nexo item with a vanilla fallback.
     *
     * @param itemId  The Nexo item ID (e.g. "lg_nav_info").
     * @param fallback A lambda that produces the fallback ItemStack when Nexo is absent.
     * @return The Nexo ItemStack if available, otherwise the fallback.
     */
    fun getItemStackOrFallback(itemId: String, fallback: () -> ItemStack): ItemStack {
        return getItemStack(itemId) ?: fallback()
    }
}