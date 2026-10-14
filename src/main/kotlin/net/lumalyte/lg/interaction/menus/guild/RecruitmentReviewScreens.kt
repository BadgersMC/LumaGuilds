package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.LfgJoinResult

/** Review presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.review(): List<RecruitmentButton> {
    val guild = requireNotNull(ownGuild())
    val application =
        requireNotNull(service.inbox(player.uniqueId, guild.id).firstOrNull { it.id == selectedApplication })
    return listOf(
        answers(application),
        RecruitmentButton(
            lang.raw("recruitment.submitted_at") + application.createdAt,
            listOf(status(application.status), lang.raw("recruitment.expires_at") + application.expiresAt),
            RecruitmentIcons.HISTORY,
        ),
        RecruitmentButton(lang.raw("recruitment.send_offer"), icon = RecruitmentIcons.CONFIRM) {
            reviewApprove = true
            reviewReason = ""
            go(RecruitmentScreen.REVIEW_CONFIRM)
        },
        RecruitmentButton(lang.raw("recruitment.decline"), icon = RecruitmentIcons.CANCEL) {
            ask(lang.raw("recruitment.reason")) {
                reviewApprove = false
                reviewReason = it
                go(RecruitmentScreen.REVIEW_CONFIRM)
            }
        },
    )
}

internal fun RecruitmentController.reviewConfirmation(): List<RecruitmentButton> {
    val guild = requireNotNull(ownGuild())
    val application =
        requireNotNull(service.inbox(player.uniqueId, guild.id).firstOrNull { it.id == selectedApplication })
    return listOf(
        answers(application),
        RecruitmentButton(
            if (reviewApprove) lang.raw("recruitment.send_offer") else lang.raw("recruitment.decline"),
            listOf(reviewReason),
            RecruitmentIcons.CONFIRM,
        ) {
            service.review(player.uniqueId, application.id, reviewApprove, reviewReason)
            go(RecruitmentScreen.INBOX)
        },
        RecruitmentButton(
            lang.raw("recruitment.cancel_review"),
            icon = RecruitmentIcons.BACK,
        ) { go(RecruitmentScreen.REVIEW) },
    )
}

internal fun RecruitmentController.confirm(): List<RecruitmentButton> {
    val quote = requireNotNull(quotedGuild)
    return listOf(
        RecruitmentButton(quote.name, details(quote), RecruitmentIcons.NAV_INFO, quote),
        RecruitmentButton(lang.raw("recruitment.confirm"), icon = RecruitmentIcons.CONFIRM) {
            require(lfg.getJoinRequirement(requireNotNull(guilds.getGuild(quote.id))) == quotedFee)
            val result =
                if (selectedApplication != null) {
                    service.confirm(player.uniqueId, requireNotNull(selectedApplication), quote, quotedFee)
                } else {
                    lfg.joinGuild(player.uniqueId, quote)
                }
            when (result) {
                is LfgJoinResult.Success -> {
                    player.sendMessage(lang.msg("recruitment.feedback.joined"))
                    go(RecruitmentScreen.MINE)
                }
                else -> {
                    player.sendMessage(lang.msg("recruitment.feedback.failed"))
                    go(RecruitmentScreen.MINE)
                }
            }
        },
    )
}
