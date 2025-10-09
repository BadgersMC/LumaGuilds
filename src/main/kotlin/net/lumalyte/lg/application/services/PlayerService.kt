package net.lumalyte.lg.application.services

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Stub implementation of PlayerService for Brigadier command migration.
 * TODO: Replace with full implementation when player system is ready.
 */
interface PlayerService {
    fun getPlayerById(playerId: UUID): Player?
    fun getPlayerName(playerId: UUID): String?
}

/**
 * Stub implementation that returns null/empty results.
 */
class PlayerServiceStub : PlayerService {
    override fun getPlayerById(playerId: UUID): Player? = null
    override fun getPlayerName(playerId: UUID): String? = null
}
