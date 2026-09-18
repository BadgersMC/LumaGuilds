package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.persistence.RewardPurchaseRepository
import net.lumalyte.lg.domain.rewards.RewardPurchaseRequest
import net.lumalyte.lg.domain.rewards.RewardPurchaseResult
import net.lumalyte.lg.domain.rewards.RewardPurchaseRejection
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

data class GuildGoldSettings(val policy: GuildGoldPolicy, val capacity: GuildGoldCapacity) {
    val effectiveCapacity: Long get() = GuildGoldCalculator.effectiveCapacity(policy, capacity)
}

fun interface GuildGoldSettingsProvider {
    fun settingsFor(guildId: UUID): GuildGoldSettings
}

class GuildGoldService(
    private val repository: GuildGoldRepository,
    private val settingsProvider: GuildGoldSettingsProvider,
    private val authorization: GuildGoldAuthorizationPort = GuildGoldAuthorizationPort.AllowAll,
    private val personalEconomy: PersonalEconomyPort = PersonalEconomyPort.Unavailable,
    private val physicalGold: PhysicalGoldPort = PhysicalGoldPort.Unavailable,
    private val periodStartProvider: () -> Long = { 0L },
    private val additionalFrozen: (UUID) -> Boolean = { false },
    private val rewardPurchases: RewardPurchaseRepository? = null,
    private val rewardPurchaseAuthorization: (UUID, UUID) -> Boolean = { _, _ -> false },
    private val rewardPurchasesEnabled: () -> Boolean = { true }
) {
    constructor(
        repository: GuildGoldRepository,
        policyProvider: GuildGoldPolicyProvider,
        capacityProvider: GuildGoldCapacityProvider,
        authorization: GuildGoldAuthorizationPort = GuildGoldAuthorizationPort.AllowAll,
        personalEconomy: PersonalEconomyPort = PersonalEconomyPort.Unavailable,
        physicalGold: PhysicalGoldPort = PhysicalGoldPort.Unavailable,
        periodStartProvider: () -> Long = { 0L },
        additionalFrozen: (UUID) -> Boolean = { false }
    ) : this(repository, GuildGoldSettingsProvider { guildId ->
        GuildGoldSettings(policyProvider.policyFor(guildId), capacityProvider.capacityFor(guildId))
    }, authorization, personalEconomy, physicalGold, periodStartProvider, additionalFrozen)

    fun balance(guildId: UUID): Long = repository.getBalance(guildId)

    /** Chapter 2 purchases are unavailable until their atomic storage and authority are wired. */
    fun purchaseReward(request: RewardPurchaseRequest): RewardPurchaseResult {
        val purchases = rewardPurchases
            ?: return RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE)
        return try {
            if (!rewardPurchasesEnabled()) return RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE)
            purchases.purchase(request) {
                when {
                    !rewardPurchaseAuthorization(request.actorId, request.guildId) -> RewardPurchaseRejection.UNAUTHORIZED
                    additionalFrozen(request.guildId) -> RewardPurchaseRejection.FROZEN
                    else -> null
                }
            }
        } catch (_: Exception) {
            RewardPurchaseResult.Failed(request.transactionId)
        }
    }

    /** Only durable evidence permits recovery; a timeout never proves a started effect failed. */
    fun reconcilePending(createdBeforeEpochMs: Long): Int {
        var resolved = repository.reconcileUnstarted(createdBeforeEpochMs)
        for (record in repository.pendingPhysicalCredits()) {
            val mutation = record.mutation
            if (repository.isFrozen(mutation.guildId) || additionalFrozen(mutation.guildId)) continue
            val receipt = physicalGold.reservation(mutation.transactionId) ?: continue
            if (receipt.playerId != mutation.actorId || receipt.value != exactAdd(mutation.amount, mutation.fee)) continue
            if (record.status == net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.FAILED_COMPENSATION) {
                if (repository.reverseExternalCredit(mutation.transactionId) && physicalGold.restore(receipt)) {
                    repository.recordCompensation(mutation.transactionId, true, "Recovery: physical reservation restored after credit reversal")
                    resolved++
                }
            } else if (physicalGold.commit(receipt) == PhysicalCommitResult.Committed) {
                repository.recordExternalOutcome(mutation.transactionId, true)
            }
        }
        for (record in repository.confirmedCredits()) {
            val guildId = record.mutation.guildId
            if (repository.isFrozen(guildId) || additionalFrozen(guildId)) continue
            if (repository.recoverConfirmedCredit(record.mutation.transactionId, capacity(guildId)) is GuildGoldResult.Applied) resolved++
        }
        return resolved
    }

    /** Preflight a future system payout; live balance, capacity and freeze are checked at execution. */
    fun allowsSystemCreditAmount(guildId: UUID, amount: Long): Boolean {
        val settings = settingsProvider.settingsFor(guildId)
        val policy = settings.policy
        return amount >= 0 && amount <= policy.maxDeposit &&
            (!policy.autoFreezeSuspicious || amount < policy.suspiciousThreshold)
    }

    /** Recovery reads the journal before retrying an internal transfer under changed live policy. */
    fun operation(transactionId: UUID): net.lumalyte.lg.domain.gold.GuildGoldOperationRecord? =
        repository.findOperation(transactionId)

    fun depositCost(guildId: UUID, amount: Long): Long? {
        val settings = settingsProvider.settingsFor(guildId)
        val policy = settings.policy
        if (amount <= 0 || amount < policy.minDeposit || amount > policy.maxDeposit) return null
        return exactAdd(amount, GuildGoldCalculator.depositFee(policy, amount))
    }

    fun topBalances(limit: Int): List<Pair<UUID, Long>> = repository.getTopBalances(limit)

    /** Largest deposit affordable from physical inventory, including fees and bank headroom. */
    fun maximumPhysicalDeposit(guildId: UUID, playerId: UUID): Long {
        val available = physicalGold.availableValue(playerId)?.coerceAtLeast(0) ?: return 0
        val settings = settingsProvider.settingsFor(guildId)
        val policy = settings.policy
        var low = 0L
        var high = minOf(available, policy.maxDeposit, (settings.effectiveCapacity - balance(guildId)).coerceAtLeast(0))
        while (low < high) {
            val candidate = low + (high - low) / 2 + (high - low) % 2
            val fee = GuildGoldCalculator.depositFee(policy, candidate)
            if (fee >= 0 && fee <= available - candidate) low = candidate else high = candidate - 1
        }
        return if (low >= policy.minDeposit) low else 0
    }

    fun withdrawalFee(guildId: UUID, amount: Long): Long =
        GuildGoldCalculator.withdrawalFee(settingsProvider.settingsFor(guildId).policy, amount)

    /** Upper bound before affordability including fees; execution rechecks the same live policy. */
    fun withdrawalLimit(guildId: UUID): Long {
        val settings = settingsProvider.settingsFor(guildId)
        val policy = settings.policy
        val remaining = (policy.dailyWithdrawalLimit -
            repository.getDailyWithdrawn(guildId, periodStartProvider())).coerceAtLeast(0)
        return minOf((balance(guildId).toDouble() * policy.withdrawalPercent).toLong(), remaining)
    }

    fun capacity(guildId: UUID): Long = settingsProvider.settingsFor(guildId).effectiveCapacity

    fun creditSystem(
        transactionId: UUID,
        guildId: UUID,
        actorId: UUID,
        amount: Long,
        route: GuildGoldRoute,
        reason: String
    ): GuildGoldResult {
        val settings = settingsProvider.settingsFor(guildId)
        val policy = settings.policy
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
        return repository.apply(mutation, settings.effectiveCapacity, periodStartEpochMs = null)
    }

    fun creditInterest(guildId: UUID, periodEndEpochMs: Long, rate: Double): GuildGoldResult {
        val transactionId = UUID.nameUUIDFromBytes(
            "interest:$guildId:$periodEndEpochMs".toByteArray(StandardCharsets.UTF_8))
        // Reuse the recorded result before recomputing from a balance that already includes this interest.
        repository.findOperation(transactionId)?.let { record ->
            if (record.mutation.guildId != guildId || record.mutation.route != GuildGoldRoute.INTEREST) {
                return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
            }
            return when (record.status) {
                net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.APPLIED -> GuildGoldResult.Applied(
                    transactionId, requireNotNull(record.oldBalance), requireNotNull(record.newBalance), record.mutation.fee)
                net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.REJECTED ->
                    GuildGoldResult.Rejected(record.rejection ?: GuildGoldRejection.EXTERNAL_REJECTED)
                else -> GuildGoldResult.Failed(transactionId, false)
            }
        }
        if (!rate.isFinite() || rate < 0 || periodEndEpochMs <= 0) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        val current = balance(guildId)
        val amount = (current.toDouble() * rate).toLong()
        validateCommon(guildId, maxOf(1, amount))?.let { return it }
        if (amount == 0L) {
            // Journal a completed period even when rounding yields no award. No balance growth
            // occurs, so an already-over-cap guild may still record this zero-value marker.
            return repository.apply(GuildGoldMutation(transactionId, guildId, UUID(0, 0),
                GuildGoldRoute.INTEREST, GuildGoldDirection.CREDIT, 0, 0, "Interest accrual"),
                Long.MAX_VALUE, periodStartEpochMs = null)
        }
        return creditSystem(transactionId, guildId, UUID(0, 0), amount, GuildGoldRoute.INTEREST, "Interest accrual")
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

    /** Recruitment admission substitutes for member bank permission, without bypassing money policy. */
    fun collectJoinFee(request: PersonalGoldRequest, physical: Boolean, admission: PaidGuildAdmission): GuildGoldResult {
        return try {
            collectAdmissionPayment(request, physical, admission)
        } catch (_: Exception) {
            // A provider or membership action may have persisted before throwing. Never retry/refund blindly.
            GuildGoldResult.Failed(request.transactionId, false)
        }
    }

    private fun collectAdmissionPayment(request: PersonalGoldRequest, physical: Boolean, admission: PaidGuildAdmission): GuildGoldResult {
        if (!admission.isEligible()) return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        if (!physical && !personalEconomy.isAvailable()) return GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE)
        validateCommon(request.guildId, request.amount)?.let { return it }
        val settings = settingsProvider.settingsFor(request.guildId)
        val policy = settings.policy
        if (request.amount < policy.minDeposit || request.amount > policy.maxDeposit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        suspicious(request.guildId, request.playerId, request.amount, request.description, policy)?.let { return it }
        if (wouldExceedCapacity(request.guildId, request.amount, settings.effectiveCapacity)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
        }
        val fee = GuildGoldCalculator.depositFee(policy, request.amount)
        val total = exactAdd(request.amount, fee) ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        val mutation = personalMutation(request, GuildGoldDirection.CREDIT, fee).copy(
            route = if (physical) GuildGoldRoute.PHYSICAL_ITEM else GuildGoldRoute.PERSONAL_ACCOUNT)
        existingResultOrPrepare(mutation)?.let { return it }

        if (!repository.beginExternal(request.transactionId, "ADMISSION")) return GuildGoldResult.Failed(request.transactionId, false)
        var reservation: PhysicalGoldReservation? = null
        if (physical) {
            when (val reserved = physicalGold.reserve(request.transactionId, request.playerId, total)) {
                PhysicalReservationResult.Unknown -> return GuildGoldResult.Failed(request.transactionId, false)
                is PhysicalReservationResult.Reserved -> reservation = reserved.reservation
                PhysicalReservationResult.Insufficient -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_REJECTED)
                PhysicalReservationResult.Unavailable -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_UNAVAILABLE)
            }
        } else {
            when (externalEffect(request.transactionId) { personalEconomy.debit(request.playerId, total) }) {
                ExternalTransferResult.Applied -> Unit
                ExternalTransferResult.Unavailable -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_UNAVAILABLE)
                is ExternalTransferResult.Rejected -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_REJECTED)
                is ExternalTransferResult.Failed -> return GuildGoldResult.Failed(request.transactionId, false)
            }
        }

        val applied = repository.applyExternalCredit(mutation, settings.effectiveCapacity)
        if (applied is GuildGoldResult.Rejected) {
            // Only a definitive rejection proves the bank was not credited and permits a refund.
            repository.recordCompensation(request.transactionId, false, "Admission credit rejected; refund pending")
            val restored = reservation?.let(physicalGold::restore)
                ?: (personalEconomy.credit(request.playerId, total) is ExternalTransferResult.Applied)
            repository.recordCompensation(request.transactionId, restored, "Admission credit rejected; payment refund")
            return GuildGoldResult.Failed(request.transactionId, restored)
        }
        if (applied !is GuildGoldResult.Applied) return GuildGoldResult.Failed(request.transactionId, false)
        if (reservation != null) {
            val consumed = commitPhysical(request.transactionId, reservation, applied, complete = false)
            if (consumed !is GuildGoldResult.Applied) return consumed
        }
        if (!admission.isEligible() || !admission.complete()) return GuildGoldResult.Failed(request.transactionId, false)
        return completeExternal(request.transactionId, applied)
    }

    fun depositPersonal(request: PersonalGoldRequest): GuildGoldResult {
        if (!personalEconomy.isAvailable()) {
            return GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE)
        }
        if (!authorization.canDeposit(request.playerId, request.guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        }
        val settings = settingsProvider.settingsFor(request.guildId)
        val policy = settings.policy
        validateCommon(request.guildId, request.amount)?.let { return it }
        if (request.amount < policy.minDeposit || request.amount > policy.maxDeposit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        suspicious(request.guildId, request.playerId, request.amount, request.description, policy)?.let { return it }
        if (wouldExceedCapacity(request.guildId, request.amount, settings.effectiveCapacity)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
        }
        val fee = GuildGoldCalculator.depositFee(policy, request.amount)
        val externalDebit = exactAdd(request.amount, fee)
            ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        val mutation = personalMutation(request, GuildGoldDirection.CREDIT, fee)
        existingResultOrPrepare(mutation)?.let { return it }

        if (!repository.beginExternal(request.transactionId, "DEPOSIT")) return GuildGoldResult.Failed(request.transactionId, false)
        return when (externalEffect(request.transactionId) { personalEconomy.debit(request.playerId, externalDebit) }) {
            ExternalTransferResult.Applied -> {
                when (val applied = repository.apply(mutation, settings.effectiveCapacity, null)) {
                    is GuildGoldResult.Applied -> applied
                    is GuildGoldResult.Rejected -> compensatePersonalDeposit(request, externalDebit)
                    else -> GuildGoldResult.Failed(request.transactionId, false)
                }
            }
            ExternalTransferResult.Unavailable -> rejectPrepared(
                request.transactionId,
                GuildGoldRejection.EXTERNAL_UNAVAILABLE
            )
            is ExternalTransferResult.Failed -> GuildGoldResult.Failed(request.transactionId, false)
            is ExternalTransferResult.Rejected -> rejectPrepared(
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
        val settings = settingsProvider.settingsFor(request.guildId)
        val policy = settings.policy
        suspicious(request.guildId, request.playerId, request.amount, request.description, policy)?.let { return it }
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
        if (runCatching { personalEconomy.balance(request.playerId) }.getOrNull() == null) {
            return GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE)
        }
        existingResultOrPrepare(mutation)?.let { return it }
        val applied = repository.applyExternalDebit(mutation, settings.effectiveCapacity, periodStart)
        if (applied !is GuildGoldResult.Applied) return applied

        if (!repository.beginExternal(request.transactionId, "WITHDRAWAL")) return GuildGoldResult.Failed(request.transactionId, false)
        return when (externalEffect(request.transactionId) { personalEconomy.credit(request.playerId, request.amount) }) {
            ExternalTransferResult.Applied -> {
                completeExternal(request.transactionId, applied)
            }
            is ExternalTransferResult.Failed -> GuildGoldResult.Failed(request.transactionId, false)
            else -> compensatePersonalWithdrawal(request, mutation, applied, periodStart, settings.effectiveCapacity)
        }
    }

    fun depositPhysical(request: PhysicalGoldRequest): GuildGoldResult {
        if (!authorization.canDeposit(request.playerId, request.guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        }
        val settings = settingsProvider.settingsFor(request.guildId)
        val policy = settings.policy
        validateCommon(request.guildId, request.amount)?.let { return it }
        if (request.amount < policy.minDeposit || request.amount > policy.maxDeposit) {
            return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        }
        suspicious(request.guildId, request.playerId, request.amount, request.description, policy)?.let { return it }
        if (wouldExceedCapacity(request.guildId, request.amount, settings.effectiveCapacity)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
        }
        val fee = GuildGoldCalculator.depositFee(policy, request.amount)
        val reservedValue = exactAdd(request.amount, fee)
            ?: return GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
        val mutation = physicalMutation(request, GuildGoldDirection.CREDIT, fee)
        existingResultOrPrepare(mutation)?.let { return it }
        if (!repository.beginExternal(request.transactionId, "DEPOSIT")) return GuildGoldResult.Failed(request.transactionId, false)
        val reservation = when (val result = physicalGold.reserve(request.transactionId, request.playerId, reservedValue)) {
            PhysicalReservationResult.Unknown -> return GuildGoldResult.Failed(request.transactionId, false)
            is PhysicalReservationResult.Reserved -> result.reservation
            PhysicalReservationResult.Unavailable -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_UNAVAILABLE)
            PhysicalReservationResult.Insufficient -> return rejectPrepared(request.transactionId, GuildGoldRejection.EXTERNAL_REJECTED)
        }
        return when (val applied = repository.applyExternalCredit(mutation, settings.effectiveCapacity)) {
            is GuildGoldResult.Applied -> {
                commitPhysical(request.transactionId, reservation, applied)
            }
            is GuildGoldResult.Rejected -> {
                repository.recordCompensation(request.transactionId, false, "Physical credit rejected; reservation restoration pending")
                val restored = physicalGold.restore(reservation)
                repository.recordCompensation(request.transactionId, restored, "physical deposit reservation restored")
                GuildGoldResult.Failed(request.transactionId, restored)
            }
            else -> GuildGoldResult.Failed(request.transactionId, false)
        }
    }

    private fun commitPhysical(transactionId: UUID, reservation: PhysicalGoldReservation,
        applied: GuildGoldResult.Applied, complete: Boolean = true): GuildGoldResult =
        when (runCatching { physicalGold.commit(reservation) }.getOrDefault(PhysicalCommitResult.Unknown)) {
            PhysicalCommitResult.Committed -> {
                if (!repository.recordExternalOutcome(transactionId, true)) GuildGoldResult.Failed(transactionId, false)
                else if (complete) completeExternal(transactionId, applied) else applied
            }
            PhysicalCommitResult.NotConsumed -> {
                if (!repository.reverseExternalCredit(transactionId)) GuildGoldResult.Failed(transactionId, false)
                else {
                    val restored = runCatching { physicalGold.restore(reservation) }.getOrDefault(false)
                    repository.recordCompensation(transactionId, restored, "Physical commit rejected; credit reversed before restoration")
                    GuildGoldResult.Failed(transactionId, restored)
                }
            }
            PhysicalCommitResult.Unknown -> GuildGoldResult.Failed(transactionId, false)
        }

    fun withdrawPhysical(request: PhysicalGoldRequest): GuildGoldResult {
        if (!authorization.canWithdraw(request.playerId, request.guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED)
        }
        validateCommon(request.guildId, request.amount)?.let { return it }
        val settings = settingsProvider.settingsFor(request.guildId)
        val policy = settings.policy
        suspicious(request.guildId, request.playerId, request.amount, request.description, policy)?.let { return it }
        val periodStart = periodStartProvider()
        val balance = balance(request.guildId)
        if (request.amount > (balance.toDouble() * policy.withdrawalPercent).toLong()) {
            return GuildGoldResult.Rejected(GuildGoldRejection.WITHDRAWAL_PERCENT)
        }
        if (request.amount > (policy.dailyWithdrawalLimit - repository.getDailyWithdrawn(request.guildId, periodStart)).coerceAtLeast(0)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.DAILY_LIMIT)
        }
        val mutation = physicalMutation(request, GuildGoldDirection.DEBIT, GuildGoldCalculator.withdrawalFee(policy, request.amount))
        existingResultOrPrepare(mutation)?.let { return it }
        val applied = repository.applyExternalDebit(mutation, settings.effectiveCapacity, periodStart)
        if (applied !is GuildGoldResult.Applied) return applied
        if (!repository.beginExternal(request.transactionId, "WITHDRAWAL")) return GuildGoldResult.Failed(request.transactionId, false)
        return when (externalEffect(request.transactionId) { physicalGold.deliver(request.playerId, request.amount, request.transactionId) }) {
            ExternalTransferResult.Applied -> {
                completeExternal(request.transactionId, applied)
            }
            is ExternalTransferResult.Failed -> GuildGoldResult.Failed(request.transactionId, false)
            else -> compensatePhysicalWithdrawal(request, mutation, periodStart, settings.effectiveCapacity)
        }
    }

    private fun compensatePersonalDeposit(request: PersonalGoldRequest, amount: Long): GuildGoldResult {
        repository.recordCompensation(request.transactionId, false, "Personal deposit rejected; refund pending")
        val refunded = personalEconomy.credit(request.playerId, amount) is ExternalTransferResult.Applied
        repository.recordCompensation(request.transactionId, refunded, "personal deposit refund")
        return GuildGoldResult.Failed(request.transactionId, refunded)
    }

    private fun compensatePersonalWithdrawal(
        request: PersonalGoldRequest,
        mutation: GuildGoldMutation,
        applied: GuildGoldResult.Applied,
        periodStart: Long,
        capacity: Long
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
            capacity,
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

    private fun physicalMutation(request: PhysicalGoldRequest, direction: GuildGoldDirection, fee: Long) =
        GuildGoldMutation(
            request.transactionId,
            request.guildId,
            request.playerId,
            GuildGoldRoute.PHYSICAL_ITEM,
            direction,
            request.amount,
            fee,
            request.description
        )

    private fun compensatePhysicalWithdrawal(
        request: PhysicalGoldRequest,
        mutation: GuildGoldMutation,
        periodStart: Long,
        capacity: Long
    ): GuildGoldResult {
        val total = exactAdd(mutation.amount, mutation.fee)
            ?: return GuildGoldResult.Failed(request.transactionId, false)
        val compensation = GuildGoldMutation(
            compensationId(request.transactionId), request.guildId, request.playerId,
            GuildGoldRoute.SYSTEM, GuildGoldDirection.CREDIT, total, 0,
            "Compensate failed physical withdrawal ${request.transactionId}"
        )
        val result = repository.compensateDebit(
            request.transactionId, compensation, capacity, periodStart,
            "physical withdrawal delivery failed"
        )
        return GuildGoldResult.Failed(request.transactionId, result is GuildGoldResult.Applied)
    }

    private fun existingResultOrPrepare(mutation: GuildGoldMutation): GuildGoldResult? =
        when (val preparation = repository.prepare(mutation)) {
            is net.lumalyte.lg.domain.gold.GuildGoldPreparation.Pending ->
                GuildGoldResult.Failed(preparation.transactionId, false)
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
                GuildGoldResult.Failed(mutation.transactionId, false)
            net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.BALANCE_APPLIED ->
                GuildGoldResult.Failed(mutation.transactionId, false)
        }

    private fun completeExternal(transactionId: UUID, applied: GuildGoldResult.Applied): GuildGoldResult =
        if (runCatching { repository.completeExternal(transactionId) }.getOrDefault(false)) applied
        else GuildGoldResult.Failed(transactionId, false)

    private fun externalEffect(transactionId: UUID, action: () -> ExternalTransferResult): ExternalTransferResult {
        val result = runCatching(action).getOrElse { ExternalTransferResult.Failed("External effect threw; outcome unknown") }
        if (result !is ExternalTransferResult.Failed) {
            if (!repository.recordExternalOutcome(transactionId, result is ExternalTransferResult.Applied))
                return ExternalTransferResult.Failed("External outcome could not be recorded")
        }
        return result
    }

    private fun rejectPrepared(transactionId: UUID, reason: GuildGoldRejection): GuildGoldResult {
        repository.rejectPrepared(transactionId, reason)
        return GuildGoldResult.Rejected(reason)
    }

    private fun suspicious(
        guildId: UUID,
        actorId: UUID,
        amount: Long,
        description: String,
        policy: GuildGoldPolicy
    ): GuildGoldResult.Rejected? {
        if (!policy.autoFreezeSuspicious || amount < policy.suspiciousThreshold) return null
        repository.setFrozen(guildId, true, actorId, "Suspicious transaction: $description")
        return GuildGoldResult.Rejected(GuildGoldRejection.SUSPICIOUS_FROZEN)
    }

    private fun wouldExceedCapacity(guildId: UUID, amount: Long, capacity: Long): Boolean =
        exactAdd(balance(guildId), amount)?.let { it > capacity } ?: true

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
        if (repository.isFrozen(guildId) || additionalFrozen(guildId)) {
            return GuildGoldResult.Rejected(GuildGoldRejection.FROZEN)
        }
        return null
    }
}
