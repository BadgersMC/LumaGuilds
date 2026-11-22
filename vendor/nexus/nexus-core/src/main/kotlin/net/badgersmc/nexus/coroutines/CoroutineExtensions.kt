package net.badgersmc.nexus.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Execute a suspending block on the IO dispatcher.
 * Use for blocking I/O operations (database queries, file I/O, network calls).
 */
suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO, block)
}

/**
 * Execute a suspending block on the Default dispatcher.
 * Use for CPU-intensive operations (parsing, computation).
 */
suspend fun <T> withDefault(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Default, block)
}
