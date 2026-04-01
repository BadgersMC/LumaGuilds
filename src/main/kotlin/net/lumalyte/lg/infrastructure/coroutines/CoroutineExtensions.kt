package net.lumalyte.lg.infrastructure.coroutines

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Extension functions for Kotlin Coroutines.
 *
 * These utilities help with performing I/O and CPU-intensive operations
 * efficiently using coroutines.
 */

/**
 * Execute a suspending block on Dispatchers.IO (backed by virtual threads).
 *
 * This is the recommended way to perform blocking I/O operations (database queries, file I/O, etc.)
 * in coroutines. With Java 21 virtual threads, blocking is cheap!
 *
 * Example:
 * ```kotlin
 * suspend fun saveToDatabase(data: String) = withIO {
 *     database.executeUpdate("INSERT INTO table VALUES (?)", data)
 * }
 * ```
 */
suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO, block)
}

/**
 * Execute a suspending block on Dispatchers.Default.
 *
 * Use for CPU-intensive operations (parsing, computation, etc.).
 *
 * Example:
 * ```kotlin
 * suspend fun processLargeDataset(data: List<Int>) = withDefault {
 *     data.map { it * it }.sum()
 * }
 * ```
 */
suspend fun <T> withDefault(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Default, block)
}
