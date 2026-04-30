package net.lumalyte.lg.infrastructure.web.handlers

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.domain.entities.LeaderboardType

import net.lumalyte.lg.infrastructure.web.dto.BannerDto
import net.lumalyte.lg.infrastructure.web.dto.BannerPatternDto
import net.lumalyte.lg.infrastructure.web.dto.GuildLeaderboardEntryDto
import net.lumalyte.lg.infrastructure.web.dto.GuildLeaderboardResponse
import java.time.Instant

class GuildLeaderboardHandler(
    private val leaderboardService: LeaderboardService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val bannerService: GuildBannerService,
    private val progressionRepository: ProgressionRepository,
    private val config: WebApiConfig
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    fun build(periodParam: String?, limitParam: String?): GuildLeaderboardResponse {
        val period = parsePeriod(periodParam)
        val limit = parseLimit(limitParam)

        val entries = leaderboardService
            .getTopEntities(LeaderboardType.LEVEL, period, limit)
            .mapNotNull { entry ->
                val guild = guildService.getGuild(entry.entityId) ?: return@mapNotNull null
                val progression = progressionRepository.getGuildProgression(guild.id)
                val members = memberService.getGuildMembers(guild.id)
                val topMembers = members
                    .sortedBy { it.joinedAt }
                    .take(config.topMembersPerGuild)
                    .map { it.playerId.toString() }
                val banner = bannerService.getGuildBanner(guild.id)?.designData?.let { design ->
                    BannerDto(
                        baseColor = design.baseColor.name,
                        baseColorHex = design.baseColor.hexCode,
                        patterns = design.patterns.map {
                            BannerPatternDto(
                                type = it.type,
                                color = it.color.name,
                                colorHex = it.color.hexCode
                            )
                        }
                    )
                }

                GuildLeaderboardEntryDto(
                    rank = entry.rank,
                    id = guild.id.toString(),
                    name = guild.name,
                    tag = guild.tag,
                    tagPlain = guild.tag?.let { stripFormatting(it) },
                    level = guild.level,
                    totalExperience = progression?.totalExperience ?: 0,
                    experienceThisLevel = progression?.experienceThisLevel ?: 0,
                    experienceForNextLevel = progression?.experienceForNextLevel ?: 0,
                    memberCount = members.size,
                    topMemberUuids = topMembers,
                    banner = banner,
                    createdAt = guild.createdAt.toString()
                )
            }

        return GuildLeaderboardResponse(
            type = ExtendedLeaderboardType.GUILD_LEVEL.name,
            period = period.name,
            generatedAt = Instant.now().toString(),
            count = entries.size,
            entries = entries
        )
    }

    private fun stripFormatting(tag: String): String =
        try {
            plainSerializer.serialize(miniMessage.deserialize(tag))
        } catch (_: Exception) {
            tag
        }

    private fun parsePeriod(raw: String?): LeaderboardPeriod {
        if (raw.isNullOrBlank()) return LeaderboardPeriod.ALL_TIME
        return runCatching { LeaderboardPeriod.valueOf(raw.uppercase()) }
            .getOrDefault(LeaderboardPeriod.ALL_TIME)
    }

    private fun parseLimit(raw: String?): Int {
        val requested = raw?.toIntOrNull() ?: config.leaderboardLimitDefault
        return requested.coerceIn(1, config.leaderboardLimitMax)
    }
}
