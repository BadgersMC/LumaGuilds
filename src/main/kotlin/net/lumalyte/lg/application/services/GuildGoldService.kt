package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.domain.gold.GuildGoldCalculator
import net.lumalyte.lg.domain.gold.GuildGoldCapacity
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldPolicy
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import java.util.UUID

fun interface GuildGoldPolicyProvider {
    fun policyFor(guildId: UUID): GuildGoldPolicy
}

fun interface GuildGoldCapacityProvider {
    fun capacityFor(guildId: UUID): GuildGoldCapacity
}

class GuildGoldService(
    private val repository: GuildGoldRepository,
    private val policyProvider: GuildGoldPolicyProvider,
    private val capacityProvider: GuildGoldCapacityProvider
) {
    fun balance(guildId: UUID): Long = repository.getBalance(guildId)

    fun capacity(guildId: UUID): Long = GuildGoldCalculator.effectiveCapacity(
        policy = policyProvider.policyFor(guildId),
        capacity = capacityProvider.capacityFor(guildId)
    )

    fun creditSystem(
        transactionId: UUID,
        guildId: UUID,
        actorId: UUID,
        amount: Long,
        route: GuildGoldRoute,
        reason: String
    ): GuildGoldResult {
        val policy = policyProvider.policyFor(guildId)
        validateCommon(guildId, amount)?.let { return it }
        if (amount > policy.maxDeposit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        if (policy.autoFreezeSuspicious && amount >= policy.suspiciousThreshold) {
            repository.setFrozen(guildId, true, actorId, "Suspicious transaction: $reason")
            return GuildGoldResult.Rejected(GuildGoldRejection.SUSPICIOUS_FROZEN)
        }
        val mutation = GuildGoldMutation(
            transactionId = transactionId,
            guildId = guildId,
            actorId = actorId,
            route = route,
            direction = GuildGoldDirection.CREDIT,
            amount = amount,
            fee = 0,
            description = reason
        )
        return repository.apply(mutation, capacity(guildId), periodStartEpochMs = null)
    }

    fun debitSystem(
        transactionId: UUID,
        guildId: UUID,
        actorId: UUID,
        amount: Long,
        reason: String
    ): GuildGoldResult {
        validateCommon(guildId, amount)?.let { return it }
        val mutation = GuildGoldMutation(
            transactionId = transactionId,
            guildId = guildId,
            actorId = actorId,
            route = GuildGoldRoute.SYSTEM,
            direction = GuildGoldDirection.DEBIT,
            amount = amount,
            fee = 0,
            description = reason
        )
        return repository.apply(mutation, capacity(guildId), periodStartEpochMs = null)
    }

    private fun validateCommon(guildId: UUID, amount: Long): GuildGoldResult.Rejected? {
        if (amount <= 0) return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        if (repository.isFrozen(guildId)) return GuildGoldResult.Rejected(GuildGoldRejection.FROZEN)
        return null
    }
}
