package net.badgersmc.nexus.coroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a plugin-scoped CoroutineScope with SupervisorJob.
 *
 * - SupervisorJob: failure in one child coroutine doesn't cancel siblings
 * - Virtual thread dispatcher: runs on virtual threads with classloader propagation
 * - CoroutineName: for debugging and logging
 */
internal fun createNexusScope(
    dispatchers: NexusDispatchers,
    contextName: String
): CoroutineScope {
    return CoroutineScope(
        SupervisorJob() +
        dispatchers.virtualThread +
        CoroutineName(contextName)
    )
}
