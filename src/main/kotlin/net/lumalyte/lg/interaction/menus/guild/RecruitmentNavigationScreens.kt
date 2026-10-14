package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.GuildApplication

private const val SEARCH_QUERY_LIMIT = 64

/** Navigation presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.go(next: RecruitmentScreen) {
    if (screen == RecruitmentScreen.BROWSE) browsePage = page
    screen = next
    page = if (next == RecruitmentScreen.BROWSE) browsePage else 0
    refresh()
}

internal fun RecruitmentController.ask(label: String, value: String = "", accept: (String) -> Unit) {
    input = RecruitmentInput(label, value, accept)
    refresh()
}

internal fun RecruitmentController.ownGuild(): Guild? = guilds.getPlayerGuilds(player.uniqueId).firstOrNull()

internal fun RecruitmentController.selectedGuild(): Guild = requireNotNull(selected?.let(guilds::getGuild))

internal fun RecruitmentController.selectedOwnApplication(): GuildApplication =
    requireNotNull(
        service.mine(player.uniqueId).firstOrNull {
            it.id == selectedApplication
        },
    )

internal fun RecruitmentController.resetBrowse() {
    page = 0
    browsePage = 0
    refresh()
}

internal fun RecruitmentController.navigation(): List<RecruitmentButton> {
    val navigation =
        mutableListOf(
            RecruitmentButton(
                lang.raw("recruitment.browse"),
                icon = RecruitmentIcons.BACK,
            ) { go(RecruitmentScreen.BROWSE) },
            RecruitmentButton(
                lang.raw("recruitment.mine"),
                icon = RecruitmentIcons.DESCRIPTION,
            ) { go(RecruitmentScreen.MINE) },
            RecruitmentButton(lang.raw("recruitment.search"), listOf(query), RecruitmentIcons.TAG) {
                ask(lang.raw("recruitment.search"), query) {
                    query = it.take(SEARCH_QUERY_LIMIT)
                    browsePage = 0
                    go(RecruitmentScreen.BROWSE)
                }
            },
            RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), RecruitmentIcons.NAV_SETTINGS) {
                go(RecruitmentScreen.FILTER)
            },
            RecruitmentButton(
                lang.raw("recruitment.reminders"),
                listOf(enabled(directory.remindersEnabled(player.uniqueId))),
                RecruitmentIcons.TOGGLE_ON,
            ) {
                directory.setReminders(player.uniqueId, !directory.remindersEnabled(player.uniqueId))
                refresh()
            },
        )
    ownGuild()?.let { guild ->
        navigation.removeAt(1)
        navigation.add(
            1,
            RecruitmentButton(lang.raw("recruitment.manage"), icon = RecruitmentIcons.NAV_MEMBERS) {
                go(RecruitmentScreen.MANAGE)
            },
        )
        if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
            navigation +=
                RecruitmentButton(lang.raw("recruitment.inbox"), icon = RecruitmentIcons.NAV_MEMBERS) {
                    go(RecruitmentScreen.INBOX)
                }
        }
        if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
            navigation +=
                RecruitmentButton(lang.raw("recruitment.settings"), icon = RecruitmentIcons.NAV_SETTINGS) {
                    go(RecruitmentScreen.SETTINGS)
                }
        }
    }
    return navigation
}
