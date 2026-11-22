package net.lumalyte.lg.di.nexus

import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.isActive
import net.badgersmc.nexus.core.NexusContext
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Borrow Nexus's managed virtual-thread pool for Java APIs that accept Executor.
 * This wrapper never owns or closes the pool; NexusContext alone shuts it down.
 *
 * A canceled parent scope rejects new work rather than allowing detached tasks to
 * outlive plugin shutdown.
 */
internal fun NexusContext.nexusVirtualExecutor(): Executor {
    val ownerScope = requireNotNull(scope) { "Nexus scope unavailable" }
    val underlying = requireNotNull(dispatchers) { "Nexus virtual dispatcher unavailable" }
        .virtualThread.asExecutor()
    return Executor { task ->
        if (!ownerScope.isActive) {
            throw RejectedExecutionException("Nexus scope is closed")
        }
        underlying.execute(task)
    }
}
