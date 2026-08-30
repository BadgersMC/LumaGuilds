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
import java.nio.charset.StandardCharsets

fun interface GuildGoldPolicyProvider {
    fun policyFor(guildId: UUID): GuildGoldPolicy
}

fun interface GuildGoldCapacityProvider {
    fun capacityFor(guildId: UUID): GuildGoldCapacity
}

class GuildGoldService(
    private val repository: GuildGoldRepository,
    private val policyProvider: GuildGoldPolicyProvider,
    private val capacityProvider: GuildGoldCapacityProvider,
    private val authorization: GuildGoldAuthorizationPort = GuildGoldAuthorizationPort.AllowAll,
    private val personalEconomy: PersonalEconomyPort = PersonalEconomyPort.Unavailable,
    private val periodStartProvider: () -> Long = { 0L }
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

    fun depositPersonal(request: PersonalGoldRequest): GuildGoldResult {
        if (!personalEconomy.isAvailable()) {
            return GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE)
        }
        if (!authorization.canDeposit(request.playerId, request.guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        }
        val policy = policyProvider.policyFor(request.guildId)
        validateCommon(request.guildId, request.amount)?.let { return it }
        if (request.amount < policy.minDeposit || request.amount > policy.maxDeposit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        suspicious(request.guildId, request.playerId, request.amount, request.description)?.let { return it }
        if (wouldExceedCapacity(request.guildId, request.amount)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
        }
        val fee = GuildGoldCalculator.depositFee(policy, request.amount)
        val externalDebit = exactAdd(request.amount, fee)
            ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        val mutation = personalMutation(request, GuildGoldDirection.CREDIT, fee)
        existingResultOrPrepare(mutation)?.let { return it }

        return when (personalEconomy.debit(request.playerId, externalDebit)) {
            ExternalTransferResult.Applied -> {
                when (val applied = repository.apply(mutation, capacity(request.guildId), null)) {
                    is GuildGoldResult.Applied -> applied
                    else -> compensatePersonalDeposit(request, externalDebit)
                }
            }
            ExternalTransferResult.Unavailable -> rejectPrepared(
                request.transactionId,
                GuildGoldRejection.EXTERNAL_UNAVAILABLE
            )
            is ExternalTransferResult.Rejected,
            is ExternalTransferResult.Failed -> rejectPrepared(
                request.transactionId,
                GuildGoldRejection.EXTERNAL_REJECTED
            )
        }
    }

    fun withdrawPersonal(request: PersonalGoldRequest): GuildGoldResult {
        if (!personalEconomy.isAvailable()) {
            return GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE)
        }
        if (!authorization.canWithdraw(request.playerId, request.guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        }
        validateCommon(request.guildId, request.amount)?.let { return it }
        val policy = policyProvider.policyFor(request.guildId)
        suspicious(request.guildId, request.playerId, request.amount, request.description)?.let { return it }
        val periodStart = periodStartProvider()
        val withdrawnToday = repository.getDailyWithdrawn(request.guildId, periodStart)
        val balance = balance(request.guildId)
        val percentageLimit = (balance.toDouble() * policy.withdrawalPercent).toLong()
        if (request.amount > percentageLimit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.WITHDRAWAL_PERCENT)
        }
        val dailyRemaining = (policy.dailyWithdrawalLimit - withdrawnToday).coerceAtLeast(0)
        if (request.amount > dailyRemaining) {
            return GuildGoldResult.Rejected(GuildGoldRejection.DAILY_LIMIT)
        }
        val fee = GuildGoldCalculator.withdrawalFee(policy, request.amount)
        val mutation = personalMutation(request, GuildGoldDirection.DEBIT, fee)
        existingResultOrPrepare(mutation)?.let { return it }
        val applied = repository.apply(mutation, capacity(request.guildId), periodStart)
        if (applied !is GuildGoldResult.Applied) return applied

        return when (personalEconomy.credit(request.playerId, request.amount)) {
            ExternalTransferResult.Applied -> applied
            else -> compensatePersonalWithdrawal(request, mutation, applied, periodStart)
        }
    }

    private fun compensatePersonalDeposit(request: PersonalGoldRequest, amount: Long): GuildGoldResult {
        val refunded = personalEconomy.credit(request.playerId, amount) is ExternalTransferResult.Applied
        repository.recordCompensation(request.transactionId, refunded, "personal deposit refund")
        return GuildGoldResult.Failed(request.transactionId, refunded)
    }

    private fun compensatePersonalWithdrawal(
        request: PersonalGoldRequest,
        mutation: GuildGoldMutation,
        applied: GuildGoldResult.Applied,
        periodStart: Long
    ): GuildGoldResult {
        val total = exactAdd(mutation.amount, mutation.fee)
            ?: return GuildGoldResult.Failed(request.transactionId, false)
        val compensation = GuildGoldMutation(
            transactionId = compensationId(request.transactionId),
            guildId = request.guildId,
            actorId = request.playerId,
            route = GuildGoldRoute.SYSTEM,
            direction = GuildGoldDirection.CREDIT,
            amount = total,
            fee = 0,
            description = "Compensate failed personal withdrawal ${applied.transactionId}"
        )
        val result = repository.compensateDebit(
            request.transactionId,
            compensation,
            capacity(request.guildId),
            periodStart,
            "personal withdrawal payout failed"
        )
        return GuildGoldResult.Failed(request.transactionId, result is GuildGoldResult.Applied)
    }

    private fun personalMutation(
        request: PersonalGoldRequest,
        direction: GuildGoldDirection,
        fee: Long
    ) = GuildGoldMutation(
        transactionId = request.transactionId,
        guildId = request.guildId,
        actorId = request.playerId,
        route = GuildGoldRoute.PERSONAL_ACCOUNT,
        direction = direction,
        amount = request.amount,
        fee = fee,
        description = request.description
    )

    private fun existingResultOrPrepare(mutation: GuildGoldMutation): GuildGoldResult? =
        when (val preparation = repository.prepare(mutation)) {
            is net.lumalyte.lg.domain.gold.GuildGoldPreparation.New -> null
            net.lumalyte.lg.domain.gold.GuildGoldPreparation.FingerprintMismatch ->
                GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
            is net.lumalyte.lg.domain.gold.GuildGoldPreparation.Existing ->
                preparation.record.toResult()
        }

    private fun net.lumalyte.lg.domain.gold.GuildGoldOperationRecord.toResult(): GuildGoldResult =
        when (status) {
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.APPLIED -> GuildGoldResult.Applied(
                mutation.transactionId,
                requireNotNull(oldBalance),
                requireNotNull(newBalance),
                mutation.fee
            )
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.REJECTED ->
                GuildGoldResult.Rejected(requireNotNull(rejection))
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.COMPENSATED ->
                GuildGoldResult.Failed(mutation.transactionId, true)
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.FAILED_COMPENSATION ->
                GuildGoldResult.Failed(mutation.transactionId, false)
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.PREPARED ->
                GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
        }

    private fun rejectPrepared(transactionId: UUID, reason: GuildGoldRejection): GuildGoldResult {
        repository.rejectPrepared(transactionId, reason)
        return GuildGoldResult.Rejected(reason)
    }

    private fun suspicious(
        guildId: UUID,
        actorId: UUID,
        amount: Long,
        description: String
    ): GuildGoldResult.Rejected? {
        val policy = policyProvider.policyFor(guildId)
        if (!policy.autoFreezeSuspicious || amount < policy.suspiciousThreshold) return null
        repository.setFrozen(guildId, true, actorId, "Suspicious transaction: $description")
        return GuildGoldResult.Rejected(GuildGoldRejection.SUSPICIOUS_FROZEN)
    }

    private fun wouldExceedCapacity(guildId: UUID, amount: Long): Boolean =
        exactAdd(balance(guildId), amount)?.let { it > capacity(guildId) } ?: true

    private fun exactAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun compensationId(transactionId: UUID): UUID = UUID.nameUUIDFromBytes(
        "guild-gold-compensation:$transactionId".toByteArray(StandardCharsets.UTF_8)
    )

    private fun validateCommon(guildId: UUID, amount: Long): GuildGoldResult.Rejected? {
        if (amount <= 0) return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        if (repository.isFrozen(guildId)) return GuildGoldResult.Rejected(GuildGoldRejection.FROZEN)
        return null
    }
}
