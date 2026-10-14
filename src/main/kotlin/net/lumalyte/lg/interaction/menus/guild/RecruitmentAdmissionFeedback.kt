package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.LfgJoinResult

/** Typed payment receipts are safe to show; arbitrary adapter exception text is not. */
internal fun recruitmentAdmissionFeedback(lang: LangService, result: LfgJoinResult): Component {
    return when (result) {
        is LfgJoinResult.PaymentRejected -> lang.msg("recruitment.feedback.payment_rejected")
        is LfgJoinResult.PaymentRefunded ->
            lang.msg("recruitment.feedback.payment_refunded", "reference" to result.transactionId.toString())
        is LfgJoinResult.PaymentUncertain ->
            lang.msg("recruitment.feedback.payment_uncertain", "reference" to result.transactionId.toString())
        else -> standardAdmissionFeedback(lang, result)
    }
}

private fun standardAdmissionFeedback(lang: LangService, result: LfgJoinResult): Component {
    return when (result) {
        is LfgJoinResult.Success -> lang.msg("recruitment.feedback.joined")
        is LfgJoinResult.GuildFull -> lang.msg("recruitment.feedback.guild_full")
        is LfgJoinResult.AlreadyInGuild -> lang.msg("recruitment.feedback.already_member")
        is LfgJoinResult.InsufficientFunds -> lang.msg(
            "recruitment.feedback.insufficient_funds",
            "required" to result.required,
            "current" to result.current,
            "currency" to result.currencyType,
        )
        else -> lang.msg("recruitment.feedback.failed")
    }
}
