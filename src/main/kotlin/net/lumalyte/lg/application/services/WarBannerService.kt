package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.WarBannerRepository
import net.lumalyte.lg.config.WarBannerConfig
import net.lumalyte.lg.domain.entities.WarBannerState
import java.util.UUID

sealed interface WarBannerPlacementResult {
    data class Placed(val state: WarBannerState, val cost: Long) : WarBannerPlacementResult
    data class PaymentUnknown(val state: WarBannerState, val cost: Long) : WarBannerPlacementResult
    data class Cooldown(val remainingMillis: Long) : WarBannerPlacementResult
    data object ActiveBanner : WarBannerPlacementResult
    data object NotMember : WarBannerPlacementResult
    data object NoPermission : WarBannerPlacementResult
    data object NoActiveWar : WarBannerPlacementResult
    data object InsufficientGold : WarBannerPlacementResult
    data object PaymentUnavailable : WarBannerPlacementResult
    data object ConfigurationError : WarBannerPlacementResult
    data class Failed(val compensated: Boolean) : WarBannerPlacementResult
}

class WarBannerService(
    private val repository: WarBannerRepository,
    private val physicalGold: PhysicalGoldPort,
    private val config: () -> WarBannerConfig,
    private val isMember: (UUID, UUID) -> Boolean,
    private val canPlace: (UUID, UUID) -> Boolean,
    private val hasActiveWar: (UUID) -> Boolean,
) {
    companion object {
        const val ACTIVE_DURATION_MILLIS: Long = 15L * 60L * 1000L
    }

    @Synchronized
    fun place(
        transactionId: UUID,
        playerId: UUID,
        guildId: UUID,
        worldId: UUID,
        x: Int,
        y: Int,
        z: Int,
        now: Long,
        render: (WarBannerState) -> Boolean,
    ): WarBannerPlacementResult {
        if (!isMember(playerId, guildId)) return WarBannerPlacementResult.NotMember
        if (!canPlace(playerId, guildId)) return WarBannerPlacementResult.NoPermission
        if (!hasActiveWar(guildId)) return WarBannerPlacementResult.NoActiveWar

        val settings = config()
        val cost = settings.rawGoldCost.toLong()
        val timing = try {
            val cooldownMillis = Math.multiplyExact(settings.cooldownMinutes.toLong(), 60_000L)
            Triple(
                cooldownMillis,
                Math.addExact(now, ACTIVE_DURATION_MILLIS),
                Math.addExact(now, cooldownMillis),
            )
        } catch (_: ArithmeticException) {
            return WarBannerPlacementResult.ConfigurationError
        }
        if (cost <= 0 || timing.first <= 0) return WarBannerPlacementResult.ConfigurationError

        val existing = repository.get(guildId)
        var priorState = existing
        if (existing != null) {
            if (existing.isActiveAt(now)) return WarBannerPlacementResult.ActiveBanner
            if (existing.active) {
                repository.deactivate(existing.guildId, existing.bannerId)
                priorState = existing.copy(active = false)
            }
            if (existing.cooldownUntil > now) {
                return WarBannerPlacementResult.Cooldown(existing.cooldownUntil - now)
            }
        }

        return when (val reservation = physicalGold.reserve(transactionId, playerId, cost)) {
            PhysicalReservationResult.Insufficient -> WarBannerPlacementResult.InsufficientGold
            PhysicalReservationResult.Unavailable -> WarBannerPlacementResult.PaymentUnavailable
            PhysicalReservationResult.Unknown -> WarBannerPlacementResult.Failed(compensated = false)
            is PhysicalReservationResult.Reserved -> {
                placeReserved(
                    reservation.reservation, guildId, worldId, x, y, z,
                    playerId, now, timing.second, timing.third, cost, priorState, render,
                )
            }
        }
    }

    private fun placeReserved(
        reservation: PhysicalGoldReservation,
        guildId: UUID,
        worldId: UUID,
        x: Int,
        y: Int,
        z: Int,
        playerId: UUID,
        now: Long,
        expiresAt: Long,
        cooldownUntil: Long,
        cost: Long,
        previous: WarBannerState?,
        render: (WarBannerState) -> Boolean,
    ): WarBannerPlacementResult {
        val state = WarBannerState(
            guildId = guildId,
            bannerId = UUID.randomUUID(),
            worldId = worldId,
            x = x,
            y = y,
            z = z,
            placedBy = playerId,
            transactionId = reservation.id,
            placedAt = now,
            expiresAt = expiresAt,
            cooldownUntil = cooldownUntil,
            active = true,
        )
        if (!repository.savePlacement(state)) {
            return rollbackFailedPlacement(previous, state, reservation)
        }

        val rendered = try {
            render(state)
        } catch (_: Exception) {
            false
        }
        if (!rendered) {
            return rollbackFailedPlacement(previous, state, reservation)
        }

        return when (physicalGold.commit(reservation)) {
            PhysicalCommitResult.Committed -> WarBannerPlacementResult.Placed(state, cost)
            PhysicalCommitResult.NotConsumed ->
                rollbackFailedPlacement(previous, state, reservation)
            PhysicalCommitResult.Unknown -> WarBannerPlacementResult.PaymentUnknown(state, cost)
        }
    }

    private fun rollbackFailedPlacement(
        previous: WarBannerState?,
        current: WarBannerState,
        reservation: PhysicalGoldReservation,
    ): WarBannerPlacementResult.Failed {
        val stateRestored = if (previous == null) {
            repository.delete(current.guildId, current.bannerId)
        } else {
            repository.savePlacement(previous)
        }
        val goldRestored = physicalGold.restore(reservation)
        return WarBannerPlacementResult.Failed(stateRestored && goldRestored)
    }

    fun state(guildId: UUID): WarBannerState? = repository.get(guildId)

    @Synchronized
    fun active(guildId: UUID, now: Long): WarBannerState? =
        repository.get(guildId)?.takeIf { it.isActiveAt(now) }

    fun activeForMember(playerId: UUID, guildId: UUID, now: Long): WarBannerState? {
        if (!isMember(playerId, guildId)) return null
        return active(guildId, now)
    }

    @Synchronized
    fun destroyAt(worldId: UUID, x: Int, y: Int, z: Int): WarBannerState? {
        val state = repository.getActiveAt(worldId, x, y, z) ?: return null
        return state.takeIf { repository.deactivate(it.guildId, it.bannerId) }
    }

    @Synchronized
    fun expireDue(now: Long): List<WarBannerState> =
        repository.expiredActive(now).filter { repository.deactivate(it.guildId, it.bannerId) }
}
