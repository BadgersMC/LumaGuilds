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

    /**
     * Mutable session state for an in-progress teleport countdown.
     *
     * `countdownTask` and `remainingSeconds` are mutated by the per-tick
     * countdown runnable; treating them as `var` inside a data class is
     * intentional and isolated to this private type.
     */
    @Suppress("MutableDataClassProperty")
    private data class TeleportSession(
        val player: Player,
        val targetLocation: Location,
        val startLocation: Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5,
        val onSuccess: (() -> Unit)? = null,
    )

    private val activeTeleports = ConcurrentHashMap<UUID, TeleportSession>()

    /** Whether the player currently has a countdown running. */
    fun hasActiveTeleport(playerId: UUID): Boolean = activeTeleports.containsKey(playerId)

    /** Seconds remaining on the player's active countdown, or `null` if none is running. */
    fun getRemainingSeconds(playerId: UUID): Int? = activeTeleports[playerId]?.remainingSeconds

    /** Cancel any active teleport countdown for the player. No-op if none is running. */
    fun cancelTeleport(playerId: UUID) {
        val session = activeTeleports.remove(playerId) ?: return
        session.countdownTask?.cancel()
    }

    /** Cleanup hook invoked from PlayerSessionListener when a player disconnects. */
    fun onPlayerQuit(playerId: UUID) = cancelTeleport(playerId)

    /**
     * Start a guild-home teleport countdown for [player] to [targetLocation].
     *
     * Behavior:
     * - Cancels any existing countdown for the player first.
     * - Refuses to start if the player is in combat (sends a message).
     * - Runs a 5-second countdown; movement greater than 0.1 blocks cancels it.
     * - On countdown completion, dismounts any vehicle/passengers, then calls
     *   [Player.teleportAsync] with [PlayerTeleportEvent.TeleportCause.PLUGIN].
     * - Logs a warning if `teleportAsync` returns `false` so admins can correlate
     *   with protection-plugin event-cancellation logs.
     *
     * Safe to call from any thread: re-dispatches to the main thread internally.
     *
     * @param onSuccess invoked on the main thread after a successful teleport
     *   (e.g. to stamp a per-player cooldown). Skipped on failure.
     */
    fun startTeleport(player: Player, targetLocation: Location, onSuccess: (() -> Unit)? = null) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                startTeleportInternal(player, targetLocation, onSuccess)
            })
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
            remainingSeconds = COUNTDOWN_SECONDS,
            onSuccess = onSuccess,
        )
        activeTeleports[playerId] = session

        player.sendMessage("§e◷ Teleportation countdown started! Don't move for $COUNTDOWN_SECONDS seconds...")
        player.sendActionBar(Component.text("§eTeleporting to guild home in §f$COUNTDOWN_SECONDS§e seconds..."))

        val task = CountdownTask(playerId)
        session.countdownTask = task
        task.runTaskTimer(plugin, 0L, TICKS_PER_SECOND)
    }

    /** Per-tick (1s) countdown runnable; movement check, decrement, then trigger teleport at 0. */
    private inner class CountdownTask(private val playerId: UUID) : BukkitRunnable() {
        override fun run() {
            val current = activeTeleports[playerId]
            if (current == null) {
                cancel()
                return
            }
            if (!current.player.isOnline) {
                cancelTeleport(playerId)
                cancel()
                return
            }

            if (hasPlayerMoved(current)) {
                cancelTeleport(playerId)
                current.player.sendMessage("§c❌ Teleportation canceled - you moved!")
                cancel()
                return
            }

            if (current.remainingSeconds <= 0) {
                performTeleport(current.player, current.targetLocation, current.onSuccess)
                activeTeleports.remove(playerId)
                cancel()
            } else {
                current.player.sendActionBar(
                    Component.text("§eTeleporting to guild home in §f${current.remainingSeconds}§e seconds..."),
                )
                current.remainingSeconds--
            }
        }
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
                    handleTeleportResult(player, targetLocation, success, throwable, onSuccess)
                })
            }
    }

    private fun handleTeleportResult(
        player: Player,
        targetLocation: Location,
        success: Boolean?,
        throwable: Throwable?,
        onSuccess: (() -> Unit)?,
    ) {
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
                val world = targetLocation.world?.name
                logger.warn(
                    "teleportAsync returned false for {} -> world={} x={} y={} z={} " +
                        "(likely cancelled by another plugin's PlayerTeleportEvent listener)",
                    player.name, world, targetLocation.x, targetLocation.y, targetLocation.z,
                )
                if (player.isOnline) player.sendMessage("§c❌ Teleport failed — please try again.")
            }
        }
    }

    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val cur = session.player.location
        val start = session.startLocation
        return Math.abs(cur.x - start.x) > MOVEMENT_TOLERANCE ||
            Math.abs(cur.y - start.y) > MOVEMENT_TOLERANCE ||
            Math.abs(cur.z - start.z) > MOVEMENT_TOLERANCE ||
            cur.world != start.world
    }

    companion object {
        private const val COUNTDOWN_SECONDS = 5
        private const val TICKS_PER_SECOND = 20L
        private const val MOVEMENT_TOLERANCE = 0.1
    }
}
