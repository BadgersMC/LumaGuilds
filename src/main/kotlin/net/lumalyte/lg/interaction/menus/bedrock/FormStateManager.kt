package net.lumalyte.lg.interaction.menus.bedrock

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Manages form state persistence and timeout handling for multi-step workflows
 * Provides thread-safe state storage with automatic cleanup
 */
object FormStateManager {

    // State storage with expiration tracking
    private val stateStorage = ConcurrentHashMap<String, StateEntry>()

    // Executor for cleanup tasks
    private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FormState-Cleanup").apply { isDaemon = true }
    }

    // Default state expiration time (30 minutes)
    private const val DEFAULT_EXPIRATION_MINUTES = 30L

    init {
        // Schedule periodic cleanup every 10 minutes
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredStates,
            10,
            10,
            TimeUnit.MINUTES
        )
    }

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
    private fun cleanupExpiredStates() {
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
     * Shutdown method for proper cleanup
     */
    fun shutdown() {
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupExecutor.shutdownNow()
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
