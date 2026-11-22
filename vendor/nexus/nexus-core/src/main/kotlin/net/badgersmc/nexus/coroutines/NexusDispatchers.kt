package net.badgersmc.nexus.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Provides coroutine dispatchers backed by Java 21 virtual threads
 * with proper classloader propagation for plugin environments.
 *
 * Each NexusContext creates its own NexusDispatchers instance to ensure
 * the correct plugin classloader is set on every virtual thread.
 */
class NexusDispatchers internal constructor(
    private val classLoader: ClassLoader,
    private val contextName: String
) {
    private val virtualThreadFactory = Thread.ofVirtual()
        .name("$contextName-vt-", 0)
        .factory()

    internal val executor: ExecutorService = Executors.newThreadPerTaskExecutor { task ->
        virtualThreadFactory.newThread {
            Thread.currentThread().contextClassLoader = classLoader
            task.run()
        }
    }

    /**
     * Coroutine dispatcher backed by virtual threads with classloader propagation.
     * This is the primary dispatcher for all plugin coroutines.
     */
    val virtualThread: CoroutineDispatcher = executor.asCoroutineDispatcher()

    internal fun shutdown() {
        executor.shutdown()
    }
}
