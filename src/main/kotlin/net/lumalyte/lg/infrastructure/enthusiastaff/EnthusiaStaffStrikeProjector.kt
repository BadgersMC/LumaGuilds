package net.lumalyte.lg.infrastructure.enthusiastaff

import net.enthusia.staff.moderation.api.PunishmentCategory
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
import net.enthusia.staff.moderation.api.PunishmentLifecycleSource
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.GuildStrike
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.UUID

/** Attributes and reconciles one provider projection without advancing a feed cursor. */
internal class EnthusiaStaffStrikeProjector(
    private val plugin: JavaPlugin,
    private val guildService: GuildService,
    private val strikeService: StrikeService,
    private val membershipHistoryRepository: MembershipHistoryRepository,
    private val configProvider: () -> StrikesConfig,
) {
    fun applyEvent(event: PunishmentLifecycleEvent) {
        val type = event.category().name
        val counted =
            configProvider()
                .countedTypes
                .asSequence()
                .map { it.uppercase() }
                .toSet()
        if (event.category() != PunishmentCategory.OTHER && type in counted) {
            val expiresAt = expirationFor(event)
            val active = effectiveActive(event, expiresAt)
            if (event.source() == PunishmentLifecycleSource.LITEBANS) {
                reconcileImportedLiteBans(type, event, active)
            } else {
                reconcileNative(event, expiresAt, active)
            }
        }
    }

    private fun reconcileNative(event: PunishmentLifecycleEvent, expiresAt: Instant?, active: Boolean) {
        if (!strikeService.reconcileExternalStrike(PROVIDER, event.sourcePunishmentId(), active, expiresAt)) {
            val guild = resolveGuildAtTime(event.subjectId(), event.issuedAt(), allowCurrentFallback = false)
            if (guild != null) strikeService.recordExternalStrike(nativeStrike(guild, event, expiresAt, active))
        }
    }

    private fun nativeStrike(
        guild: UUID,
        event: PunishmentLifecycleEvent,
        expiresAt: Instant?,
        active: Boolean,
    ): GuildStrike {
        return GuildStrike(
            guildId = guild,
            playerUuid = event.subjectId(),
            playerName = event.subjectName().orElse(null),
            punishmentType = event.category().name,
            reason = event.publicReason(),
            executorName = event.actorName().orElse(null),
            issuedAt = event.issuedAt(),
            sourceProvider = PROVIDER,
            sourcePunishmentId = event.sourcePunishmentId(),
            expiresAt = expiresAt,
            active = active,
        )
    }

    private fun reconcileImportedLiteBans(type: String, event: PunishmentLifecycleEvent, active: Boolean) {
        val entryId = event.sourcePunishmentId().toLongOrNull()
        if (entryId == null) {
            plugin.logger.warning(
                "Skipping malformed LiteBans strike lifecycle id '${event.sourcePunishmentId()}'",
            )
            return
        }

        // Always reconcile existing rows, including when historical backfill is disabled.
        if (!strikeService.reconcileLegacyStrike(type, entryId, active) && configProvider().backfill.enabled) {
            importLegacy(type, event, active, entryId)
        }
    }

    private fun importLegacy(type: String, event: PunishmentLifecycleEvent, active: Boolean, entryId: Long) {
        val guild =
            resolveGuildAtTime(event.subjectId(), event.issuedAt(), configProvider().backfill.fallbackToCurrentGuild)
        if (guild != null) {
            strikeService.recordStrike(
                guild,
                event.subjectId(),
                event.subjectName().orElse(null),
                type,
                event.publicReason(),
                event.actorName().orElse(null),
                event.issuedAt(),
                entryId,
                active,
            )
        }
    }

    private fun effectiveActive(event: PunishmentLifecycleEvent, expiresAt: Instant?): Boolean =
        event.active() && (expiresAt == null || expiresAt.isAfter(Instant.now()))

    private fun expirationFor(event: PunishmentLifecycleEvent): Instant? {
        return when (event.category()) {
            PunishmentCategory.MUTE, PunishmentCategory.BAN -> event.expiresAt().orElse(null)
            else -> null
        }
    }

    private fun resolveGuildAtTime(playerId: UUID, at: Instant, allowCurrentFallback: Boolean): UUID? {
        // A failed historical read must fail this page for retry, never enable current-guild fallback.
        val stints = membershipHistoryRepository.getByPlayer(playerId)
        stints
            .firstOrNull { stint ->
                !stint.joinedAt.isAfter(at) && stint.departedAt?.isAfter(at) != false
            }
            ?.let { return it.guildId }

        return if (allowCurrentFallback) {
            runCatching {
                guildService
                    .getPlayerGuilds(playerId)
                    .sortedBy { it.id }
                    .firstOrNull()
                    ?.id
            }.getOrNull()
        } else {
            null
        }
    }

    private companion object {
        const val PROVIDER = "ENTHUSIA_STAFF"
    }
}
