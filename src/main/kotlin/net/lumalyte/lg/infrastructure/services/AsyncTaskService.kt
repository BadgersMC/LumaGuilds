package net.lumalyte.lg.infrastructure.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/**
 * Runs blocking I/O on the virtual-thread dispatcher owned by the plugin's DI context.
 *
 * The injected scope is owned by Nexus after cutover; while the migration is in progress,
 * the legacy bootstrap supplies its existing plugin scope. No detached CoroutineScopes
 * or independent executor services are created by this class.
 */
class AsyncTaskService(
    private val scope: CoroutineScope,
    private val virtualDispatcher: CoroutineDispatcher,
    private val plugin: Plugin
) {
    /**
     * Run a blocking operation on the virtual-thread dispatcher.
     * Cancelling the owning plugin scope completes the future exceptionally,
     * including when cancellation happens before execution begins.
     */
    fun <T> runAsync(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val job = scope.launch(virtualDispatcher) {
            try {
                future.complete(block())
            } catch (cancelled: CancellationException) {
                future.cancel(true)
                throw cancelled
            } catch (error: Exception) {
                future.completeExceptionally(error)
            }
        }
        job.invokeOnCompletion { error ->
            if (error != null && !future.isDone) future.completeExceptionally(error)
        }
        return future
    }

    /** Run a blocking operation as a suspend function on the owned virtual dispatcher. */
    suspend fun <T> runSuspend(block: () -> T): T =
        withContext(virtualDispatcher) { block() }

    /**
     * Run blocking I/O and dispatch its callback to Bukkit's primary thread.
     * The owning plugin instance is provided directly (never looked up by name).
     */
    fun <T> runAsyncCallback(
        task: () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        scope.launch(virtualDispatcher) {
            try {
                val result = task()
                plugin.server.scheduler.runTask(plugin, Runnable { onSuccess(result) })
            } catch (error: Exception) {
                if (plugin.isEnabled) {
                    plugin.server.scheduler.runTask(plugin, Runnable { onError(error) })
                }
            }
        }
    }
}
