package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PlaytimeActivityService
import org.bukkit.Bukkit
import org.enthusia.playtime.activity.ActivityState
import org.enthusia.playtime.api.PlaytimeService
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bridges EnthusiaPlaytime's live activity state into LumaGuilds so AFK / suspicious
 * players don't earn guild XP (mob-farm kills, block macros, automated movement).
 *
 * Degrades gracefully: if EnthusiaPlaytime is missing or hasn't registered its service
 * yet, nothing is ever blocked. The service is resolved through the Bukkit
 * ServicesManager on every call so a PlayTime `/reload` (which re-registers the
 * service) is picked up automatically, and `getLiveState` is a cheap in-memory lookup.
 */
class PlaytimeActivityServiceBukkit : PlaytimeActivityService {

    private val logger = LoggerFactory.getLogger(PlaytimeActivityServiceBukkit::class.java)

    override fun isXpBlocked(playerId: UUID): Boolean {
        val service = try {
            @Suppress("UNCHECKED_CAST")
            Bukkit.getServicesManager().load(PlaytimeService::class.java) as? PlaytimeService
        } catch (e: Throwable) {
            null // PlayTime absent — never block XP
        } ?: return false

        return try {
            isBlockedState(service.getLiveState(playerId))
        } catch (e: Exception) {
            logger.debug("Failed to read live activity state for {}: {}", playerId, e.message)
            false
        }
    }

    companion object {
        /**
         * Pure decision: only AFK and SUSPICIOUS are blocked — ACTIVE and IDLE still earn.
         * Unknown/unavailable state never blocks.
         */
        fun isBlockedState(state: ActivityState?): Boolean =
            state == ActivityState.AFK || state == ActivityState.SUSPICIOUS
    }
}
