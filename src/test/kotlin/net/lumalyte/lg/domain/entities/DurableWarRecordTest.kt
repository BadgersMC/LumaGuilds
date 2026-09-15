package net.lumalyte.lg.domain.entities

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class DurableWarRecordTest {
    private val id = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private fun record() = DurableWarRecord(id,
        war = War(id = id, declaringGuildId = first, defendingGuildId = second),
        wager = WarWager(warId = id, declaringGuildId = first, defendingGuildId = second,
            declaringGuildWager = 100, defendingGuildWager = 100), paymentPhase = WarPaymentPhase.FUNDING)

    @Test fun `nonterminal phases cannot contain resolved wagers`() {
        val base = record()
        for (phase in WarPaymentPhase.entries.filter { it != WarPaymentPhase.SETTLED }) {
            assertFailsWith<IllegalArgumentException>(phase.name) {
                base.copy(paymentPhase = phase, settlementChosen = phase == WarPaymentPhase.SETTLING,
                    wager = base.wager!!.copy(status = WagerStatus.DRAW, resolvedAt = Instant.now()))
            }
        }
    }

    @Test fun `funding and escrow phases cannot already choose settlement`() {
        for (phase in listOf(WarPaymentPhase.FUNDING, WarPaymentPhase.ESCROWED, WarPaymentPhase.REFUNDING)) {
            assertFailsWith<IllegalArgumentException>(phase.name) {
                record().copy(paymentPhase = phase, settlementChosen = true)
            }
        }
    }

    @Test fun `settling requires an outcome and unwagered wars cannot carry payment state`() {
        assertFailsWith<IllegalArgumentException> { record().copy(paymentPhase = WarPaymentPhase.SETTLING) }
        assertFailsWith<IllegalArgumentException> {
            record().copy(wager = null, paymentPhase = null, settlementChosen = true)
        }
        assertFailsWith<IllegalArgumentException> {
            record().copy(wager = null, paymentPhase = null, paymentAttempts = mapOf("debit" to 1))
        }
    }

    @Test fun `unresolved escrow cannot carry a resolution timestamp or a paid winner`() {
        val base = record()
        assertFailsWith<IllegalArgumentException> { base.copy(wager = base.wager!!.copy(resolvedAt = Instant.now())) }
        assertFailsWith<IllegalArgumentException> { base.copy(wager = base.wager!!.copy(winnerGuildId = first)) }
    }
}
