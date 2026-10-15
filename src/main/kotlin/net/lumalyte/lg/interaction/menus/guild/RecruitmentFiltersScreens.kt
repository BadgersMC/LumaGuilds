package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.recruitment.RecruitmentMode

private const val SORT_MODE_COUNT = 3
private const val LANGUAGE_FILTER_LIMIT = 40
private const val PLAY_TIME_FILTER_LIMIT = 100

private const val BUILDING_INTEREST = "building"
private const val PVP_INTEREST = "pvp"

private enum class QuickFilter(val icon: String) {
    RECRUITING(RecruitmentIcons.CONFIRM),
    BEGINNER(RecruitmentIcons.NAV_MEMBERS),
    BUILDING(RecruitmentIcons.HOME),
    PVP(RecruitmentIcons.NAV_WARFARE),
    DISCORD(RecruitmentIcons.INVITE),
}

/** Filters presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.quickFilters(): List<RecruitmentButton> {
    return QuickFilter.entries.map { filter ->
        RecruitmentButton(filterLabel(filter), listOf(enabled(filterActive(filter))), filter.icon) {
            toggleFilter(filter)
            resetBrowse()
        }
    } + listOf(sortButton(), resetButton())
}

private fun RecruitmentController.filterLabel(filter: QuickFilter): String {
    return when (filter) {
        QuickFilter.RECRUITING -> lang.raw("recruitment.filter_recruiting")
        QuickFilter.BEGINNER -> lang.raw("recruitment.filter_beginner")
        QuickFilter.BUILDING -> lang.raw("recruitment.building")
        QuickFilter.PVP -> lang.raw("recruitment.pvp")
        QuickFilter.DISCORD -> lang.raw("recruitment.mode.discord")
    }
}

private fun RecruitmentController.filterActive(filter: QuickFilter): Boolean {
    return when (filter) {
        QuickFilter.RECRUITING -> recruitingOnly
        QuickFilter.BEGINNER -> beginnersOnly
        QuickFilter.BUILDING -> interest == BUILDING_INTEREST
        QuickFilter.PVP -> interest == PVP_INTEREST
        QuickFilter.DISCORD -> joiningMethod == RecruitmentMode.DISCORD
    }
}

private fun RecruitmentController.toggleFilter(filter: QuickFilter) {
    when (filter) {
        QuickFilter.RECRUITING -> recruitingOnly = !recruitingOnly
        QuickFilter.BEGINNER -> beginnersOnly = !beginnersOnly
        QuickFilter.BUILDING -> interest = if (interest == BUILDING_INTEREST) null else BUILDING_INTEREST
        QuickFilter.PVP -> interest = if (interest == PVP_INTEREST) null else PVP_INTEREST
        QuickFilter.DISCORD -> toggleDiscordFilter()
    }
}

private fun RecruitmentController.sortButton(): RecruitmentButton {
    return RecruitmentButton(
        lang.raw("recruitment.sort"),
        listOf(
            when (sorting) {
                1 -> lang.raw("recruitment.sort_1")
                2 -> lang.raw("recruitment.sort_2")
                else -> lang.raw("recruitment.sort_0")
            },
        ),
        RecruitmentIcons.SORT,
    ) {
        sorting = (sorting + 1) % SORT_MODE_COUNT
        resetBrowse()
    }
}

private fun RecruitmentController.resetButton(): RecruitmentButton {
    return RecruitmentButton(lang.raw("recruitment.reset"), icon = RecruitmentIcons.CANCEL) {
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
    }
}

internal fun RecruitmentController.filters(): List<RecruitmentButton> {
    return listOf(
        RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), RecruitmentIcons.NAV_SETTINGS) {
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
            RecruitmentIcons.NAV_SETTINGS,
        ) {
            recruitingOnly = !recruitingOnly
            refresh()
        },
        RecruitmentButton(
            lang.raw("recruitment.filter_beginner"),
            listOf(enabled(beginnersOnly)),
            RecruitmentIcons.NAV_SETTINGS,
        ) {
            beginnersOnly = !beginnersOnly
            refresh()
        },
        RecruitmentButton(
            lang.raw("recruitment.method"),
            listOf(joiningMethod?.let(::mode) ?: lang.raw("recruitment.any")),
            RecruitmentIcons.NAV_SETTINGS,
        ) {
            joiningMethod =
                when (joiningMethod) {
                    null -> RecruitmentMode.JOIN_IMMEDIATELY
                    RecruitmentMode.CLOSED -> null
                    else -> RecruitmentMode.entries[requireNotNull(joiningMethod).ordinal + 1]
                }
            refresh()
        },
        RecruitmentButton(lang.raw("recruitment.filter_language"), listOf(languageFilter), RecruitmentIcons.TAG) {
            ask(lang.raw("recruitment.filter_language"), languageFilter) {
                languageFilter = it.take(LANGUAGE_FILTER_LIMIT)
                browsePage = 0
                refresh()
            }
        },
        RecruitmentButton(lang.raw("recruitment.filter_times"), listOf(timeFilter), RecruitmentIcons.CREATED) {
            ask(lang.raw("recruitment.filter_times"), timeFilter) {
                timeFilter = it.take(PLAY_TIME_FILTER_LIMIT)
                browsePage = 0
                refresh()
            }
        },
    )
}

private fun RecruitmentController.toggleDiscordFilter() {
    joiningMethod = if (joiningMethod == RecruitmentMode.DISCORD) null else RecruitmentMode.DISCORD
}
