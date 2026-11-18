package net.lumalyte.lg.interaction.menus.bedrock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages form state persistence and timeout handling for multi-step workflows
 * Provides thread-safe state storage with automatic cleanup
 *
 * Periodic cleanup is a child job of a plugin-owned [CoroutineScope], started explicitly via
 * [start] and stopped via [shutdown]. No executor or thread is created by this object. Until
 * [start] is called, expired entries are still dropped lazily on [restoreState].
 *
 * Threading: storage is a [ConcurrentHashMap]; the cleanup sweep touches only that map and never
 * calls Bukkit APIs, so it is safe on any (non-main) dispatcher.
 */
object FormStateManager {

    // State storage with expiration tracking
    private val stateStorage = ConcurrentHashMap<String, StateEntry>()

    // Default state expiration time (30 minutes)
    internal const val DEFAULT_EXPIRATION_MINUTES = 30L

    // Initial delay and recurring cadence of the cleanup sweep (10 minutes)
    internal val CLEANUP_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)

    private const val SHUTDOWN_JOIN_TIMEOUT_MILLIS = 5_000L

    private val logger = Logger.getLogger(FormStateManager::class.java.name)

    private val lifecycleLock = Any()

    @Volatile
    private var cleanupJob: Job? = null

    /**
     * Starts the periodic cleanup as a child of [ownerScope] on [cleanupDispatcher].
     * Idempotent: returns false (and does nothing) while a cleanup child is already active.
     * The owner scope is never cancelled by this object.
     */
    fun start(ownerScope: CoroutineScope, cleanupDispatcher: CoroutineDispatcher): Boolean =
        start(ownerScope, cleanupDispatcher, CLEANUP_INTERVAL_MILLIS)

    /** Test seam: same as [start] with a configurable cadence. */
    internal fun start(
        ownerScope: CoroutineScope,
        cleanupDispatcher: CoroutineDispatcher,
        cleanupIntervalMillis: Long,
    ): Boolean = synchronized(lifecycleLock) {
        if (cleanupJob?.isActive == true) return@synchronized false
        cleanupJob = ownerScope.launch(cleanupDispatcher) {
            while (isActive) {
                delay(cleanupIntervalMillis)
                try {
                    cleanupExpiredStates()
                } catch (e: Exception) {
                    // Keep the sweep alive; a single failed pass must not stop future cleanup.
                    logger.log(Level.WARNING, "Bedrock form state cleanup pass failed", e)
                }
            }
        }
        true
    }

    /** Whether the cleanup child job is currently running (for lifecycle checks). */
    internal fun isCleanupActive(): Boolean = cleanupJob?.isActive == true

    /**
     * Data class to store state with expiration timestamp
     */
    private data class StateEntry(
        val state: Map<String, Any?>,
        val expiresAt: Long
    )

    /**
     * Saves form state with automatic expiration
     * @param key Unique identifier for the state
     * @param state Data to save
     * @param expirationMinutes Optional custom expiration time
     */
    fun saveState(key: String, state: Map<String, Any?>, expirationMinutes: Long = DEFAULT_EXPIRATION_MINUTES) {
        val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expirationMinutes)
        val entry = StateEntry(state, expiresAt)
        stateStorage[key] = entry
    }

    /**
     * Restores form state if it exists and hasn't expired
     * @param key Unique identifier for the state
     * @return The saved state or null if not found/expired
     */
    fun restoreState(key: String): Map<String, Any?>? {
        val entry = stateStorage[key] ?: return null

        // Check if expired
        if (System.currentTimeMillis() > entry.expiresAt) {
            stateStorage.remove(key)
            return null
        }

        return entry.state
    }

    /**
     * Clears saved state for a specific key
     * @param key Unique identifier for the state to clear
     */
    fun clearState(key: String) {
        stateStorage.remove(key)
    }

    /**
     * Gets all state keys for a player (for debugging/cleanup)
     * @param playerId Player UUID as string
     * @return List of state keys for the player
     */
    fun getPlayerStateKeys(playerId: String): List<String> {
        val prefix = "$playerId:"
        return stateStorage.keys.filter { key -> key.startsWith(prefix) }
    }

    /**
     * Clears all states for a specific player
     * @param playerId Player UUID as string
     */
    fun clearPlayerStates(playerId: String) {
        val playerKeys = getPlayerStateKeys(playerId)
        playerKeys.forEach { stateStorage.remove(it) }
    }

    /**
     * Gets the number of active states (for monitoring)
     * @return Current state count
     */
    fun getActiveStateCount(): Int {
        return stateStorage.size
    }

    /**
     * Cleanup expired states
     */
    internal fun cleanupExpiredStates() {
        val now = System.currentTimeMillis()
        val expiredKeys = stateStorage.entries.filter { (_, entry) ->
            now > entry.expiresAt
        }.map { it.key }

        expiredKeys.forEach { stateStorage.remove(it) }
    }

    /**
     * Enhanced state restoration with timeout handling
     * @param key State key
     * @param fallbackHandler Handler to call if state is not found or expired
     * @return Restored state or result of fallback handler
     */
    fun restoreStateWithFallback(key: String, fallbackHandler: () -> Map<String, Any?>): Map<String, Any?> {
        val state = restoreState(key)
        return if (state != null) {
            state
        } else {
            // State not found or expired - execute fallback
            val fallbackState = fallbackHandler()
            // Save the fallback state for future use
            saveState(key, fallbackState)
            fallbackState
        }
    }

    /**
     * Creates a state key with proper prefixing
     * @param playerId Player UUID
     * @param menuName Menu class name
     * @param stateName Specific state identifier
     * @return Formatted state key
     */
    fun createStateKey(playerId: String, menuName: String, stateName: String): String {
        return "$playerId:$menuName:$stateName"
    }

    /**
     * Updates existing state by merging with new data
     * @param key State key
     * @param newData New data to merge
     */
    fun updateState(key: String, newData: Map<String, Any?>) {
        val existingState = restoreState(key) ?: emptyMap()
        val updatedState = existingState + newData
        saveState(key, updatedState)
    }

    /**
     * Gets state metadata for debugging
     * @param key State key
     * @return Metadata about the state or null if not found
     */
    fun getStateMetadata(key: String): Map<String, Any>? {
        val entry = stateStorage[key] ?: return null
        val now = System.currentTimeMillis()
        return mapOf(
            "expiresInMinutes" to TimeUnit.MILLISECONDS.toMinutes(entry.expiresAt - now),
            "size" to entry.state.size,
            "keys" to entry.state.keys.toList()
        )
    }

    /**
     * Cancels and joins only the cleanup child job (never the owner scope) and clears all state.
     * Safe to call repeatedly or without a prior [start]; [start] may be called again afterwards.
     *
     * Blocks the caller (normally the Bukkit main thread in onDisable) for at most 5 seconds.
     * In practice the join is near-instant: the child only suspends in delay() and the sweep is
     * a short in-memory map scan.
     */
    fun shutdown() {
        val job = synchronized(lifecycleLock) {
            cleanupJob.also { cleanupJob = null }
        }
        if (job != null) {
            runBlocking {
                withTimeoutOrNull(SHUTDOWN_JOIN_TIMEOUT_MILLIS) { job.cancelAndJoin() }
            } ?: logger.warning("Bedrock form state cleanup did not stop within ${SHUTDOWN_JOIN_TIMEOUT_MILLIS}ms")
        }
        stateStorage.clear()
    }
}

/**
 * Extension functions for easier state management
 */
fun Map<String, Any?>.toFormState(): Map<String, Any?> = this

fun Map<String, Any?>.withTimeout(timeoutMinutes: Long): Pair<Map<String, Any?>, Long> = this to timeoutMinutes

/**
 * Utility for creating workflow steps
 */
data class WorkflowStep(
    val stepName: String,
    val data: Map<String, Any?>,
    val isComplete: Boolean = false
) {
    companion object {
        fun create(stepName: String, data: Map<String, Any?> = emptyMap()): WorkflowStep {
            return WorkflowStep(stepName, data, false)
        }

        fun complete(stepName: String, data: Map<String, Any?> = emptyMap()): WorkflowStep {
            return WorkflowStep(stepName, data, true)
        }
    }
}
