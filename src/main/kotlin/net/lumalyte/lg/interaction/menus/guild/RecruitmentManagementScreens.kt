package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.recruitment.RecruitmentMode

private const val GENERAL_SETUP_FIELDS = 5
private const val DISCORD_SETUP_FIELDS = 7
private const val PLAY_TIMES_STEP = 3
private const val LANGUAGE_STEP = 4
private const val DISCORD_INVITE_STEP = 5

/** Management presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.management(): List<RecruitmentButton> {
    val guild = ownGuild() ?: return browse()
    return buildList {
        add(
            RecruitmentButton(lang.raw("recruitment.preview"), details(guild), RecruitmentIcons.NAV_INFO, guild) {
                selected = guild.id
                showScreen(RecruitmentScreen.PROFILE)
            },
        )
        if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
            add(
                RecruitmentButton(
                    lang.raw("recruitment.inbox"),
                    icon = RecruitmentIcons.NAV_MEMBERS,
                ) { showScreen(RecruitmentScreen.INBOX) },
            )
        }
        if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
            add(
                RecruitmentButton(lang.raw("recruitment.setup"), icon = RecruitmentIcons.TAG) {
                    setupDraft = service.listings.profile(guild).copy(mode = RecruitmentMode.CLOSED)
                    showScreen(RecruitmentScreen.SETUP)
                },
            )
            add(
                RecruitmentButton(
                    lang.raw("recruitment.settings"),
                    icon = RecruitmentIcons.NAV_SETTINGS,
                ) { showScreen(RecruitmentScreen.SETTINGS) },
            )
        }
        add(
            RecruitmentButton(
                lang.raw("recruitment.browse"),
                icon = RecruitmentIcons.NAV_INFO,
            ) { showScreen(RecruitmentScreen.BROWSE) },
        )
        add(
            RecruitmentButton(
                lang.raw("recruitment.mine"),
                icon = RecruitmentIcons.DESCRIPTION,
            ) { showScreen(RecruitmentScreen.MINE) },
        )
    }
}

internal fun RecruitmentController.setupMethods(): List<RecruitmentButton> {
    return RecruitmentMode.entries.map { method ->
        RecruitmentButton(mode(method), icon = RecruitmentIcons.NAV_SETTINGS) {
            val guild = requireNotNull(ownGuild())
            require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
            setupMode = method
            setupInput(0)
        }
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
    val count = if (setupMode == RecruitmentMode.DISCORD) DISCORD_SETUP_FIELDS else GENERAL_SETUP_FIELDS
    if (step == count) {
        showScreen(RecruitmentScreen.SETUP_PREVIEW)
        return
    }
    ask("${step + 1}/$count ${fields[step].first}", fields[step].second) { value ->
        val current = requireNotNull(setupDraft)
        setupDraft =
            when (step) {
                0 -> current.copy(pitch = value)
                1 -> current.copy(requirements = value)
                2 -> current.copy(interests = value.split(',').map(String::trim).filter(String::isNotBlank).toSet())
                PLAY_TIMES_STEP -> current.copy(preferredTimes = value)
                LANGUAGE_STEP -> current.copy(language = value)
                DISCORD_INVITE_STEP -> current.copy(discordInvite = value.ifBlank { null })
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
        listingPreview(guild, draft),
        setupBeginnerToggle(draft),
        setupPublishButton(draft),
        setupDiscardButton(),
    )
}

private fun RecruitmentController.listingPreview(
    guild: net.lumalyte.lg.domain.entities.Guild,
    draft: net.lumalyte.lg.domain.recruitment.RecruitmentProfile,
): RecruitmentButton {
    return RecruitmentButton(
        lang.raw("recruitment.preview"),
        draft.setupDetails(mode(setupMode)),
        RecruitmentIcons.NAV_INFO,
        guild,
    )
}

private fun net.lumalyte.lg.domain.recruitment.RecruitmentProfile.setupDetails(method: String): List<String> {
    return listOf(
        method,
        pitch,
        requirements,
        interests.joinToString(", "),
        preferredTimes,
        language,
        discordInvite ?: "",
        discordInstructions,
    ).filter(String::isNotBlank)
}

private fun RecruitmentController.setupBeginnerToggle(
    draft: net.lumalyte.lg.domain.recruitment.RecruitmentProfile,
): RecruitmentButton {
    return RecruitmentButton(
        lang.raw("recruitment.beginner"),
        listOf(enabled(draft.beginnerFriendly)),
        RecruitmentIcons.TOGGLE_ON,
    ) {
        setupDraft = draft.copy(beginnerFriendly = !draft.beginnerFriendly)
        refresh()
    }
}

private fun RecruitmentController.setupPublishButton(
    draft: net.lumalyte.lg.domain.recruitment.RecruitmentProfile,
): RecruitmentButton {
    return RecruitmentButton(lang.raw("recruitment.publish"), icon = RecruitmentIcons.CONFIRM) {
        if (setupMode == RecruitmentMode.DISCORD &&
            (draft.discordInvite == null || draft.discordInstructions.isBlank())
        ) {
            player.sendMessage(lang.msg("recruitment.discord_setup"))
            setupInput(DISCORD_INVITE_STEP)
        } else {
            service.listings.saveProfile(player.uniqueId, draft.copy(mode = setupMode))
            setupDraft = null
            showScreen(RecruitmentScreen.MANAGE)
        }
    }
}

private fun RecruitmentController.setupDiscardButton(): RecruitmentButton {
    return RecruitmentButton(lang.raw("recruitment.discard"), icon = RecruitmentIcons.CANCEL) {
        setupDraft = null
        showScreen(RecruitmentScreen.MANAGE)
    }
}
