package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import java.util.UUID

/** Public listing fields and settings authorization are separate from private application decisions. */
internal class GuildRecruitmentListings(
    private val directory: RecruitmentDirectoryRepository,
    private val guilds: GuildService,
    private val members: MemberService,
    private val maximumMembers: () -> Int = { Int.MAX_VALUE },
) {
    fun browse(): List<Guild> =
        guilds.getAllGuilds().filter {
            profile(
                it,
            ).publicListing
        }.sortedBy { it.name.lowercase() }

    fun savePresentation(
        actor: UUID,
        guildId: UUID,
        change: (RecruitmentProfile) -> RecruitmentProfile,
    ) {
        val guild = requireNotNull(guilds.getGuild(guildId))
        require(
            guilds.hasPermission(actor, guildId, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS),
        )
        val current = profile(guild)
        val next = change(current)
        require(
            next.guildId == guildId && next.copy(
                publicListing = current.publicListing, joinAnnouncements = current.joinAnnouncements,
                showBanner = current.showBanner, questReminders = current.questReminders,
            ) == current,
        ) { "Only presentation preferences may change" }
        val changed =
            buildSet {
                if (next.publicListing != current.publicListing) add("publicListing")
                if (next.joinAnnouncements != current.joinAnnouncements) add("joinAnnouncements")
                if (next.showBanner != current.showBanner) add("showBanner")
                if (next.questReminders != current.questReminders) add("questReminders")
            }
        if (changed.isNotEmpty()) directory.savePresentation(next, changed)
    }

    fun profile(guild: Guild): RecruitmentProfile =
        directory.profile(guild.id) ?: RecruitmentProfile.legacy(guild.id, guild.isOpen)

    fun spaces(guild: UUID): Int =
        (minOf(members.getMemberLimit(guild), maximumMembers()) - members.getMemberCount(guild)).coerceAtLeast(
            0,
        )

    fun saveProfile(
        actor: UUID,
        profile: RecruitmentProfile,
    ) {
        require(guilds.getGuild(profile.guildId) != null)
        require(GuildRecruitmentPermissions.canEditSettings(members, actor, profile.guildId)) {
            "Recruitment settings permission required"
        }
        directory.save(profile)
    }
}
