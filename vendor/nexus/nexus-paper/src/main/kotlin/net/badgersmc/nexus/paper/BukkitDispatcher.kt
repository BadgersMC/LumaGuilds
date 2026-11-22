package net.badgersmc.nexus.paper

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatcher that executes on the Paper main thread.
 * If already on the main thread, the coroutine framework skips dispatch entirely
 * (via [isDispatchNeeded]) — no scheduling overhead, no extra frame.
 * Use via: withContext(plugin.bukkitDispatcher) { /* Bukkit API here */ }
 */
class BukkitDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    /** Returns false when already on the main thread, letting the framework run inline. */
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Bukkit.isPrimaryThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        plugin.server.scheduler.runTask(plugin, block)
    }
}
