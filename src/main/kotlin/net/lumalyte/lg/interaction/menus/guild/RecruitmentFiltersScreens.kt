package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.recruitment.RecruitmentMode

/** Filters presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.quickFilters(): List<RecruitmentButton> = listOf(
    RecruitmentButton(
        lang.raw("recruitment.filter_recruiting"),
        listOf(enabled(recruitingOnly)),
        "lg_confirm",
    ) {
        recruitingOnly = !recruitingOnly
        resetBrowse()
    },
    RecruitmentButton(
        lang.raw("recruitment.filter_beginner"),
        listOf(enabled(beginnersOnly)),
        "lg_nav_members",
    ) {
        beginnersOnly = !beginnersOnly
        resetBrowse()
    },
    RecruitmentButton(lang.raw("recruitment.building"), listOf(enabled(interest == "building")), "lg_home") {
        interest = if (interest == "building") null else "building"
        resetBrowse()
    },
    RecruitmentButton(lang.raw("recruitment.pvp"), listOf(enabled(interest == "pvp")), "lg_nav_warfare") {
        interest = if (interest == "pvp") null else "pvp"
        resetBrowse()
    },
    RecruitmentButton(
        lang.raw("recruitment.mode.discord"),
        listOf(enabled(joiningMethod == RecruitmentMode.DISCORD)),
        "lg_invite",
    ) {
        joiningMethod = if (joiningMethod == RecruitmentMode.DISCORD) null else RecruitmentMode.DISCORD
        resetBrowse()
    },
    RecruitmentButton(
        lang.raw("recruitment.sort"),
        listOf(
            when (sorting) {
                1 -> lang.raw("recruitment.sort_1")
                2 -> lang.raw("recruitment.sort_2")
                else -> lang.raw("recruitment.sort_0")
            },
        ),
        "lg_sort",
    ) {
        sorting = (sorting + 1) % 3
        resetBrowse()
    },
    RecruitmentButton(lang.raw("recruitment.reset"), icon = "lg_cancel") {
        query = ""
        peaceful = null
        recruitingOnly = true
        beginnersOnly = false
        joiningMethod = null
        interest = null
        sorting = 0
        languageFilter = ""
        timeFilter = ""
        resetBrowse()
    },
)

internal fun RecruitmentController.filters(): List<RecruitmentButton> = listOf(
    RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_nav_settings") {
        peaceful =
            when (peaceful) {
                null -> true
                true -> false
                false -> null
            }
        refresh()
    },
    RecruitmentButton(
        lang.raw("recruitment.filter_recruiting"),
        listOf(enabled(recruitingOnly)),
        "lg_nav_settings",
    ) {
        recruitingOnly = !recruitingOnly
        refresh()
    },
    RecruitmentButton(
        lang.raw("recruitment.filter_beginner"),
        listOf(enabled(beginnersOnly)),
        "lg_nav_settings",
    ) {
        beginnersOnly = !beginnersOnly
        refresh()
    },
    RecruitmentButton(
        lang.raw("recruitment.method"),
        listOf(joiningMethod?.let(::mode) ?: lang.raw("recruitment.any")),
        "lg_nav_settings",
    ) {
        joiningMethod =
            when (joiningMethod) {
                null -> RecruitmentMode.JOIN_IMMEDIATELY
                RecruitmentMode.CLOSED -> null
                else -> RecruitmentMode.entries[requireNotNull(joiningMethod).ordinal + 1]
            }
        refresh()
    },
    RecruitmentButton(lang.raw("recruitment.filter_language"), listOf(languageFilter), "lg_tag") {
        ask(lang.raw("recruitment.filter_language"), languageFilter) {
            languageFilter = it.take(40)
            browsePage = 0
            refresh()
        }
    },
    RecruitmentButton(lang.raw("recruitment.filter_times"), listOf(timeFilter), "lg_created") {
        ask(lang.raw("recruitment.filter_times"), timeFilter) {
            timeFilter = it.take(100)
            browsePage = 0
            refresh()
        }
    },
)
