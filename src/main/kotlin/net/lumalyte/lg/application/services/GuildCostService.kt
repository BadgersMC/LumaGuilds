package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface GuildCreationCostResult {
    data class Applied(val guild: Guild, val cost: Long) : GuildCreationCostResult
    data object InsufficientGold : GuildCreationCostResult
    data object PaymentUnavailable : GuildCreationCostResult
    data object ConfigurationError : GuildCreationCostResult
    data class CreationFailed(val compensated: Boolean) : GuildCreationCostResult
    data class Uncertain(val guild: Guild, val transactionId: UUID) : GuildCreationCostResult
}

sealed interface HomeActivationCostResult {
    data class Applied(val cost: Long) : HomeActivationCostResult
    data class Rejected(val reason: GuildGoldRejection) : HomeActivationCostResult
    data object ConfigurationError : HomeActivationCostResult
    data class PaymentFailed(val transactionId: UUID, val compensationSucceeded: Boolean) : HomeActivationCostResult
    data class ActivationFailed(val compensated: Boolean) : HomeActivationCostResult
}

class GuildCostService(
    private val config: () -> MainConfig,
    private val physicalGold: PhysicalGoldPort,
    private val gold: GuildGoldService,
) {
    fun homeActivationCost(homeOrdinal: Int): Long? {
        require(homeOrdinal >= 1)
        val cfg = config()
        if (!cfg.chapterTwoGoldCostsEnabled) return 0
        val base = cfg.guild.homeActivationBaseCost
        val scale = cfg.guild.homeActivationScale
        if (base <= 0 || !scale.isFinite() || scale < 1.0) return null
        return try {
            BigDecimal.valueOf(base.toLong())
                .multiply(BigDecimal.valueOf(scale).pow(homeOrdinal - 1))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun createGuild(
        transactionId: UUID,
        playerId: UUID,
        create: () -> Guild?,
    ): GuildCreationCostResult {
        val cfg = config()
        if (!cfg.chapterTwoGoldCostsEnabled) {
            return create()?.let { GuildCreationCostResult.Applied(it, 0) }
                ?: GuildCreationCostResult.CreationFailed(compensated = true)
        }
        val cost = cfg.guild.createGuildCost.toLong()
        if (cost <= 0) return GuildCreationCostResult.ConfigurationError

        return when (val reserved = physicalGold.reserve(transactionId, playerId, cost)) {
            PhysicalReservationResult.Insufficient -> GuildCreationCostResult.InsufficientGold
            PhysicalReservationResult.Unavailable -> GuildCreationCostResult.PaymentUnavailable
            PhysicalReservationResult.Unknown -> GuildCreationCostResult.CreationFailed(compensated = false)
            is PhysicalReservationResult.Reserved -> {
                val guild = try { create() } catch (_: Exception) { null }
                if (guild == null) {
                    GuildCreationCostResult.CreationFailed(physicalGold.restore(reserved.reservation))
                } else {
                    when (physicalGold.commit(reserved.reservation)) {
                        PhysicalCommitResult.Committed -> GuildCreationCostResult.Applied(guild, cost)
                        PhysicalCommitResult.NotConsumed -> {
                            val restored = physicalGold.restore(reserved.reservation)
                            if (restored) GuildCreationCostResult.CreationFailed(true)
                            else GuildCreationCostResult.Uncertain(guild, transactionId)
                        }
                        PhysicalCommitResult.Unknown -> GuildCreationCostResult.Uncertain(guild, transactionId)
                    }
                }
            }
        }
    }

    fun activateHome(
        transactionId: UUID,
        guildId: UUID,
        actorId: UUID,
        homeOrdinal: Int,
        alreadyActivated: Boolean,
        activate: () -> Boolean,
    ): HomeActivationCostResult {
        if (alreadyActivated || !config().chapterTwoGoldCostsEnabled) {
            return if (activate()) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        val cost = homeActivationCost(homeOrdinal) ?: return HomeActivationCostResult.ConfigurationError
        return when (val debited = gold.debitSystem(
            transactionId, guildId, actorId, cost, "Activate guild home #$homeOrdinal"
        )) {
            is GuildGoldResult.Rejected -> HomeActivationCostResult.Rejected(debited.reason)
            is GuildGoldResult.Failed -> HomeActivationCostResult.PaymentFailed(
                debited.transactionId, debited.compensationSucceeded
            )
            is GuildGoldResult.Applied -> {
                if (activate()) HomeActivationCostResult.Applied(cost)
                else {
                    val compensationId = UUID.nameUUIDFromBytes(
                        "home-activation-refund:$transactionId".toByteArray(StandardCharsets.UTF_8)
                    )
                    val refund = gold.creditSystem(
                        compensationId, guildId, actorId, cost, GuildGoldRoute.SYSTEM,
                        "Compensate failed guild home activation"
                    )
                    HomeActivationCostResult.ActivationFailed(refund is GuildGoldResult.Applied)
                }
            }
        }
    }
}
