package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile

/** Browse presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.matchesAvailability(guild: Guild, profile: RecruitmentProfile): Boolean {
    return (peaceful == null || peaceful == (guild.mode == GuildMode.PEACEFUL)) &&
        (!recruitingOnly || profile.mode != RecruitmentMode.CLOSED && service.listings.spaces(guild.id) > 0) &&
        (joiningMethod == null || joiningMethod == profile.mode)
}

internal fun RecruitmentController.matchesPreferences(guild: Guild, profile: RecruitmentProfile): Boolean {
    return (!beginnersOnly || profile.beginnerFriendly) &&
        profile.language.contains(languageFilter, true) && profile.preferredTimes.contains(timeFilter, true) &&
        (interest == null || profile.interests.any { it.equals(interest, true) }) &&
        (guild.name + " " + profile.pitch + " " + profile.interests.joinToString(" ")).contains(query, true)
}

internal fun RecruitmentController.browse(): List<RecruitmentButton> {
    return service.listings.browse().filter { guild ->
        val profile = service.listings.profile(guild)
        matchesAvailability(guild, profile) && matchesPreferences(guild, profile)
    }
        .sortedWith(
            when (sorting) {
                1 -> compareByDescending<Guild> { it.level }.thenBy { it.name.lowercase() }
                2 -> compareBy<Guild> { it.name.lowercase() }
                else ->
                    compareByDescending<Guild> {
                        service.listings.profile(it).mode != RecruitmentMode.CLOSED &&
                            service.listings.spaces(it.id) > 0
                    }
                        .thenByDescending { guild ->
                            members.getGuildMembers(guild.id).count { member ->
                                visibleRecruiter(member.playerId) &&
                                    GuildRecruitmentPermissions.canReview(members, member.playerId, guild.id)
                            }
                        }
                        .thenBy { it.name.lowercase() }
            },
        )
        .map { guild ->
            RecruitmentButton(guild.name, details(guild), RecruitmentIcons.NAV_INFO, guild) {
                selectedApplication = null
                selected = guild.id
                showScreen(RecruitmentScreen.PROFILE)
            }
        }
}

private fun RecruitmentController.visibleRecruiter(id: java.util.UUID): Boolean {
    return org.bukkit.Bukkit.getPlayer(id)?.let {
        it.isOnline && player.canSee(it)
    } == true
}
