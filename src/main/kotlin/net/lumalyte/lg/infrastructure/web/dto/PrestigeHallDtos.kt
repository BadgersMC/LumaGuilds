package net.lumalyte.lg.infrastructure.web.dto

/**
 * REQ-301: the Hall of Prestige feed for enthusia.info (schemaVersion 1). Field meanings are in the
 * website handoff's `api/prestige-snapshot.schema.json`; nullable fields are always present.
 */
data class PrestigeSnapshotDto(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val revision: String,
    val maxTier: Int,
    val tiers: List<PrestigeTierDto>,
    val guilds: List<PrestigeGuildDto>,
    val recent: List<PrestigeRecentDto>,
    val totals: PrestigeTotalsDto,
)

data class PrestigeTierDto(
    val tier: Int,
    val roman: String,
    val fee: Long,
    val perks: List<String>,
    val first: PrestigeGuildRefDto?,
    val guildCount: Int,
)

data class PrestigeGuildRefDto(val guildId: String, val name: String, val reachedAt: String, val disbanded: Boolean)

data class PrestigeGuildDto(
    val id: String,
    val name: String,
    val tag: String?,
    val tier: Int,
    val hallRank: Int,
    val reachedAt: String,
    val level: Int,
    val memberCount: Int,
    val banner: BannerDto?,
    val history: List<PrestigeHistoryDto>,
)

data class PrestigeHistoryDto(
    val tier: Int,
    val reachedAt: String,
    val firstOnServer: Boolean,
    val keptReward: PrestigeRewardDto?,
    val feePaid: Long?,
    val credited: List<String>,
    val creditedTotal: Int,
)

data class PrestigeRewardDto(val id: String, val name: String)

data class PrestigeRecentDto(val guildId: String, val name: String, val tier: Int, val reachedAt: String, val disbanded: Boolean)

data class PrestigeTotalsDto(val prestiges: Int, val guildsPrestiged: Int, val tiersClaimed: Int, val goldSpent: Long)
