package net.lumalyte.lg.infrastructure.web.dto

data class GuildLeaderboardResponse(
    val type: String,
    val period: String,
    val generatedAt: String,
    val count: Int,
    val entries: List<GuildLeaderboardEntryDto>
)

data class GuildLeaderboardEntryDto(
    val rank: Int,
    val id: String,
    val name: String,
    val tag: String?,
    val tagPlain: String?,
    val level: Int,
    val totalExperience: Int,
    val experienceThisLevel: Int,
    val experienceForNextLevel: Int,
    val memberCount: Int,
    val bankBalance: Int,
    val activityScore: Int,
    val topMemberUuids: List<String>,
    val banner: BannerDto?,
    val createdAt: String,
    val score: Double
)

data class BannerDto(
    val baseColor: String,
    val baseColorHex: String,
    val patterns: List<BannerPatternDto>
)

data class BannerPatternDto(
    val type: String,
    val color: String,
    val colorHex: String
)
