package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.RecruitmentMode

/** Profiles presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.details(guild: Guild): List<String> {
    val profile = service.listings.profile(guild)
    val fee = lfg.getJoinRequirement(guild)
    val feeText =
        fee?.let { "${it.amount} ${it.currencyName}" }
            ?: if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
                lang.raw("recruitment.fee_unavailable")
            } else {
                lang.raw("guild_dialogs.board.no_fee")
            }
    return buildList {
        add(mode(profile.mode))
        add(lang.raw("recruitment.spaces") + ": " + service.listings.spaces(guild.id))
        add(lang.raw("recruitment.level") + ": " + guild.level)
        add(lang.raw("recruitment.fee") + ": " + feeText)
        if (profile.pitch.isNotBlank()) {
            add("")
            add(profile.pitch)
        }
        val fields =
            listOf(
                lang.raw("recruitment.profile_fields.requirements") to profile.requirements,
                lang.raw("recruitment.profile_fields.interests") to profile.interests.joinToString(", "),
                lang.raw("recruitment.profile_fields.times") to profile.preferredTimes,
                lang.raw("recruitment.profile_fields.language") to profile.language,
            ).filter { it.second.isNotBlank() }
        if (fields.isNotEmpty()) add("")
        fields.forEach { (key, value) -> add(key + ": " + value) }
        if (profile.beginnerFriendly) add(lang.raw("recruitment.beginner"))
        add("")
        add(
            lang.raw("recruitment.listing_updated") +
                (directory.updatedAt(guild.id)?.let(::recruitmentListingTime) ?: lang.raw("recruitment.unknown")),
        )
    }
}

internal fun RecruitmentController.profile(): List<RecruitmentButton> {
    val guild = selectedGuild()
    val profile = service.listings.profile(guild)
    val items =
        mutableListOf(
            RecruitmentButton(
                guild.name,
                details(guild),
                RecruitmentIcons.NAV_INFO,
                guild,
                showBanner = profile.showBanner,
            ),
        )
    items +=
        RecruitmentButton(lang.raw("recruitment.guide"), icon = RecruitmentIcons.DESCRIPTION) {
            val current = selectedGuild()
            val navigationValid = returnGuard()
            net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.book(
                player,
                net.lumalyte.lg.infrastructure.services.RecruitmentBookContent(
                    current.name, details(current), lang.raw("recruitment.return_profile"), theme(), current,
                ),
                { navigationValid() && selected == current.id && screen == RecruitmentScreen.PROFILE },
                { run { refresh() } },
            )
        }
    if (service.listings.spaces(guild.id) > 0 && members.getPlayerGuilds(player.uniqueId).isEmpty()) {
        when (profile.mode) {
            RecruitmentMode.JOIN_IMMEDIATELY ->
                items +=
                    RecruitmentButton(mode(profile.mode), icon = RecruitmentIcons.CONFIRM) {
                        quotedGuild = guild
                        quotedFee = lfg.getJoinRequirement(guild)
                        showScreen(RecruitmentScreen.CONFIRM)
                    }
            RecruitmentMode.IN_GAME ->
                items +=
                    RecruitmentButton(mode(profile.mode), icon = RecruitmentIcons.DESCRIPTION) {
                        ask(lang.raw("recruitment.introduction")) {
                            service.submit(player.uniqueId, guild.id, it)
                            showScreen(RecruitmentScreen.MINE)
                        }
                    }
            // The optional Discord button below opens exact-destination confirmation.
            RecruitmentMode.DISCORD -> Unit
            RecruitmentMode.CLOSED -> Unit
        }
    }
    if (profile.discordInvite != null) {
        items +=
            RecruitmentButton(lang.raw("recruitment.discord"), icon = RecruitmentIcons.INVITE) {
                showScreen(RecruitmentScreen.DISCORD)
            }
    }
    return items
}

internal fun RecruitmentController.discord(): List<RecruitmentButton> {
    val guild = selectedGuild()
    val profile = service.listings.profile(guild)
    val invite = requireNotNull(profile.discordInvite)
    return listOf(
        RecruitmentButton(
            lang.raw("recruitment.discord"),
            listOf(invite, profile.discordInstructions, lang.raw("recruitment.discord_notice")),
            RecruitmentIcons.INVITE,
        ),
        RecruitmentButton(lang.raw("recruitment.discord_confirm"), listOf(invite), RecruitmentIcons.CONFIRM) {
            require(service.listings.profile(selectedGuild()).discordInvite == invite) { "Invite changed" }
            player.sendMessage(Component.text(invite).clickEvent(ClickEvent.openUrl(invite)))
            refresh()
        },
    )
}

/** Stable UTC display; retain the stored Instant without exposing nanosecond precision. */
internal fun recruitmentListingTime(at: java.time.Instant): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
        .withZone(java.time.ZoneOffset.UTC).format(at)
