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
     * Plain class (not data class): `countdownTask` and `remainingSeconds`
     * are mutated by the per-tick runnable, which would violate the
     * `MutableDataClassProperty` rule if expressed as a data class.
     */
    private class TeleportSession(
        val player: Player,
        val targetLocation: Location,
        val startLocation: Location,
        val onSuccess: (() -> Unit)? = null,
    ) {
        var countdownTask: BukkitRunnable? = null
        var remainingSeconds: Int = COUNTDOWN_SECONDS
    }

    /** Immutable bundle of teleport callsite info passed to result handlers. */
    private class TeleportContext(
        val player: Player,
        val targetLocation: Location,
        val onSuccess: (() -> Unit)?,
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
     * Start a guild-home teleport countdown.
     *
     * Behavior:
     * - Cancels any existing countdown for the player first.
     * - Refuses to start if the player is in combat (sends a message).
     * - Runs a 5-second countdown; movement greater than 0.1 blocks cancels it.
     * - On completion, dismounts any vehicle/passengers, then calls
     *   [Player.teleportAsync] with [PlayerTeleportEvent.TeleportCause.PLUGIN].
     * - Logs a warning if `teleportAsync` returns `false` so admins can correlate
     *   with protection-plugin event-cancellation logs.
     *
     * Safe to call from any thread: re-dispatches to the main thread internally.
     *
     * @param player the player to teleport.
     * @param targetLocation the destination.
     * @param onSuccess invoked on the main thread after a successful teleport
     *   (e.g. to stamp a per-player cooldown). Skipped on failure.
     */
    fun startTeleport(player: Player, targetLocation: Location, onSuccess: (() -> Unit)? = null) {
        if (Bukkit.isPrimaryThread()) {
            startTeleportInternal(player, targetLocation, onSuccess)
        } else {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable { startTeleportInternal(player, targetLocation, onSuccess) },
            )
        }
    }

    private fun startTeleportInternal(player: Player, targetLocation: Location, onSuccess: (() -> Unit)?) {
        val playerId = player.uniqueId
        cancelTeleport(playerId)
        if (CombatUtil.isInCombat(player)) {
            player.sendMessage("§e◷ Cannot teleport in combat.")
            return
        }
        val session = TeleportSession(player, targetLocation, player.location.clone(), onSuccess)
        activeTeleports[playerId] = session
        announceCountdownStart(player)
        val task = CountdownTask(playerId)
        session.countdownTask = task
        task.runTaskTimer(plugin, 0L, TICKS_PER_SECOND)
    }

    private fun announceCountdownStart(player: Player) {
        player.sendMessage("§e◷ Teleportation countdown started! Don't move for $COUNTDOWN_SECONDS seconds...")
        player.sendActionBar(
            Component.text("§eTeleporting to guild home in §f$COUNTDOWN_SECONDS§e seconds..."),
        )
    }

    /** Per-tick (1s) countdown runnable; checks movement, decrements, triggers teleport at 0. */
    private inner class CountdownTask(private val playerId: UUID) : BukkitRunnable() {
        override fun run() {
            val current = activeTeleports[playerId]
            when {
                current == null -> cancel()
                !current.player.isOnline -> {
                    cancelTeleport(playerId)
                    cancel()
                }
                hasPlayerMoved(current) -> {
                    cancelTeleport(playerId)
                    current.player.sendMessage("§c❌ Teleportation canceled - you moved!")
                    cancel()
                }
                current.remainingSeconds <= 0 -> {
                    performTeleport(TeleportContext(current.player, current.targetLocation, current.onSuccess))
                    activeTeleports.remove(playerId)
                    cancel()
                }
                else -> tickCountdown(current)
            }
        }
    }

    private fun tickCountdown(session: TeleportSession) {
        session.player.sendActionBar(
            Component.text("§eTeleporting to guild home in §f${session.remainingSeconds}§e seconds..."),
        )
        session.remainingSeconds--
    }

    private fun performTeleport(ctx: TeleportContext) {
        ejectVehicleAndPassengers(ctx.player)
        // Pass TeleportCause.PLUGIN so region/protection plugins see a known cause
        // (default UNKNOWN is rejected by many protection plugins, causing silent
        // PlayerTeleportEvent cancellation -> CompletableFuture(false)).
        ctx.player.teleportAsync(ctx.targetLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete { success, throwable ->
                Bukkit.getScheduler().runTask(plugin, Runnable { dispatchResult(ctx, success, throwable) })
            }
    }

    private fun ejectVehicleAndPassengers(player: Player) {
        // teleportAsync rejects entities with passengers; protection plugins often
        // cancel mounted teleports.
        player.vehicle?.let { player.leaveVehicle() }
        if (player.passengers.isNotEmpty()) {
            player.passengers.toList().forEach { player.removePassenger(it) }
        }
    }

    private fun dispatchResult(ctx: TeleportContext, success: Boolean?, throwable: Throwable?) {
        when {
            throwable != null -> onTeleportException(ctx, throwable)
            success == true -> onTeleportSucceeded(ctx)
            else -> onTeleportRejected(ctx)
        }
    }

    private fun onTeleportException(ctx: TeleportContext, throwable: Throwable) {
        logger.warn("Teleport threw for ${ctx.player.name} -> ${ctx.targetLocation}", throwable)
        if (ctx.player.isOnline) ctx.player.sendMessage("§c❌ Teleport failed — an error occurred.")
    }

    private fun onTeleportSucceeded(ctx: TeleportContext) {
        if (ctx.player.isOnline) {
            ctx.player.sendMessage("§a✅ Welcome to your guild home!")
            ctx.player.sendActionBar(Component.text("§aTeleported to guild home!"))
        }
        ctx.onSuccess?.invoke()
    }

    private fun onTeleportRejected(ctx: TeleportContext) {
        val world = ctx.targetLocation.world?.name
        logger.warn(
            "teleportAsync returned false for {} -> world={} x={} y={} z={} " +
                "(likely cancelled by another plugin's PlayerTeleportEvent listener)",
            ctx.player.name, world, ctx.targetLocation.x, ctx.targetLocation.y, ctx.targetLocation.z,
        )
        if (ctx.player.isOnline) ctx.player.sendMessage("§c❌ Teleport failed — please try again.")
    }

    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val cur = session.player.location
        val start = session.startLocation
        return Math.abs(cur.x - start.x) > MOVEMENT_TOLERANCE ||
            Math.abs(cur.y - start.y) > MOVEMENT_TOLERANCE ||
            Math.abs(cur.z - start.z) > MOVEMENT_TOLERANCE ||
            cur.world != start.world
    }

    /** Named magic-number constants used by the countdown and movement check. */
    companion object {
        private const val COUNTDOWN_SECONDS = 5
        private const val TICKS_PER_SECOND = 20L
        private const val MOVEMENT_TOLERANCE = 0.1
    }
}
