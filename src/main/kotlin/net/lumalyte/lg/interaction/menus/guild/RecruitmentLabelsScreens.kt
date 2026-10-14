package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.RecruitmentMode

/** Labels presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.enabled(value: Boolean): String =
    if (value) {
        lang.raw(
            "recruitment.enabled",
        )
    } else {
        lang.raw("recruitment.disabled")
    }

internal fun RecruitmentController.guildMode(): String =
    when (peaceful) {
        null -> lang.raw("recruitment.any")
        true -> lang.raw("recruitment.peaceful")
        false -> lang.raw("recruitment.competitive")
    }

internal fun RecruitmentController.status(value: ApplicationStatus): String =
    when (value) {
        ApplicationStatus.SUBMITTED -> lang.raw("recruitment.status.submitted")
        ApplicationStatus.OFFERED -> lang.raw("recruitment.status.offered")
        ApplicationStatus.ADMISSION_PENDING -> lang.raw("recruitment.status.admission_pending")
        ApplicationStatus.JOINED -> lang.raw("recruitment.status.joined")
        ApplicationStatus.DECLINED -> lang.raw("recruitment.status.declined")
        ApplicationStatus.WITHDRAWN -> lang.raw("recruitment.status.withdrawn")
        ApplicationStatus.EXPIRED -> lang.raw("recruitment.status.expired")
        ApplicationStatus.CLOSED -> lang.raw("recruitment.status.closed")
    }

internal fun RecruitmentController.mode(mode: RecruitmentMode): String =
    when (mode) {
        RecruitmentMode.JOIN_IMMEDIATELY -> lang.raw("recruitment.mode.join")
        RecruitmentMode.IN_GAME -> lang.raw("recruitment.mode.apply")
        RecruitmentMode.DISCORD -> lang.raw("recruitment.mode.discord")
        RecruitmentMode.CLOSED -> lang.raw("recruitment.mode.closed")
    }
