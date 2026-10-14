package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.recruitment.RecruitmentMode

/** Management presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.management(): List<RecruitmentButton> {
    val guild = ownGuild() ?: return browse()
    return buildList {
        add(
            RecruitmentButton(lang.raw("recruitment.preview"), details(guild), "lg_nav_info", guild) {
                selected = guild.id
                go(RecruitmentScreen.PROFILE)
            },
        )
        if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
            add(
                RecruitmentButton(
                    lang.raw("recruitment.inbox"),
                    icon = "lg_nav_members",
                ) { go(RecruitmentScreen.INBOX) },
            )
        }
        if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
            add(
                RecruitmentButton(lang.raw("recruitment.setup"), icon = "lg_tag") {
                    setupDraft = service.profile(guild).copy(mode = RecruitmentMode.CLOSED)
                    go(RecruitmentScreen.SETUP)
                },
            )
            add(
                RecruitmentButton(
                    lang.raw("recruitment.settings"),
                    icon = "lg_nav_settings",
                ) { go(RecruitmentScreen.SETTINGS) },
            )
        }
        add(
            RecruitmentButton(
                lang.raw("recruitment.browse"),
                icon = "lg_nav_info",
            ) { go(RecruitmentScreen.BROWSE) },
        )
        add(RecruitmentButton(lang.raw("recruitment.mine"), icon = "lg_description") { go(RecruitmentScreen.MINE) })
    }
}

internal fun RecruitmentController.setupMethods(): List<RecruitmentButton> = RecruitmentMode.entries.map { method ->
    RecruitmentButton(mode(method), icon = "lg_nav_settings") {
        val guild = requireNotNull(ownGuild())
        require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
        setupMode = method
        setupInput(0)
    }
}

internal fun RecruitmentController.setupInput(step: Int) {
    val draft = requireNotNull(setupDraft)
    val fields =
        listOf(
            lang.raw("recruitment.pitch") to draft.pitch,
            lang.raw("recruitment.requirements") to draft.requirements,
            lang.raw("recruitment.interests") to draft.interests.joinToString(","),
            lang.raw("recruitment.times") to draft.preferredTimes,
            lang.raw("recruitment.language") to draft.language,
            lang.raw("recruitment.discord") to (draft.discordInvite ?: ""),
            lang.raw("recruitment.instructions") to draft.discordInstructions,
        )
    val count = if (setupMode == RecruitmentMode.DISCORD) 7 else 5
    if (step == count) {
        go(RecruitmentScreen.SETUP_PREVIEW)
        return
    }
    ask("${step + 1}/$count ${fields[step].first}", fields[step].second) { value ->
        val current = requireNotNull(setupDraft)
        setupDraft =
            when (step) {
                0 -> current.copy(pitch = value)
                1 -> current.copy(requirements = value)
                2 -> current.copy(interests = value.split(',').map(String::trim).filter(String::isNotBlank).toSet())
                3 -> current.copy(preferredTimes = value)
                4 -> current.copy(language = value)
                5 -> current.copy(discordInvite = value.ifBlank { null })
                else -> current.copy(discordInstructions = value)
            }
        setupInput(step + 1)
    }
}

internal fun RecruitmentController.setupPreview(): List<RecruitmentButton> {
    val guild = requireNotNull(ownGuild())
    require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
    val draft = requireNotNull(setupDraft)
    return listOf(
        RecruitmentButton(
            lang.raw("recruitment.preview"),
            listOf(
                mode(setupMode),
                draft.pitch,
                draft.requirements,
                draft.interests.joinToString(", "),
                draft.preferredTimes,
                draft.language,
                draft.discordInvite ?: "",
                draft.discordInstructions,
            ).filter(String::isNotBlank),
            "lg_nav_info",
            guild,
        ),
        RecruitmentButton(
            lang.raw("recruitment.beginner"),
            listOf(enabled(draft.beginnerFriendly)),
            "lg_toggle_on",
        ) {
            setupDraft = draft.copy(beginnerFriendly = !draft.beginnerFriendly)
            refresh()
        },
        RecruitmentButton(lang.raw("recruitment.publish"), icon = "lg_confirm") {
            if (setupMode == RecruitmentMode.DISCORD &&
                (draft.discordInvite == null || draft.discordInstructions.isBlank())
            ) {
                player.sendMessage(lang.msg("recruitment.discord_setup"))
                setupInput(5)
            } else {
                service.saveProfile(player.uniqueId, draft.copy(mode = setupMode))
                setupDraft = null
                go(RecruitmentScreen.MANAGE)
            }
        },
        RecruitmentButton(lang.raw("recruitment.discard"), icon = "lg_cancel") {
            setupDraft = null
            go(RecruitmentScreen.MANAGE)
        },
    )
}
