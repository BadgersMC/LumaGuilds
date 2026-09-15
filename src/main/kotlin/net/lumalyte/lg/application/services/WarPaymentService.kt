package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.gold.*
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** One authoritative instance per server. No Bukkit calls and no direct balance writes. */
class WarPaymentService(private val wars: WarRepository, private val gold: GuildGoldService) {
    @Synchronized
    fun fund(recordId: UUID): Boolean = runCatching { fundRecorded(recordId) }.getOrDefault(false)

    private fun fundRecorded(id: UUID): Boolean {
        var record = wars.get(id) ?: return false
        val wager = record.wager ?: return false
        when (record.paymentPhase) {
            WarPaymentPhase.ESCROWED -> return true
            WarPaymentPhase.REFUNDING -> { refundFunding(id); return false }
            WarPaymentPhase.SETTLED -> {
                if (wager.status != WagerStatus.CANCELLED || record.settlementChosen) return false
                // This is a new acceptance attempt only after all prior funding has been refunded.
                record = saved(record.copy(fundingCycle = Math.addExact(record.fundingCycle, 1),
                    paymentPhase = WarPaymentPhase.FUNDING, paymentAttempts = emptyMap(),
                    wager = wager.copy(status = WagerStatus.ESCROWED, resolvedAt = null))) ?: return false
            }
            WarPaymentPhase.FUNDING -> Unit
            else -> return false
        }
        // Either guild may win. Validate the combined pot before taking either stake,
        // not merely the two smaller debits. Changed policy during interrupted funding
        // cancels that attempt and refunds only journal-confirmed debits.
        if (!listOf(wager.declaringGuildId, wager.defendingGuildId).all {
                gold.allowsSystemCreditAmount(it, wager.totalPot.toLong()) }) {
            val latest = wars.get(id) ?: return false
            if (saved(latest.copy(paymentPhase = WarPaymentPhase.REFUNDING)) != null) refundFunding(id)
            return false
        }
        for ((leg, guild, amount) in fundingLegs(record)) {
            when (transfer(id, leg, guild, amount, credit = false)) {
                Step.APPLIED -> Unit
                Step.REJECTED -> {
                    val latest = wars.get(id) ?: return false
                    if (saved(latest.copy(paymentPhase = WarPaymentPhase.REFUNDING)) != null) refundFunding(id)
                    return false
                }
                Step.UNCERTAIN -> { review(id); return false }
                Step.STOP -> return false
            }
        }
        val latest = wars.get(id) ?: return false
        return saved(latest.copy(paymentPhase = WarPaymentPhase.ESCROWED)) != null
    }

    @Synchronized
    fun settle(recordId: UUID, winnerGuildId: UUID?): Boolean =
        runCatching { settleRecorded(recordId, winnerGuildId) }.getOrDefault(false)

    private fun settleRecorded(id: UUID, winner: UUID?): Boolean {
        var record = wars.get(id) ?: return false
        val wager = record.wager ?: return false
        if (winner != null && winner !in setOf(wager.declaringGuildId, wager.defendingGuildId)) return false
        if (record.settlementChosen && record.settlementWinner != winner) return false
        when (record.paymentPhase) {
            WarPaymentPhase.SETTLED -> return record.settlementChosen
            WarPaymentPhase.ESCROWED -> {
                record = saved(record.copy(paymentPhase = WarPaymentPhase.SETTLING,
                    settlementChosen = true, settlementWinner = winner)) ?: return false
            }
            WarPaymentPhase.SETTLING -> if (!record.settlementChosen) return false
            else -> return false
        }
        val payouts = if (winner == null) listOf(
            Leg("declaring-payout", wager.declaringGuildId, wager.declaringGuildWager.toLong()),
            Leg("defending-payout", wager.defendingGuildId, wager.defendingGuildWager.toLong()))
        else listOf(Leg("winner-payout", winner, wager.totalPot.toLong()))
        for ((leg, guild, amount) in payouts) {
            when (transfer(id, leg, guild, amount, credit = true)) {
                Step.APPLIED -> Unit
                Step.UNCERTAIN -> { review(id); return false }
                else -> return false
            }
        }
        val latest = wars.get(id) ?: return false
        return saved(latest.copy(paymentPhase = WarPaymentPhase.SETTLED,
            wager = requireNotNull(latest.wager).copy(status = if (winner == null) WagerStatus.DRAW else WagerStatus.WON,
                winnerGuildId = winner, resolvedAt = Instant.now()))) != null
    }

    private fun refundFunding(id: UUID): Boolean {
        val record = wars.get(id) ?: return false
        for ((leg, guild, amount) in fundingLegs(record)) {
            if (amount == 0L) continue
            val attempt = record.paymentAttempts[leg] ?: continue
            val debit = gold.operation(mutation(record, leg, attempt, guild, amount, false).transactionId)
            if (debit == null || debit.status == GuildGoldOperationStatus.REJECTED) continue
            if (debit.mutation != mutation(record, leg, attempt, guild, amount, false) ||
                debit.status != GuildGoldOperationStatus.APPLIED) { review(id); return false }
            when (transfer(id, "$leg-refund", guild, amount, credit = true)) {
                Step.APPLIED -> Unit
                Step.UNCERTAIN -> { review(id); return false }
                else -> return false
            }
        }
        val latest = wars.get(id) ?: return false
        return saved(latest.copy(paymentPhase = WarPaymentPhase.SETTLED,
            wager = requireNotNull(latest.wager).copy(status = WagerStatus.CANCELLED, resolvedAt = Instant.now()))) != null
    }

    private fun transfer(id: UUID, leg: String, guild: UUID, amount: Long, credit: Boolean): Step {
        if (amount == 0L) return Step.APPLIED
        var record = wars.get(id) ?: return Step.STOP
        var attempt = record.paymentAttempts[leg]
        if (attempt == null) {
            attempt = 1
            record = saved(record.copy(paymentAttempts = record.paymentAttempts + (leg to attempt))) ?: return Step.STOP
        }
        var mutation = mutation(record, leg, attempt, guild, amount, credit)
        val previous = gold.operation(mutation.transactionId)
        if (previous != null) {
            if (previous.mutation != mutation) return Step.UNCERTAIN
            when (previous.status) {
                GuildGoldOperationStatus.APPLIED -> return Step.APPLIED
                GuildGoldOperationStatus.REJECTED -> {
                    // Rejection proves zero movement. A new persisted attempt can recheck changed capacity/policy.
                    attempt = Math.addExact(attempt, 1)
                    record = saved(record.copy(paymentAttempts = record.paymentAttempts + (leg to attempt))) ?: return Step.STOP
                    mutation = mutation(record, leg, attempt, guild, amount, credit)
                }
                else -> return Step.UNCERTAIN
            }
        }
        val result = runCatching {
            if (credit) gold.creditSystem(mutation.transactionId, guild, mutation.actorId, amount,
                GuildGoldRoute.SYSTEM, mutation.description)
            else gold.debitSystem(mutation.transactionId, guild, mutation.actorId, amount, mutation.description)
        }.getOrNull()
        val confirmed = gold.operation(mutation.transactionId)
        if (confirmed != null) {
            if (confirmed.mutation != mutation) return Step.UNCERTAIN
            return when (confirmed.status) {
                GuildGoldOperationStatus.APPLIED -> Step.APPLIED
                GuildGoldOperationStatus.REJECTED -> Step.REJECTED
                else -> Step.UNCERTAIN
            }
        }
        return if (result is GuildGoldResult.Rejected) Step.REJECTED else Step.UNCERTAIN
    }

    private fun mutation(record: DurableWarRecord, leg: String, attempt: Int, guild: UUID, amount: Long, credit: Boolean): GuildGoldMutation {
        val key = "war:${record.id}:cycle:${record.fundingCycle}:$leg:$attempt"
        return GuildGoldMutation(UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)), guild, UUID(0, 0),
            GuildGoldRoute.SYSTEM, if (credit) GuildGoldDirection.CREDIT else GuildGoldDirection.DEBIT,
            amount, 0, key)
    }

    private fun fundingLegs(record: DurableWarRecord): List<Leg> = requireNotNull(record.wager).let {
        listOf(Leg("declaring-debit", it.declaringGuildId, it.declaringGuildWager.toLong()),
            Leg("defending-debit", it.defendingGuildId, it.defendingGuildWager.toLong()))
    }

    private fun saved(record: DurableWarRecord): DurableWarRecord? =
        if (wars.save(record)) record.copy(revision = Math.addExact(record.revision, 1)) else null

    private fun review(id: UUID) { wars.get(id)?.let { saved(it.copy(paymentPhase = WarPaymentPhase.REVIEW)) } }
    private data class Leg(val name: String, val guild: UUID, val amount: Long)
    private enum class Step { APPLIED, REJECTED, UNCERTAIN, STOP }
}
