package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile

/** Browse presentation builders share the current authorized navigation session. */
internal fun RecruitmentController.matchesAvailability(guild: Guild, profile: RecruitmentProfile): Boolean =
    (peaceful == null || peaceful == (guild.mode == GuildMode.PEACEFUL)) &&
        (!recruitingOnly || (profile.mode != RecruitmentMode.CLOSED && service.spaces(guild.id) > 0)) &&
        (joiningMethod == null || joiningMethod == profile.mode)

internal fun RecruitmentController.matchesPreferences(guild: Guild, profile: RecruitmentProfile): Boolean =
    (!beginnersOnly || profile.beginnerFriendly) &&
        profile.language.contains(languageFilter, true) && profile.preferredTimes.contains(timeFilter, true) &&
        (interest == null || profile.interests.any { it.equals(interest, true) }) &&
        (guild.name + " " + profile.pitch + " " + profile.interests.joinToString(" ")).contains(query, true)

internal fun RecruitmentController.browse(): List<RecruitmentButton> = service.browse().filter { guild ->
    val profile = service.profile(guild)
    matchesAvailability(guild, profile) && matchesPreferences(guild, profile)
}.sortedWith(
    when (sorting) {
        1 -> compareByDescending<Guild> { it.level }.thenBy { it.name.lowercase() }
        2 -> compareBy<Guild> { it.name.lowercase() }
        else ->
            compareByDescending<Guild> {
                service.profile(it).mode != RecruitmentMode.CLOSED && service.spaces(it.id) > 0
            }.thenByDescending { guild ->
                members.getGuildMembers(guild.id).count { member ->
                    org.bukkit.Bukkit.getPlayer(member.playerId)?.let { it.isOnline && player.canSee(it) } == true &&
                        GuildRecruitmentPermissions.canReview(members, member.playerId, guild.id)
                }
            }.thenBy { it.name.lowercase() }
    },
).map { guild ->
    RecruitmentButton(guild.name, details(guild), "lg_nav_info", guild) {
        selectedApplication = null
        selected = guild.id
        go(RecruitmentScreen.PROFILE)
    }
}
