package net.lumalyte.lg.infrastructure.web.handlers

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.infrastructure.web.dto.BannerDto
import net.lumalyte.lg.infrastructure.web.dto.BannerPatternDto
import net.lumalyte.lg.infrastructure.web.dto.GuildLeaderboardEntryDto
import net.lumalyte.lg.infrastructure.web.dto.GuildLeaderboardResponse
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class GuildLeaderboardHandler(
    private val leaderboardService: LeaderboardService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val bannerService: GuildBannerService,
    private val progressionRepository: ProgressionRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val bankService: BankService,
    private val config: WebApiConfig
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    enum class Category(val id: String) {
        LEVEL("level"),
        BALANCE("balance"),
        ACTIVITY("activity"),
        MEMBERS("members"),
        AGE("age");

        companion object {
            fun parse(raw: String?): Category {
                if (raw.isNullOrBlank()) return LEVEL
                val v = raw.lowercase()
                return entries.firstOrNull { it.id == v } ?: LEVEL
            }
        }
    }

    fun build(typeParam: String?, periodParam: String?, limitParam: String?): GuildLeaderboardResponse {
        val category = Category.parse(typeParam)
        val period = parsePeriod(periodParam)
        val limit = parseLimit(limitParam)

        val ranked = rankGuilds(category, limit)

        val entries = ranked.mapIndexedNotNull { idx, (guildId, score) ->
            buildEntry(guildId, idx + 1, score)
        }

        return GuildLeaderboardResponse(
            type = category.id,
            period = period.name,
            generatedAt = Instant.now().toString(),
            count = entries.size,
            entries = entries
        )
    }

    private fun rankGuilds(category: Category, limit: Int): List<Pair<UUID, Double>> {
        return when (category) {
            Category.LEVEL -> {
                guildService.getAllGuilds()
                    .map { g ->
                        val xp = progressionRepository.getGuildProgression(g.id)?.totalExperience ?: 0
                        // composite: level dominates, xp tie-breaks
                        g.id to (g.level * 1_000_000.0 + xp)
                    }
                    .sortedByDescending { it.second }
                    .take(limit)
            }
            Category.BALANCE -> {
                guildService.getAllGuilds()
                    .map { g -> g.id to safeBalance(g.id) }
                    .sortedByDescending { it.second }
                    .take(limit)
            }
            Category.ACTIVITY -> {
                val weekStart = currentWeekStart()
                leaderboardRepository.getWeeklyActivityForPeriod(weekStart, 1000)
                    .map { it.guildId to it.totalScore.toDouble() }
                    .sortedByDescending { it.second }
                    .take(limit)
            }
            Category.MEMBERS -> {
                guildService.getAllGuilds()
                    .map { g -> g.id to memberService.getGuildMembers(g.id).size.toDouble() }
                    .sortedByDescending { it.second }
                    .take(limit)
            }
            Category.AGE -> {
                guildService.getAllGuilds()
                    .sortedBy { it.createdAt }
                    .take(limit)
                    .map { it.id to it.createdAt.epochSecond.toDouble() }
            }
        }
    }

    private fun buildEntry(guildId: UUID, rank: Int, score: Double): GuildLeaderboardEntryDto? {
        val guild = guildService.getGuild(guildId) ?: return null
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

        val balance = safeBalance(guild.id).toInt()
        val activityScore = currentWeekActivityScore(guild.id)

        return GuildLeaderboardEntryDto(
            rank = rank,
            id = guild.id.toString(),
            name = guild.name,
            tag = guild.tag,
            tagPlain = guild.tag?.let { stripFormatting(it) },
            level = guild.level,
            totalExperience = progression?.totalExperience ?: 0,
            experienceThisLevel = progression?.experienceThisLevel ?: 0,
            experienceForNextLevel = progression?.experienceForNextLevel ?: 0,
            memberCount = members.size,
            bankBalance = balance,
            activityScore = activityScore,
            topMemberUuids = topMembers,
            banner = banner,
            createdAt = guild.createdAt.toString(),
            score = score
        )
    }

    private fun safeBalance(guildId: UUID): Double =
        try { bankService.getBalance(guildId).toDouble() } catch (_: Exception) { 0.0 }

    private fun currentWeekActivityScore(guildId: UUID): Int =
        try {
            leaderboardService.getWeeklyActivity(guildId, currentWeekStart())?.totalScore ?: 0
        } catch (_: Exception) { 0 }

    private fun currentWeekStart(): Instant =
        ZonedDateTime.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

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
