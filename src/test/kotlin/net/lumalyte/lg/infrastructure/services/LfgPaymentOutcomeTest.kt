package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

internal class LfgPaymentOutcomeTest {
    private val guild = Guild(UUID.randomUUID(), "Builders", createdAt = Instant.EPOCH)
    private val logger = mockk<org.slf4j.Logger>(relaxed = true)

    @Test
    fun paymentOutcomesRemainDistinct() {
        val reference = UUID.randomUUID()
        kotlin.test.assertIs<net.lumalyte.lg.application.services.LfgJoinResult.PaymentRejected>(
            outcome(GuildGoldResult.Rejected(GuildGoldRejection.FROZEN)),
        )
        kotlin.test.assertIs<net.lumalyte.lg.application.services.LfgJoinResult.PaymentRefunded>(
            outcome(GuildGoldResult.Failed(reference, true)),
        )
        kotlin.test.assertIs<net.lumalyte.lg.application.services.LfgJoinResult.PaymentUncertain>(
            outcome(GuildGoldResult.Failed(reference, false)),
        )
    }

    private fun outcome(result: GuildGoldResult) = result.admissionFeedback(guild, logger)

    @Test
    fun paymentReferencePreserved() {
        val reference = UUID.randomUUID()
        assertEquals(
            reference,
            (
                GuildGoldResult.Failed(reference, false).admissionFeedback(guild, logger)
                    as net.lumalyte.lg.application.services.LfgJoinResult.PaymentUncertain
                ).transactionId,
        )
        assertEquals(
            reference,
            (
                GuildGoldResult.Failed(reference, true).admissionFeedback(guild, logger)
                    as net.lumalyte.lg.application.services.LfgJoinResult.PaymentRefunded
                ).transactionId,
        )
    }
}
