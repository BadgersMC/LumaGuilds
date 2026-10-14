package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile

/** Settings presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.settings(): List<RecruitmentButton> {
    val guild = requireNotNull(ownGuild())
    require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
    val profile = service.profile(guild)

    fun edit(label: String, value: String, change: (RecruitmentProfile, String) -> RecruitmentProfile) =
        RecruitmentButton(label, listOf(value), "lg_tag") {
            ask(
                label,
                value,
            ) {
                service.saveProfile(
                    player.uniqueId,
                    change(service.profile(requireNotNull(guilds.getGuild(guild.id))), it),
                )
            }
        }
    return listOf(
        RecruitmentButton(lang.raw("recruitment.method"), listOf(mode(profile.mode)), "lg_nav_settings") {
            go(RecruitmentScreen.METHOD)
        },
        edit(lang.raw("recruitment.pitch"), profile.pitch) { current, value -> current.copy(pitch = value) },
        edit(
            lang.raw("recruitment.requirements"),
            profile.requirements,
        ) { current, value -> current.copy(requirements = value) },
        edit(
            lang.raw("recruitment.interests"),
            profile.interests.joinToString(","),
        ) { current, value ->
            current.copy(
                interests = value.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
            )
        },
        edit(
            lang.raw("recruitment.times"),
            profile.preferredTimes,
        ) { current, value -> current.copy(preferredTimes = value) },
        edit(
            lang.raw("recruitment.language"),
            profile.language,
        ) { current, value -> current.copy(language = value) },
        RecruitmentButton(
            lang.raw("recruitment.beginner"),
            listOf(enabled(profile.beginnerFriendly)),
            "lg_toggle_on",
        ) {
            val current = service.profile(requireNotNull(guilds.getGuild(guild.id)))
            service.saveProfile(player.uniqueId, current.copy(beginnerFriendly = !current.beginnerFriendly))
            refresh()
        },
        edit(
            lang.raw("recruitment.discord"),
            profile.discordInvite ?: "",
        ) { current, value ->
            current.copy(
                discordInvite = value.ifBlank {
                    null
                },
            )
        },
        edit(
            lang.raw("recruitment.instructions"),
            profile.discordInstructions,
        ) { current, value -> current.copy(discordInstructions = value) },
    )
}

internal fun RecruitmentController.methodSettings(): List<RecruitmentButton> = RecruitmentMode.entries.map { method ->
    RecruitmentButton(mode(method), icon = "lg_nav_settings") {
        val guild = requireNotNull(ownGuild())
        val profile = service.profile(guild)
        if (method == RecruitmentMode.DISCORD &&
            (profile.discordInvite == null || profile.discordInstructions.isBlank())
        ) {
            player.sendMessage(lang.msg("recruitment.discord_setup"))
            go(RecruitmentScreen.SETTINGS)
            return@RecruitmentButton
        }
        service.saveProfile(player.uniqueId, profile.copy(mode = method))
        go(RecruitmentScreen.SETTINGS)
    }
}
