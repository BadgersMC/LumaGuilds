package net.lumalyte.lg.common

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

/**
 * Centralized storage for all NamespacedKey instances used by the plugin.
 * This prevents creating duplicate keys and eliminates the need for hardcoded
 * plugin name lookups with force-unwrap operators.
 *
 * Keys are lazily initialized when the plugin is enabled and reused throughout
 * the plugin lifecycle.
 */
object PluginKeys {
    private lateinit var pluginInstance: Plugin

    /**
     * Initialize the PluginKeys object with the plugin instance.
     * Must be called during plugin onEnable() before any keys are accessed.
     */
    fun initialize(plugin: Plugin) {
        pluginInstance = plugin
    }

    /**
     * Key used to store guild vault ID in persistent data containers.
     * Used by VaultProtectionListener and GuildCommand to identify vault chests.
     */
    val GUILD_VAULT_ID: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "guild_vault_id")
    }

    /**
     * Key used to mark items as guild banners in persistent data containers.
     * Used to prevent guild banners from being used as furnace fuel.
     */
    val GUILD_BANNER_MARKER: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "guild_banner")
    }

    /** Marker for deployable tactical war-banner items. */
    val WAR_BANNER_ITEM: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "war_banner_item")
    }

    /** Guild owner encoded on a deployable war-banner item or placed banner tile. */
    val WAR_BANNER_GUILD_ID: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "war_banner_guild_id")
    }

    /** Unique active war-banner deployment id stored on the placed banner tile. */
    val WAR_BANNER_ID: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "war_banner_id")
    }

    val SPAWN_BANNER_ITEM: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "spawn_banner_item")
    }

    val SPAWN_BANNER_ID: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "spawn_banner_id")
    }

    val SPAWN_BANNER_RANK: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "spawn_banner_rank")
    }

    val SPAWN_BANNER_CATEGORY: NamespacedKey by lazy {
        NamespacedKey(pluginInstance, "spawn_banner_category")
    }

    /**
     * Returns the plugin instance for use in scheduler tasks and event registration.
     */
    fun getPlugin(): Plugin {
        if (!::pluginInstance.isInitialized) {
            throw IllegalStateException("PluginKeys not initialized. Call initialize() in onEnable().")
        }
        return pluginInstance
    }
}
