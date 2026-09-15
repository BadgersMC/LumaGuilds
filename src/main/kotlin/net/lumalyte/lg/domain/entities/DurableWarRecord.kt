package net.lumalyte.lg.domain.entities

import java.util.UUID

/** One revision-checked recovery unit; paymentPhase governs an unfinished wager's lifecycle. */
data class DurableWarRecord(
    val id: UUID,
    val revision: Long = 0,
    val fundingCycle: Int = 1,
    val declaration: WarDeclaration? = null,
    val war: War? = null,
    val stats: WarStats? = null,
    val wager: WarWager? = null,
    val paymentPhase: WarPaymentPhase? = null,
    val settlementChosen: Boolean = false,
    val settlementWinner: UUID? = null,
    val paymentAttempts: Map<String, Int> = emptyMap()
) {
    init {
        require(revision >= 0)
        require(fundingCycle >= 1)
        require(declaration != null || war != null)
        require(declaration == null || declaration.id == id)
        require(war == null || war.id == id)
        require(stats == null || stats.warId == id)
        val first = war?.declaringGuildId ?: requireNotNull(declaration).declaringGuildId
        val second = war?.defendingGuildId ?: requireNotNull(declaration).defendingGuildId
        require(first != second)
        declaration?.let {
            require(it.declaringGuildId == first && it.defendingGuildId == second)
            require(it.wagerAmount >= 0 && !it.proposedDuration.isNegative && !it.proposedDuration.isZero)
        }
        wager?.let {
            require(it.warId == id && it.declaringGuildId == first && it.defendingGuildId == second)
            require(it.declaringGuildWager >= 0 && it.defendingGuildWager >= 0)
            require(it.totalPot.toLong() == it.declaringGuildWager.toLong() + it.defendingGuildWager.toLong())
        }
        require((wager == null) == (paymentPhase == null))
        when (paymentPhase) {
            null -> require(!settlementChosen && paymentAttempts.isEmpty())
            WarPaymentPhase.FUNDING, WarPaymentPhase.ESCROWED, WarPaymentPhase.REFUNDING -> require(!settlementChosen)
            WarPaymentPhase.SETTLING -> require(settlementChosen)
            WarPaymentPhase.SETTLED, WarPaymentPhase.REVIEW -> Unit
        }
        if (wager != null && paymentPhase != WarPaymentPhase.SETTLED) {
            require(wager.status == WagerStatus.ESCROWED && wager.resolvedAt == null && wager.winnerGuildId == null)
        }
        if (paymentPhase == WarPaymentPhase.SETTLED) {
            requireNotNull(wager)
            require(wager.resolvedAt != null)
            when (wager.status) {
                WagerStatus.WON -> require(settlementChosen && settlementWinner != null && wager.winnerGuildId == settlementWinner)
                WagerStatus.DRAW -> require(settlementChosen && settlementWinner == null && wager.winnerGuildId == null)
                WagerStatus.CANCELLED -> require(!settlementChosen && settlementWinner == null && wager.winnerGuildId == null)
                WagerStatus.ESCROWED -> error("Settled war cannot retain an unpaid escrow wager")
            }
        }
        require(settlementWinner == null || (settlementChosen && settlementWinner in setOf(first, second)))
        require(paymentAttempts.all { (leg, attempt) -> leg.isNotBlank() && attempt >= 1 })
    }
}

enum class WarPaymentPhase { FUNDING, ESCROWED, REFUNDING, SETTLING, SETTLED, REVIEW }
