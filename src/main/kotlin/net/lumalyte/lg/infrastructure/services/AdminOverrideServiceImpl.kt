package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.AdminOverrideService
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of AdminOverrideService using in-memory storage.
 *
 * This service manages admin override state for players, allowing administrators
 * to bypass guild membership and permission restrictions during their session.
 *
 * Key characteristics:
 * - Thread-safe using ConcurrentHashMap
 * - In-memory only (no persistence)
 * - State automatically cleared on logout
 * - All state changes are logged for audit trail
 */
class AdminOverrideServiceImpl : AdminOverrideService {

    private val logger = LoggerFactory.getLogger(AdminOverrideServiceImpl::class.java)

    /**
     * Map storing override states for players.
     * Key: Player UUID
     * Value: Override state (true = enabled, false = disabled)
     *
     * ConcurrentHashMap ensures thread-safe access from multiple threads.
     */
    private val overrideStates = ConcurrentHashMap<UUID, Boolean>()

    override fun toggleOverride(playerId: UUID): Boolean {
        val currentState = hasOverride(playerId)
        val newState = !currentState

        if (newState) {
            enableOverride(playerId)
        } else {
            disableOverride(playerId)
        }

        return newState
    }

    override fun hasOverride(playerId: UUID): Boolean {
        return overrideStates.getOrDefault(playerId, false)
    }

    override fun enableOverride(playerId: UUID) {
        overrideStates[playerId] = true
        logger.info("Player {} enabled admin guild override", playerId)
    }

    override fun disableOverride(playerId: UUID) {
        overrideStates[playerId] = false
        logger.info("Player {} disabled admin guild override", playerId)
    }

    override fun clearOverride(playerId: UUID) {
        val wasEnabled = hasOverride(playerId)
        overrideStates.remove(playerId)

        if (wasEnabled) {
            logger.info("Player {} override state cleared (logout)", playerId)
        }
    }
}
