package net.lumalyte.lg.infrastructure.persistence.players

import net.lumalyte.lg.domain.entities.PlayerState
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import java.util.*
import java.util.logging.Logger

/**
 * Holds a collection of every player on the server.
 * WARNING: This is a memory-only implementation - player states are lost on server restart!
 */
class PlayerStateRepositoryMemory: PlayerStateRepository {
    companion object {
        private val logger = Logger.getLogger("PlayerStateRepositoryMemory")
    }

    private var playerStates: MutableMap<UUID, PlayerState> = mutableMapOf()

    init {
        logger.warning("[INIT] PlayerStateRepositoryMemory initialized - player states will NOT persist across server restarts!")
        logger.info("[INIT] This is a known issue that may cause visualization problems after restarts")
    }

    override fun getAll() : Set<PlayerState> {
        val result = playerStates.values.toSet()
        return result
    }

    override fun get(id: UUID) : PlayerState? {
        val result = playerStates[id]
        return result
    }

    override fun add(playerState: PlayerState): Boolean {
        val result = !playerStates.containsKey(playerState.playerId)
        if (result) {
            playerStates[playerState.playerId] = playerState
        }
        return result
    }

    override fun update(playerState: PlayerState): Boolean {
        val oldState = playerStates[playerState.playerId]
        val result = playerStates.containsKey(playerState.playerId)
        if (result) {
            playerStates[playerState.playerId] = playerState
        }

        if (!result) {
            logger.warning("[WARN] update() failed - PlayerState for ${playerState.playerId} does not exist")
        }

        return result
    }

    override fun remove(playerState: PlayerState): Boolean {
        val result = playerStates.remove(playerState.playerId) != null
        if (!result) {
            logger.warning("[WARN] remove() failed - PlayerState for ${playerState.playerId} not found")
        }
        return result
    }
}
