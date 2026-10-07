package net.lumalyte.lg.infrastructure.enthusiastaff

import net.enthusia.staff.moderation.api.PunishmentCategory
import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
import net.enthusia.staff.moderation.api.PunishmentLifecycleEventKind
import net.enthusia.staff.moderation.api.PunishmentLifecyclePage
import net.enthusia.staff.moderation.api.PunishmentLifecyclePlatform
import net.enthusia.staff.moderation.api.PunishmentLifecycleSource
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.StrikeFeedCursor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable EnthusiaStaff -> Guild Strikes adapter.
 *
 * Staff owns lifecycle persistence/replay. Luma owns guild attribution and its strike ledger.
 * The local cursor advances only after a page has been applied or intentionally skipped.
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
    private var lastFailure: String? = null
    private var cursor = strikeService.feedCursor(PROVIDER)?.let {
        PunishmentLifecycleCursor(it.occurredAt, it.eventId)
    } ?: PunishmentLifecycleCursor.beginning()

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
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { poll() }, 20L, 100L)
        return true
    }

    private fun poll() {
        if (closed || !configProvider().enabled || !inFlight.compareAndSet(false, true)) return
        val service = platform
        if (service == null || !service.available()) {
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

        service.readAfter(cursor, PAGE_SIZE).whenComplete { page, error ->
            if (error != null) {
                inFlight.set(false)
                reportFailure("EnthusiaStaff Guild Strikes feed read failed", error)
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
                reportFailure("Could not schedule Guild Strikes feed application", scheduleError)
            }
        }
    }

    private fun applyPage(page: PunishmentLifecyclePage) {
        var scheduleNext = false
        try {
            if (closed || !configProvider().enabled) return
            page.events().forEach(::applyEvent)
            val next = page.nextCursor()
            strikeService.saveFeedCursor(PROVIDER, StrikeFeedCursor(next.occurredAt(), next.eventId()))
            cursor = next
            lastFailure = null
            scheduleNext = page.hasMore()
        } catch (error: Exception) {
            reportFailure("Failed to apply EnthusiaStaff Guild Strikes page; cursor was not advanced", error)
        } finally {
            inFlight.set(false)
        }
        if (scheduleNext && !closed) {
            Bukkit.getScheduler().runTask(plugin, Runnable { poll() })
        }
    }

    internal fun applyEvent(event: PunishmentLifecycleEvent) {
        val type = event.category().name
        val counted = configProvider().countedTypes.asSequence().map { it.uppercase() }.toSet()
        if (event.category() == PunishmentCategory.OTHER || type !in counted) return

        if (event.source() == PunishmentLifecycleSource.LITEBANS) {
            if (event.kind() == PunishmentLifecycleEventKind.CREATED) return
            val entryId = event.sourcePunishmentId().toLongOrNull()
            if (entryId == null) {
                plugin.logger.warning(
                    "Skipping malformed LiteBans strike lifecycle id '${event.sourcePunishmentId()}'",
                )
                return
            }
            strikeService.reconcileLegacyStrike(type, entryId, event.active())
            return
        }

        val expiresAt = when (event.category()) {
            PunishmentCategory.MUTE, PunishmentCategory.BAN -> event.expiresAt().orElse(null)
            else -> null
        }
        if (event.kind() == PunishmentLifecycleEventKind.CREATED) {
            val guildId = resolveGuildAtTime(event.subjectId(), event.issuedAt()) ?: return
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
                active = event.active(),
            )
        } else {
            strikeService.reconcileExternalStrike(
                PROVIDER,
                event.sourcePunishmentId(),
                event.active(),
                expiresAt,
            )
        }
    }

    private fun resolveGuildAtTime(playerId: UUID, at: Instant): UUID? {
        val stints = runCatching { membershipHistoryRepository.getByPlayer(playerId) }.getOrElse { emptyList() }
        stints.firstOrNull { stint ->
            !stint.joinedAt.isAfter(at) && (stint.departedAt == null || stint.departedAt!!.isAfter(at))
        }?.let { return it.guildId }

        if (!configProvider().backfill.fallbackToCurrentGuild) return null
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
    }
}
