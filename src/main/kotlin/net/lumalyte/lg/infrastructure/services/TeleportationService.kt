package net.lumalyte.lg.infrastructure.services

import com.github.sirblobman.combatlogx.api.ICombatLogX
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import net.lumalyte.lg.utils.CombatUtil


/**
 * Centralized service for managing guild home teleportation countdowns.
 * Prevents conflicts between command-based and menu-based teleports.
 */
class TeleportationService(private val plugin: Plugin) {

    private val logger = LoggerFactory.getLogger(TeleportationService::class.java)

    private data class TeleportSession(
        val player: Player,
        val targetLocation: Location,
        val startLocation: Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5
    )

    private val activeTeleports = ConcurrentHashMap<UUID, TeleportSession>()

    /**
     * Start a teleportation countdown for a player.
     * Cancels any existing teleport for the player.
     */
    fun startTeleport(player: Player, targetLocation: Location): Boolean {
        val playerId = player.uniqueId

        // Cancel any existing teleport
        cancelTeleport(playerId)

        if (CombatUtil.isInCombat(player)){
            player.sendMessage("§e◷ Cannot teleport in combat.")
            return false
        }

        val session = TeleportSession(
            player = player,
            targetLocation = targetLocation,
            startLocation = player.location.clone(),
            remainingSeconds = 5
        )

        activeTeleports[playerId] = session

        player.sendMessage("§e◷ Teleportation countdown started! Don't move for 5 seconds...")
        player.sendActionBar(Component.text("§eTeleporting to guild home in §f5§e seconds..."))

        val countdownTask = object : BukkitRunnable() {
            override fun run() {
                val currentSession = activeTeleports[playerId]
                if (currentSession == null) {
                    cancel()
                    return
                }

                // Check if player moved
                if (hasPlayerMoved(currentSession)) {
                    cancelTeleport(playerId)
                    player.sendMessage("§c❌ Teleportation canceled - you moved!")
                    cancel()
                    return
                }

                currentSession.remainingSeconds--

                if (currentSession.remainingSeconds <= 0) {
                    // Teleport the player
                    player.teleport(currentSession.targetLocation)
                    player.sendMessage("§a✅ Welcome to your guild home!")
                    player.sendActionBar(Component.text("§aTeleported to guild home!"))

                    // Clean up
                    activeTeleports.remove(playerId)
                    cancel()
                } else {
                    // Update action bar
                    player.sendActionBar(Component.text("§eTeleporting to guild home in §f${currentSession.remainingSeconds}§e seconds..."))
                }
            }
        }

        session.countdownTask = countdownTask
        countdownTask.runTaskTimer(plugin, 0L, 20L) // Start immediately, then every second

        logger.debug("Started teleport countdown for player ${player.name}")
        return true
    }

    /**
     * Cancel any active teleport for a player.
     */
    fun cancelTeleport(playerId: UUID) {
        val session = activeTeleports.remove(playerId) ?: return
        session.countdownTask?.cancel()
        logger.debug("Cancelled teleport for player $playerId")
    }

    /**
     * Check if a player has an active teleport.
     */
    fun hasActiveTeleport(playerId: UUID): Boolean {
        return activeTeleports.containsKey(playerId)
    }

    /**
     * Handle player quit - cleanup their teleport tracking.
     */
    fun onPlayerQuit(playerId: UUID) {
        cancelTeleport(playerId)
    }

    /**
     * Check if player has moved from their starting location.
     */
    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val currentLocation = session.player.location
        val startLocation = session.startLocation

        // Check if player moved more than 0.1 blocks in any direction
        return Math.abs(currentLocation.x - startLocation.x) > 0.1 ||
               Math.abs(currentLocation.y - startLocation.y) > 0.1 ||
               Math.abs(currentLocation.z - startLocation.z) > 0.1 ||
               currentLocation.world != startLocation.world
    }
}
