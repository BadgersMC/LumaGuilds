package net.badgersmc.nexus.paper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.bukkit.plugin.Plugin

/** Convenience accessor — creates a new BukkitDispatcher for this plugin. */
val Plugin.bukkitDispatcher: BukkitDispatcher get() = BukkitDispatcher(this)

/** Execute a suspending block on the main thread. */
suspend fun <T> Plugin.withBukkit(block: suspend CoroutineScope.() -> T): T =
    withContext(BukkitDispatcher(this), block)
