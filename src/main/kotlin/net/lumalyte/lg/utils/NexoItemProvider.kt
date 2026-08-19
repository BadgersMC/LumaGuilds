package net.lumalyte.lg.utils

import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory

/**
 * Provides ItemStacks from Nexo's custom item registry with a fallback chain.
 *
 * Uses reflection to avoid a hard compile-time dependency on Nexo —
 * mirrors the pattern established by [net.lumalyte.lg.infrastructure.services.NexoEmojiService].
 *
 * Supports Nexo 1.22.1 (NexoItems) and newer (NexoAPI) for forward compatibility.
 * When Nexo is absent, the fallback lambda is used (typically a vanilla ItemStack).
 */
object NexoItemProvider {

    private val logger = LoggerFactory.getLogger(NexoItemProvider::class.java)

    /** Cache of Nexo API classes discovered via reflection. */
    private var apiClass: Class<*>? = null
    private var apiMethod: java.lang.reflect.Method? = null

    /**
     * Returns true if the Nexo plugin is loaded and its API is accessible.
     */
    fun isAvailable(): Boolean {
        if (apiClass == null) {
            resolveApi()
        }
        return apiClass != null
    }

    /**
     * Attempts to resolve a Nexo API class and getItemStack method.
     * Tries NexoAPI first (newer versions), then NexoItems (1.22.x).
     */
    private fun resolveApi() {
        // Try NexoAPI (newer versions: 1.22.x+)
        for (className in listOf("com.nexomc.nexo.api.NexoAPI", "com.nexomc.nexo.api.NexoItems")) {
            try {
                val clazz = Class.forName(className)
                val methodName = if (className.contains("NexoAPI")) "getItemStack" else "getItemStack"
                val method = clazz.getMethod(methodName, String::class.java)
                apiClass = clazz
                apiMethod = method
                logger.debug("Nexo API resolved: {}.{}()", className, methodName)
                return
            } catch (_: ClassNotFoundException) {
                // try next class name
            } catch (_: NoSuchMethodException) {
                // try next class name
            }
        }
        logger.debug("Nexo API not available — running in compatibility mode")
    }

    /**
     * Attempts to get a Nexo custom item by its item ID (e.g. "lg_nav_info").
     *
     * @param itemId The Nexo item identifier (e.g. "lg_nav_info").
     * @return The ItemStack from Nexo, or null if Nexo is unavailable or the ID is not found.
     */
    fun getItemStack(itemId: String): ItemStack? {
        if (apiClass == null) resolveApi()
        val method = apiMethod ?: return null
        return try {
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