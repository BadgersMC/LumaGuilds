package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.RecruitmentMode

/** Profiles presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.details(guild: Guild): List<String> {
    val profile = service.profile(guild)
    val fee = lfg.getJoinRequirement(guild)
    return listOf(
        mode(profile.mode),
        lang.raw("recruitment.spaces") + ": " + service.spaces(guild.id),
        lang.raw("recruitment.level") + ": " + guild.level,
        lang.raw("recruitment.fee") + ": " + (
            fee?.let {
                "${it.amount} ${it.currencyName}"
            } ?: if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
                lang.raw("recruitment.fee_unavailable")
            } else {
                "0"
            }
        ),
        profile.pitch, profile.requirements,
        profile.interests.joinToString(
            ", ",
        ),
        profile.preferredTimes, profile.language,
        lang.raw("recruitment.listing_updated") +
            (directory.updatedAt(guild.id)?.toString() ?: lang.raw("recruitment.unknown")),
        if (profile.beginnerFriendly) lang.raw("recruitment.beginner") else "",
    ).filter(String::isNotBlank)
}

internal fun RecruitmentController.profile(): List<RecruitmentButton> {
    val guild = selectedGuild()
    val profile = service.profile(guild)
    val items = mutableListOf(RecruitmentButton(guild.name, details(guild), RecruitmentIcons.NAV_INFO, guild))
    items +=
        RecruitmentButton(lang.raw("recruitment.guide"), icon = RecruitmentIcons.DESCRIPTION) {
            val current = selectedGuild()
            val navigationValid = returnGuard()
            net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.book(
                player, current.name, details(current), lang.raw("recruitment.return_menu"),
                { navigationValid() && selected == current.id && screen == RecruitmentScreen.PROFILE },
                { run { refresh() } },
            )
        }
    if (service.spaces(guild.id) > 0 && members.getPlayerGuilds(player.uniqueId).isEmpty()) {
        when (profile.mode) {
            RecruitmentMode.JOIN_IMMEDIATELY ->
                items +=
                    RecruitmentButton(mode(profile.mode), icon = RecruitmentIcons.CONFIRM) {
                        quotedGuild = guild
                        quotedFee = lfg.getJoinRequirement(guild)
                        go(RecruitmentScreen.CONFIRM)
                    }
            RecruitmentMode.IN_GAME ->
                items +=
                    RecruitmentButton(mode(profile.mode), icon = RecruitmentIcons.DESCRIPTION) {
                        ask(lang.raw("recruitment.introduction")) {
                            service.submit(player.uniqueId, guild.id, it)
                            go(RecruitmentScreen.MINE)
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
                go(RecruitmentScreen.DISCORD)
            }
    }
    return items
}

internal fun RecruitmentController.discord(): List<RecruitmentButton> {
    val guild = selectedGuild()
    val profile = service.profile(guild)
    val invite = requireNotNull(profile.discordInvite)
    return listOf(
        RecruitmentButton(
            lang.raw("recruitment.discord"),
            listOf(invite, profile.discordInstructions, lang.raw("recruitment.discord_notice")),
            RecruitmentIcons.INVITE,
        ),
        RecruitmentButton(lang.raw("recruitment.discord_confirm"), listOf(invite), RecruitmentIcons.CONFIRM) {
            require(service.profile(selectedGuild()).discordInvite == invite) { "Invite changed" }
            player.closeInventory()
            player.sendMessage(Component.text(invite).clickEvent(ClickEvent.openUrl(invite)))
        },
    )
}
