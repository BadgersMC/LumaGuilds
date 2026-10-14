package net.lumalyte.lg.interaction.menus.guild

import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RecruitmentPaymentFeedbackTest {
    private val lang = mockk<LangService>(relaxed = true)

    @Test
    fun uncertainPaymentShowsReference() {
        val reference = UUID.randomUUID()
        recruitmentAdmissionFeedback(lang, LfgJoinResult.PaymentUncertain(reference))
        verify { lang.msg("recruitment.feedback.payment_uncertain", "reference" to reference.toString()) }
    }

    @Test
    fun confirmedRefundShowsReference() {
        val reference = UUID.randomUUID()
        recruitmentAdmissionFeedback(lang, LfgJoinResult.PaymentRefunded(reference))
        verify { lang.msg("recruitment.feedback.payment_refunded", "reference" to reference.toString()) }
    }

    @Test
    fun definitiveRejectionExplainsNoPayment() {
        recruitmentAdmissionFeedback(lang, LfgJoinResult.PaymentRejected(GuildGoldRejection.FROZEN))
        verify { lang.msg("recruitment.feedback.payment_rejected") }
    }

    @Test
    fun genericAdapterDetailsRemainPrivate() {
        recruitmentAdmissionFeedback(lang, LfgJoinResult.Error("private SQL address"))
        verify { lang.msg("recruitment.feedback.failed") }
    }
}
