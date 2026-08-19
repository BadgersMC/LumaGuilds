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

    /** Cache of Nexo API info discovered via reflection. */
    private data class ApiInfo(
        val clazz: Class<*>,
        val instance: Any?,   // null for static methods, INSTANCE for Kotlin objects
        val method: java.lang.reflect.Method
    )

    private var api: ApiInfo? = null

    /**
     * Returns true if the Nexo plugin is loaded and its API is accessible.
     */
    fun isAvailable(): Boolean {
        if (api == null) {
            resolveApi()
        }
        return api != null
    }

    /**
     * Attempts to resolve a Nexo API class and getItemStack method.
     * Tries NexoAPI first (newer versions), then NexoItems (1.22.x).
     *
     * For Kotlin object classes (NexoItems), the method is an instance method
     * on the singleton (accessed via the `INSTANCE` field), NOT a static method.
     * We resolve the INSTANCE once and reuse it on every call.
     */
    private fun resolveApi() {
        val candidates = listOf(
            // Newer Nexo versions: com.nexomc.nexo.api.NexoAPI (static getItemStack)
            ApiCandidate("com.nexomc.nexo.api.NexoAPI", isKotlinObject = false),
            // Nexo 1.22.x: com.nexomc.nexo.api.NexoItems (Kotlin object, instance method)
            ApiCandidate("com.nexomc.nexo.api.NexoItems", isKotlinObject = true),
        )

        for (candidate in candidates) {
            try {
                val clazz = Class.forName(candidate.className)
                val method = clazz.getMethod("getItemStack", String::class.java)

                val instance = if (candidate.isKotlinObject) {
                    // Kotlin object — method is instance-level on the singleton
                    clazz.getField("INSTANCE").get(null)
                } else {
                    null // static method
                }

                api = ApiInfo(clazz, instance, method)
                logger.info("Nexo API resolved: {}.getItemStack() (receiver: {})",
                    candidate.className, if (instance != null) "INSTANCE" else "static")
                return
            } catch (_: ClassNotFoundException) {
                logger.info("Nexo class {} not found", candidate.className)
            } catch (_: NoSuchMethodException) {
                logger.info("Nexo class {} found but getItemStack method missing", candidate.className)
            } catch (_: NoSuchFieldException) {
                logger.info("Nexo class {} found but no INSTANCE field (not a Kotlin object?)", candidate.className)
            }
        }
        logger.info("Nexo API not available — running in compatibility mode")
    }

    /**
     * Attempts to get a Nexo custom item by its item ID (e.g. "lg_nav_info").
     *
     * @param itemId The Nexo item identifier (e.g. "lg_nav_info").
     * @return The ItemStack from Nexo, or null if Nexo is unavailable or the ID is not found.
     */
    fun getItemStack(itemId: String): ItemStack? {
        val info = api ?: return null
        return try {
            info.method.invoke(info.instance, itemId) as? ItemStack
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

    private data class ApiCandidate(
        val className: String,
        val isKotlinObject: Boolean
    )
}