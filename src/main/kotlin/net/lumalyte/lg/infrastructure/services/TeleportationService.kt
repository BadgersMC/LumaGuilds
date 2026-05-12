package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.lumalyte.lg.utils.CombatUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized service for guild home teleportation countdowns.
 * Single source of truth for command, Java menu, and Bedrock menu surfaces.
 */
class TeleportationService(private val plugin: Plugin) {

    private val logger = LoggerFactory.getLogger(TeleportationService::class.java)

    private data class TeleportSession(
        val player: Player,
        val targetLocation: Location,
        val startLocation: Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5,
        val onSuccess: (() -> Unit)? = null
    )

    private val activeTeleports = ConcurrentHashMap<UUID, TeleportSession>()

    fun hasActiveTeleport(playerId: UUID): Boolean = activeTeleports.containsKey(playerId)

    fun getRemainingSeconds(playerId: UUID): Int? = activeTeleports[playerId]?.remainingSeconds

    fun cancelTeleport(playerId: UUID) {
        val session = activeTeleports.remove(playerId) ?: return
        session.countdownTask?.cancel()
    }

    fun onPlayerQuit(playerId: UUID) = cancelTeleport(playerId)

    /**
     * Start a guild-home teleport countdown. Safe to call from any thread —
     * re-dispatches to the main thread if needed.
     *
     * @param onSuccess called on main thread after a successful teleport (e.g. to record cooldown).
     */
    fun startTeleport(player: Player, targetLocation: Location, onSuccess: (() -> Unit)? = null) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, Runnable { startTeleportInternal(player, targetLocation, onSuccess) })
        } else {
            startTeleportInternal(player, targetLocation, onSuccess)
        }
    }

    private fun startTeleportInternal(player: Player, targetLocation: Location, onSuccess: (() -> Unit)?) {
        val playerId = player.uniqueId
        cancelTeleport(playerId)

        if (CombatUtil.isInCombat(player)) {
            player.sendMessage("§e◷ Cannot teleport in combat.")
            return
        }

        val session = TeleportSession(
            player = player,
            targetLocation = targetLocation,
            startLocation = player.location.clone(),
            remainingSeconds = 5,
            onSuccess = onSuccess
        )
        activeTeleports[playerId] = session

        player.sendMessage("§e◷ Teleportation countdown started! Don't move for 5 seconds...")
        player.sendActionBar(Component.text("§eTeleporting to guild home in §f5§e seconds..."))

        val task = object : BukkitRunnable() {
            override fun run() {
                val current = activeTeleports[playerId]
                if (current == null) { cancel(); return }
                if (!player.isOnline) { cancelTeleport(playerId); cancel(); return }

                if (hasPlayerMoved(current)) {
                    cancelTeleport(playerId)
                    player.sendMessage("§c❌ Teleportation canceled - you moved!")
                    cancel()
                    return
                }

                if (current.remainingSeconds <= 0) {
                    performTeleport(player, current.targetLocation, current.onSuccess)
                    activeTeleports.remove(playerId)
                    cancel()
                } else {
                    player.sendActionBar(Component.text("§eTeleporting to guild home in §f${current.remainingSeconds}§e seconds..."))
                    current.remainingSeconds--
                }
            }
        }
        session.countdownTask = task
        task.runTaskTimer(plugin, 0L, 20L)
    }

    private fun performTeleport(player: Player, targetLocation: Location, onSuccess: (() -> Unit)?) {
        // Eject any vehicle/passengers — teleportAsync rejects entities with passengers
        // and protection plugins often cancel mounted teleports.
        player.vehicle?.let { player.leaveVehicle() }
        if (player.passengers.isNotEmpty()) {
            player.passengers.toList().forEach { player.removePassenger(it) }
        }

        // Pass TeleportCause.PLUGIN so region/protection plugins see a known cause
        // (default UNKNOWN is rejected by many protection plugins, causing silent
        // PlayerTeleportEvent cancellation -> CompletableFuture(false)).
        player.teleportAsync(targetLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete { success, throwable ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    when {
                        throwable != null -> {
                            logger.warn("Teleport threw for ${player.name} -> $targetLocation", throwable)
                            if (player.isOnline) player.sendMessage("§c❌ Teleport failed — an error occurred.")
                        }
                        success == true -> {
                            if (player.isOnline) {
                                player.sendMessage("§a✅ Welcome to your guild home!")
                                player.sendActionBar(Component.text("§aTeleported to guild home!"))
                            }
                            onSuccess?.invoke()
                        }
                        else -> {
                            logger.warn(
                                "teleportAsync returned false for ${player.name} -> world=${targetLocation.world?.name} " +
                                    "x=${targetLocation.x} y=${targetLocation.y} z=${targetLocation.z} " +
                                    "(likely cancelled by another plugin's PlayerTeleportEvent listener)"
                            )
                            if (player.isOnline) player.sendMessage("§c❌ Teleport failed — please try again.")
                        }
                    }
                })
            }
    }

    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val cur = session.player.location
        val start = session.startLocation
        return Math.abs(cur.x - start.x) > 0.1 ||
            Math.abs(cur.y - start.y) > 0.1 ||
            Math.abs(cur.z - start.z) > 0.1 ||
            cur.world != start.world
    }
}
