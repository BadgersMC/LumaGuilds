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
    fun browse(): List<Guild> = guilds.getAllGuilds().sortedBy { it.name.lowercase() }

    fun profile(guild: Guild): RecruitmentProfile =
        directory.profile(guild.id) ?: RecruitmentProfile.legacy(guild.id, guild.isOpen)

    fun spaces(guild: UUID): Int =
        (minOf(members.getMemberLimit(guild), maximumMembers()) - members.getMemberCount(guild)).coerceAtLeast(
            0,
        )

    fun saveProfile(actor: UUID, profile: RecruitmentProfile) {
        require(guilds.getGuild(profile.guildId) != null)
        require(GuildRecruitmentPermissions.canEditSettings(members, actor, profile.guildId)) {
            "Recruitment settings permission required"
        }
        directory.save(profile)
    }
}
