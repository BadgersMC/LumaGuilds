package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.GuildApplication

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

internal fun RecruitmentController.selectedOwnApplication(): GuildApplication = requireNotNull(
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
            RecruitmentButton(lang.raw("recruitment.browse"), icon = "lg_back") { go(RecruitmentScreen.BROWSE) },
            RecruitmentButton(lang.raw("recruitment.mine"), icon = "lg_description") { go(RecruitmentScreen.MINE) },
            RecruitmentButton(lang.raw("recruitment.search"), listOf(query), "lg_tag") {
                ask(lang.raw("recruitment.search"), query) {
                    query = it.take(64)
                    browsePage = 0
                    go(RecruitmentScreen.BROWSE)
                }
            },
            RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_nav_settings") {
                go(RecruitmentScreen.FILTER)
            },
            RecruitmentButton(
                lang.raw("recruitment.reminders"),
                listOf(enabled(directory.remindersEnabled(player.uniqueId))),
                "lg_toggle_on",
            ) {
                directory.setReminders(player.uniqueId, !directory.remindersEnabled(player.uniqueId))
                refresh()
            },
        )
    ownGuild()?.let { guild ->
        navigation.removeAt(1)
        navigation.add(
            1,
            RecruitmentButton(lang.raw("recruitment.manage"), icon = "lg_nav_members") {
                go(RecruitmentScreen.MANAGE)
            },
        )
        if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
            navigation += RecruitmentButton(lang.raw("recruitment.inbox"), icon = "lg_nav_members") {
                go(RecruitmentScreen.INBOX)
            }
        }
        if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
            navigation += RecruitmentButton(lang.raw("recruitment.settings"), icon = "lg_nav_settings") {
                go(RecruitmentScreen.SETTINGS)
            }
        }
    }
    return navigation
}
