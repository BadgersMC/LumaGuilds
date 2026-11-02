package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication

/** Applications presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.answers(application: GuildApplication): RecruitmentButton {
    return RecruitmentButton(
        status(application.status),
        listOf(application.answers, application.decisionReason),
        RecruitmentIcons.DESCRIPTION,
    ) {
        // The visible lore is a summary. Fetch and authorize again before revealing the complete private answer.
        val fresh =
            if (application.applicantId == player.uniqueId) {
                service.mine(player.uniqueId).firstOrNull { it.id == application.id }
            } else {
                service.inbox(player.uniqueId, application.guildId).firstOrNull { it.id == application.id }
            }
        val current = requireNotNull(fresh)
        val expectedScreen = screen
        val navigationValid = returnGuard()
        net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.book(
            player,
            net.lumalyte.lg.infrastructure.services.RecruitmentBookContent(
                lang.raw("recruitment.application_details"),
                listOf(current.answers, current.decisionReason),
                lang.raw("recruitment.return_application"),
                theme(),
                guilds.getGuild(current.guildId),
                org.bukkit.Bukkit.getOfflinePlayer(current.applicantId).name ?: current.applicantId.toString(),
            ),
            {
                navigationValid() && selectedApplication == current.id && screen == expectedScreen &&
                    if (current.applicantId == player.uniqueId) {
                        service.mine(player.uniqueId).any { it.id == current.id }
                    } else {
                        net.lumalyte.lg.application.services.GuildRecruitmentPermissions.canReview(
                            members, player.uniqueId, current.guildId,
                        ) && service.inbox(player.uniqueId, current.guildId).any { it.id == current.id }
                    }
            },
            { run { refresh() } },
        )
    }
}

internal fun RecruitmentController.applicationButton(application: GuildApplication): RecruitmentButton {
    val name = guilds.getGuild(application.guildId)?.name ?: application.guildId.toString()
    return RecruitmentButton(
        name,
        listOf(
            status(application.status),
            lang.raw("recruitment.submitted_at") + application.createdAt,
            lang.raw("recruitment.expires_at") + application.expiresAt,
            applicationAge(application),
            application.decisionReason,
        ),
        RecruitmentIcons.DESCRIPTION,
    ) {
        selectedApplication = application.id
        selected = application.guildId
        showScreen(RecruitmentScreen.APPLICATION)
    }
}

internal fun RecruitmentController.applicantActions(): List<RecruitmentButton> {
    val application = selectedOwnApplication()
    val items =
        mutableListOf(answers(application))
    if (application.status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED)) {
        items +=
            RecruitmentButton(lang.raw("recruitment.withdraw"), icon = RecruitmentIcons.CANCEL) {
                service.withdraw(player.uniqueId, application.id)
                showScreen(RecruitmentScreen.MINE)
            }
    }
    if (application.status == ApplicationStatus.OFFERED) {
        items +=
            RecruitmentButton(lang.raw("recruitment.offer"), icon = RecruitmentIcons.CONFIRM) {
                quotedGuild = selectedGuild()
                quotedFee = lfg.getJoinRequirement(requireNotNull(quotedGuild))
                showScreen(RecruitmentScreen.CONFIRM)
            }
    }
    if (application.status == ApplicationStatus.ADMISSION_PENDING) {
        items += RecruitmentButton(lang.raw("recruitment.pending"), icon = RecruitmentIcons.DESCRIPTION)
    }
    return items
}

internal fun RecruitmentController.inbox(): List<RecruitmentButton> {
    val guild = requireNotNull(ownGuild())
    return service.inbox(player.uniqueId, guild.id).filter { it.status == ApplicationStatus.SUBMITTED }
        .sortedBy { it.createdAt }
        .map {
                application ->
            RecruitmentButton(
                org.bukkit.Bukkit.getOfflinePlayer(application.applicantId).name ?: application.applicantId.toString(),
                listOf(
                    status(application.status),
                    lang.raw("recruitment.submitted_at") + application.createdAt,
                    lang.raw("recruitment.expires_at") + application.expiresAt,
                    applicationAge(application),
                ),
                RecruitmentIcons.NAV_MEMBERS,
            ) {
                selectedApplication = application.id
                selected = guild.id
                showScreen(RecruitmentScreen.REVIEW)
            }
        }
}

internal fun RecruitmentController.applicationAge(application: GuildApplication): String {
    return lang.raw("recruitment.age_hours") +
        java.time.Duration.between(application.createdAt, java.time.Instant.now())
            .toHours()
            .coerceAtLeast(0)
}
