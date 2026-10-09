package net.lumalyte.lg.infrastructure.enthusiastaff

import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
import net.enthusia.staff.moderation.api.PunishmentLifecyclePage
import net.enthusia.staff.moderation.api.PunishmentLifecyclePlatform
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import org.bukkit.Bukkit
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Convergent EnthusiaStaff -> Guild Strikes adapter.
 *
 * Each reconciliation pass scans the authoritative current Staff sanction projection from the
 * beginning. Provider-owned punishment ids make repeated passes idempotent. Restarting the scan
 * after every completed pass also guarantees eventual discovery of sanctions committed while an
 * earlier scan is already in progress.
 */
internal class EnthusiaStaffStrikeFeed(
    private val plugin: JavaPlugin,
    guildService: GuildService,
    private val strikeService: StrikeService,
    membershipHistoryRepository: MembershipHistoryRepository,
    private val configProvider: () -> StrikesConfig,
) : AutoCloseable {
    private val projector =
        EnthusiaStaffStrikeProjector(plugin, guildService, strikeService, membershipHistoryRepository, configProvider)
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var closed = false
    private var task: BukkitTask? = null
    private var platform: PunishmentLifecyclePlatform? = null
    private var cursor = PunishmentLifecycleCursor.beginning()
    private var lastFailure: String? = null

    fun start(): Boolean {
        val service = Bukkit.getServicesManager().load(PunishmentLifecyclePlatform::class.java)
        val compatible = service != null && service.apiVersion() == PunishmentLifecyclePlatform.API_VERSION
        if (compatible) {
            platform = service
            task =
                Bukkit.getScheduler().runTaskTimer(plugin, Runnable { beginSweep() }, INITIAL_DELAY_TICKS, SWEEP_TICKS)
        } else if (service != null) {
            plugin.logger.warning(
                "EnthusiaStaff punishment lifecycle API version ${service.apiVersion()} is incompatible; " +
                    "expected ${PunishmentLifecyclePlatform.API_VERSION}",
            )
        }
        return compatible
    }

    private fun beginSweep() {
        if (closed || !configProvider().enabled || !inFlight.compareAndSet(false, true)) return
        cursor = PunishmentLifecycleCursor.beginning()
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { prepareSweep() })
        } catch (scheduleError: IllegalPluginAccessException) {
            inFlight.set(false)
            reportFailure("Could not schedule Guild Strikes sweep preparation", scheduleError)
        }
    }

    // A provider/repository failure must clear in-flight state and preserve retry; Errors still propagate.
    @Suppress("TooGenericExceptionCaught")
    private fun prepareSweep() {
        if (closed || !configProvider().enabled) {
            inFlight.set(false)
            return
        }
        try {
            strikeService.deactivateExpiredExternal(Instant.now())
        } catch (error: Exception) {
            inFlight.set(false)
            reportFailure("Failed to expire Guild Strikes", error)
            return
        }
        readPage()
    }

    private fun readPage() {
        val service = platform
        if (closed || service == null || !service.available()) {
            inFlight.set(false)
            return
        }
        service.readAfter(cursor, PAGE_SIZE).toCompletableFuture()
            .orTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete(::receivePage)
    }

    private fun receivePage(page: PunishmentLifecyclePage?, error: Throwable?) {
        if (error != null) {
            inFlight.set(false)
            reportFailure("EnthusiaStaff Guild Strikes snapshot read failed", error)
        } else if (closed || !plugin.isEnabled) {
            inFlight.set(false)
        } else {
            try {
                // Persistence/history work remains async; never make moderation scans a tick loop.
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { applyPage(checkNotNull(page)) })
            } catch (scheduleError: IllegalPluginAccessException) {
                inFlight.set(false)
                reportFailure("Could not schedule Guild Strikes snapshot application", scheduleError)
            }
        }
    }

    // This async integration boundary must retry any failed event without advancing its page cursor.
    @Suppress("TooGenericExceptionCaught")
    internal fun applyPage(page: PunishmentLifecyclePage) {
        if (closed || !configProvider().enabled) {
            inFlight.set(false)
            return
        }
        try {
            page.events().forEach(::applyEvent)
            lastFailure = null
            if (page.hasMore()) {
                cursor = page.nextCursor()
                readPage()
            } else {
                cursor = PunishmentLifecycleCursor.beginning()
                inFlight.set(false)
            }
        } catch (error: Exception) {
            inFlight.set(false)
            reportFailure("Failed to apply EnthusiaStaff Guild Strikes snapshot page", error)
        }
    }

    /** Testable event seam; page failure/retry remains owned by this feed. */
    internal fun applyEvent(event: PunishmentLifecycleEvent) = projector.applyEvent(event)

    private fun reportFailure(message: String, error: Throwable) {
        val detail = error.cause?.message ?: error.message ?: error.javaClass.simpleName
        val signature = "$message: $detail"
        if (signature != lastFailure) {
            plugin.logger.warning(signature)
            lastFailure = signature
        }
    }

    override fun close() {
        closed = true
        task?.cancel()
        task = null
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val INITIAL_DELAY_TICKS = 20L
        private const val SWEEP_TICKS = 1_200L
    }
}
