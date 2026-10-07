package net.lumalyte.lg.infrastructure.enthusiastaff

import net.enthusia.staff.moderation.api.PunishmentCategory
import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
import net.enthusia.staff.moderation.api.PunishmentLifecyclePage
import net.enthusia.staff.moderation.api.PunishmentLifecyclePlatform
import net.enthusia.staff.moderation.api.PunishmentLifecycleSource
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.util.UUID
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
    private val guildService: GuildService,
    private val strikeService: StrikeService,
    private val membershipHistoryRepository: MembershipHistoryRepository,
    private val configProvider: () -> StrikesConfig,
) : AutoCloseable {
    private val inFlight = AtomicBoolean(false)
    private var closed = false
    private var task: BukkitTask? = null
    private var platform: PunishmentLifecyclePlatform? = null
    private var cursor = PunishmentLifecycleCursor.beginning()
    private var lastFailure: String? = null

    fun start(): Boolean {
        val service = Bukkit.getServicesManager().load(PunishmentLifecyclePlatform::class.java) ?: return false
        if (service.apiVersion() != PunishmentLifecyclePlatform.API_VERSION) {
            plugin.logger.warning(
                "EnthusiaStaff punishment lifecycle API version ${service.apiVersion()} is incompatible; " +
                    "expected ${PunishmentLifecyclePlatform.API_VERSION}",
            )
            return false
        }
        platform = service
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { beginSweep() }, INITIAL_DELAY_TICKS, SWEEP_TICKS)
        return true
    }

    private fun beginSweep() {
        if (closed || !configProvider().enabled || !inFlight.compareAndSet(false, true)) return
        cursor = PunishmentLifecycleCursor.beginning()
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
        service.readAfter(cursor, PAGE_SIZE)
            .toCompletableFuture()
            .orTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete { page, error ->
            if (error != null) {
                inFlight.set(false)
                reportFailure("EnthusiaStaff Guild Strikes snapshot read failed", error)
                return@whenComplete
            }
            if (closed || !plugin.isEnabled) {
                inFlight.set(false)
                return@whenComplete
            }
            try {
                Bukkit.getScheduler().runTask(plugin, Runnable { applyPage(page) })
            } catch (scheduleError: RuntimeException) {
                inFlight.set(false)
                reportFailure("Could not schedule Guild Strikes snapshot application", scheduleError)
            }
        }
    }

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

    internal fun applyEvent(event: PunishmentLifecycleEvent) {
        val type = event.category().name
        val counted = configProvider().countedTypes.asSequence().map { it.uppercase() }.toSet()
        if (event.category() == PunishmentCategory.OTHER || type !in counted) return

        val expiresAt = expirationFor(event)
        val active = effectiveActive(event, expiresAt)

        if (event.source() == PunishmentLifecycleSource.LITEBANS) {
            reconcileImportedLiteBans(type, event, active)
            return
        }

        if (strikeService.reconcileExternalStrike(
                PROVIDER,
                event.sourcePunishmentId(),
                active,
                expiresAt,
            )) {
            return
        }

        val guildId = resolveGuildAtTime(event.subjectId(), event.issuedAt(), allowCurrentFallback = false) ?: return
        strikeService.recordExternalStrike(
            guildId = guildId,
            playerUuid = event.subjectId(),
            playerName = event.subjectName().orElse(null),
            punishmentType = type,
            reason = event.publicReason(),
            executorName = event.actorName().orElse(null),
            issuedAt = event.issuedAt(),
            sourceProvider = PROVIDER,
            sourcePunishmentId = event.sourcePunishmentId(),
            expiresAt = expiresAt,
            active = active,
        )
    }

    private fun reconcileImportedLiteBans(
        type: String,
        event: PunishmentLifecycleEvent,
        active: Boolean,
    ) {
        val entryId = event.sourcePunishmentId().toLongOrNull()
        if (entryId == null) {
            plugin.logger.warning(
                "Skipping malformed LiteBans strike lifecycle id '${event.sourcePunishmentId()}'",
            )
            return
        }

        // Existing LiteBans backfill rows keep their original numeric identity. Always reconcile
        // rows that already exist, even if historical backfill is currently disabled.
        if (strikeService.reconcileLegacyStrike(type, entryId, active)) return

        // Repairing a missing historical row is itself backfill, so preserve the operator's
        // existing backfill.enabled switch instead of silently importing old history.
        if (!configProvider().backfill.enabled) return

        val guildId = resolveGuildAtTime(
            event.subjectId(),
            event.issuedAt(),
            allowCurrentFallback = configProvider().backfill.fallbackToCurrentGuild,
        ) ?: return
        strikeService.recordStrike(
            guildId = guildId,
            playerUuid = event.subjectId(),
            playerName = event.subjectName().orElse(null),
            punishmentType = type,
            reason = event.publicReason(),
            executorName = event.actorName().orElse(null),
            issuedAt = event.issuedAt(),
            litebansEntryId = entryId,
            active = active,
        )
    }

    private fun effectiveActive(event: PunishmentLifecycleEvent, expiresAt: Instant?): Boolean =
        event.active() && (expiresAt == null || expiresAt.isAfter(Instant.now()))

    private fun expirationFor(event: PunishmentLifecycleEvent): Instant? =
        when (event.category()) {
            PunishmentCategory.MUTE, PunishmentCategory.BAN -> event.expiresAt().orElse(null)
            else -> null
        }

    private fun resolveGuildAtTime(
        playerId: UUID,
        at: Instant,
        allowCurrentFallback: Boolean,
    ): UUID? {
        val stints = runCatching { membershipHistoryRepository.getByPlayer(playerId) }.getOrElse { emptyList() }
        stints.firstOrNull { stint ->
            !stint.joinedAt.isAfter(at) && stint.departedAt?.isAfter(at) != false
        }?.let { return it.guildId }

        if (!allowCurrentFallback) return null
        return runCatching {
            guildService.getPlayerGuilds(playerId).sortedBy { it.id }.firstOrNull()?.id
        }.getOrNull()
    }

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
        private const val PROVIDER = "ENTHUSIA_STAFF"
        private const val PAGE_SIZE = 50
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val INITIAL_DELAY_TICKS = 20L
        private const val SWEEP_TICKS = 1_200L
    }
}
