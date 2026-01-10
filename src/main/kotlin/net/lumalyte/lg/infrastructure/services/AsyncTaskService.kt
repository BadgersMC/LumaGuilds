package net.lumalyte.lg.infrastructure.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.concurrent.CompletableFuture

/**
 * Service for running blocking I/O operations on virtual threads.
 *
 * Use this for:
 * - Network I/O (HTTP requests, webhooks)
 * - File I/O (backups, exports)
 * - Any blocking operation that would waste a platform thread
 *
 * Virtual threads make these operations essentially free - they park
 * instead of blocking when waiting for I/O.
 */
class AsyncTaskService : KoinComponent {

    private val virtualDispatcher: CoroutineDispatcher by inject(named("VirtualDispatcher"))

    /**
     * Run a blocking I/O operation on a virtual thread.
     * Returns a CompletableFuture for Java interop.
     *
     * Example:
     * ```kotlin
     * asyncTaskService.runAsync {
     *     httpClient.send(request) // Blocking HTTP call
     * }.thenAccept { result ->
     *     // Handle result on main thread
     * }
     * ```
     */
    fun <T> runAsync(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()

        // Execute on virtual thread via dispatcher
        CoroutineScope(virtualDispatcher).launch {
            try {
                val result = block()
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Run a blocking I/O operation on a virtual thread (suspend version).
     *
     * Example:
     * ```kotlin
     * suspend fun sendWebhook() {
     *     val result = asyncTaskService.runSuspend {
     *         httpClient.send(request)
     *     }
     * }
     * ```
     */
    suspend fun <T> runSuspend(block: () -> T): T {
        return withContext(virtualDispatcher) {
            block()
        }
    }

    /**
     * Run a blocking I/O operation on a virtual thread with callback.
     * Useful when you can't use CompletableFuture or coroutines.
     *
     * Example:
     * ```kotlin
     * asyncTaskService.runAsyncCallback(
     *     task = { httpClient.send(request) },
     *     onSuccess = { result -> println("Success: $result") },
     *     onError = { error -> println("Error: ${error.message}") }
     * )
     * ```
     */
    fun <T> runAsyncCallback(
        task: () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        CoroutineScope(virtualDispatcher).launch {
            try {
                val result = task()
                // Callback on main thread for safety
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds")!!,
                    Runnable { onSuccess(result) }
                )
            } catch (e: Exception) {
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds")!!,
                    Runnable { onError(e) }
                )
            }
        }
    }
}
