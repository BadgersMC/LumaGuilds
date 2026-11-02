package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component

private val REJECTION_REASONS =
    mapOf(
        "Recruitment settings permission required" to RecruitmentRejection.EDIT_DENIED,
        "Recruitment permission required" to RecruitmentRejection.REVIEW_DENIED,
        "Guild no longer exists" to RecruitmentRejection.GUILD_REMOVED,
        "This guild does not accept in-game applications" to RecruitmentRejection.METHOD_CHANGED,
        "Recruitment method has changed" to RecruitmentRejection.METHOD_CHANGED,
        Pair(
            "An application is already active or your application limit was reached",
            RecruitmentRejection.APPLICATION_LIMIT,
        ),
        "Application no longer exists" to RecruitmentRejection.APPLICATION_REMOVED,
        "Application changed; reopen the inbox" to RecruitmentRejection.STALE_APPLICATION,
        "Offer already changed; refresh your applications" to RecruitmentRejection.STALE_APPLICATION,
        "Application changed; refresh your applications" to RecruitmentRejection.STALE_APPLICATION,
        "Joining fee changed; review the offer again" to RecruitmentRejection.FEE_CHANGED,
        "Joining currency or quoted fee changed; review the offer again" to RecruitmentRejection.FEE_CHANGED,
        "You are not eligible to apply" to RecruitmentRejection.INELIGIBLE,
        "Applicant is no longer eligible" to RecruitmentRejection.INELIGIBLE,
        "You are no longer eligible to join" to RecruitmentRejection.INELIGIBLE,
        Pair(
            "Discord applications require a Discord invite and application instructions",
            RecruitmentRejection.DISCORD_SETUP,
        ),
        "Invite changed" to RecruitmentRejection.INVITE_CHANGED,
    )

/** Only recognized validation failures select specific hints; adapter details remain private. */
internal fun recruitmentRejectionReason(error: Exception): RecruitmentRejection? =
    if (error is IllegalArgumentException) REJECTION_REASONS[error.message] else null

private val REJECTION_MESSAGES: Map<RecruitmentRejection, (LangService) -> Component> =
    mapOf(
        RecruitmentRejection.APPLICATION_LIMIT to { lang -> lang.msg("recruitment.feedback.application_limit") },
        RecruitmentRejection.STALE_APPLICATION to { lang -> lang.msg("recruitment.feedback.stale_application") },
        RecruitmentRejection.INELIGIBLE to { lang -> lang.msg("recruitment.feedback.ineligible") },
        RecruitmentRejection.EDIT_DENIED to { lang -> lang.msg("recruitment.feedback.edit_denied") },
        RecruitmentRejection.REVIEW_DENIED to { lang -> lang.msg("recruitment.feedback.review_denied") },
        RecruitmentRejection.GUILD_REMOVED to { lang -> lang.msg("recruitment.feedback.guild_removed") },
        RecruitmentRejection.METHOD_CHANGED to { lang -> lang.msg("recruitment.feedback.method_changed") },
        RecruitmentRejection.APPLICATION_REMOVED to { lang -> lang.msg("recruitment.feedback.application_removed") },
        RecruitmentRejection.FEE_CHANGED to { lang -> lang.msg("recruitment.feedback.fee_changed") },
        RecruitmentRejection.INVITE_CHANGED to { lang -> lang.msg("recruitment.feedback.invite_changed") },
        RecruitmentRejection.DISCORD_SETUP to { lang -> lang.msg("recruitment.discord_setup") },
    )

internal fun recruitmentRejectionMessage(lang: LangService, error: Exception, input: Boolean = false): Component {
    val reason = recruitmentRejectionReason(error)
    val message = reason?.let { REJECTION_MESSAGES[it]?.invoke(lang) }
    return message ?: if (input) lang.msg("recruitment.invalid_input") else lang.msg("recruitment.feedback.failed")
}
