package net.lumalyte.lg.config

import net.lumalyte.lg.application.services.PerkType
import org.bukkit.Material

/**
 * Main progression configuration loaded from progression.yml
 */
data class ProgressionSystemConfig(
    // Global settings
    val enabled: Boolean = true,
    val claimsBased: Boolean = true,
    val maxLevel: Int = 30,

    // Leveling formula
    val leveling: LevelingFormulaConfig = LevelingFormulaConfig(),

    // Rate limiting
    val rateLimiting: RateLimitingConfig = RateLimitingConfig(),

    // Experience sources
    val experienceSources: ExperienceSourcesConfig = ExperienceSourcesConfig(),

    // Level rewards
    val levelsClaims: Map<Int, LevelRewardConfig> = emptyMap(),
    val levelsNoClaims: Map<Int, LevelRewardConfig> = emptyMap(),

    // Milestone rewards
    val milestones: Map<Int, MilestoneRewardConfig> = emptyMap(),

    // Activity tracking
    val activity: ActivityTrackingConfig = ActivityTrackingConfig(),

    // Leaderboards
    val leaderboards: LeaderboardConfig = LeaderboardConfig()
) {
    /**
     * Gets the appropriate level rewards based on claims_based setting
     */
    fun getActiveLevelRewards(): Map<Int, LevelRewardConfig> {
        return if (claimsBased) levelsClaims else levelsNoClaims
    }
}

/**
 * Leveling formula configuration
 */
data class LevelingFormulaConfig(
    val baseXp: Double = 800.0,
    val levelExponent: Double = 1.3,
    val linearBonus: Int = 200
) {
    /**
     * Calculates XP required for a specific level using the configured formula
     */
    fun calculateXpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (baseXp * Math.pow(level.toDouble() - 1, levelExponent) + (level * linearBonus)).toInt()
    }
}

/**
 * Rate limiting configuration
 */
data class RateLimitingConfig(
    val xpCooldownMs: Long = 5000L,
    val maxXpPerBatch: Int = 50
)

/**
 * Experience sources configuration
 */
data class ExperienceSourcesConfig(
    val guild: GuildExperienceSourcesConfig = GuildExperienceSourcesConfig(),
    val player: PlayerExperienceSourcesConfig = PlayerExperienceSourcesConfig()
)

/**
 * Guild-wide experience sources
 */
data class GuildExperienceSourcesConfig(
    val bankDeposit: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 0, xpPer100Coins = 1),
    val memberJoined: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 50),
    val warWon: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 500),
    val warLost: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 100),
    val claimCreated: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 100),
    val allianceFormed: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 150),
    val partyCreated: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 25)
)

/**
 * Player activity experience sources
 */
data class PlayerExperienceSourcesConfig(
    val playerKill: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 25),
    val mobKill: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 2),
    val cropHarvest: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 1),
    val blockBreak: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 1),
    val blockPlace: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 1),
    val crafting: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 2),
    val smelting: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 2),
    val brewing: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 3),
    val fishing: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 3),
    val enchanting: ExperienceSourceConfig = ExperienceSourceConfig(enabled = true, xp = 10)
)

/**
 * Individual experience source configuration
 */
data class ExperienceSourceConfig(
    val enabled: Boolean = true,
    val xp: Int = 0,
    val xpPer100Coins: Int = 0  // Special case for bank deposits
)

/**
 * Level reward configuration
 */
data class LevelRewardConfig(
    // Perks unlocked at this level
    val perks: List<PerkType> = emptyList(),

    // Claim bonuses (if claims enabled)
    val claimBlocks: Int = 0,
    val claimCount: Int = 0,

    // Bank bonuses
    val bankLimit: Int = 0,
    val interestRate: Double = 0.0,

    // Home bonuses
    val homes: Int = 1,

    // Member bonuses
    val members: Int = 10,

    // War bonuses
    val warSlots: Int = 3,

    // Multipliers
    val withdrawalFeeMultiplier: Double = 1.0,
    val homeCooldownMultiplier: Double = 1.0,
    val warCostMultiplier: Double = 1.0
) {
    /**
     * Gets cumulative claim blocks up to this level
     */
    fun getCumulativeClaimBlocks(allLevels: Map<Int, LevelRewardConfig>, currentLevel: Int): Int {
        return allLevels.entries
            .filter { it.key <= currentLevel }
            .sumOf { it.value.claimBlocks }
    }

    /**
     * Gets cumulative claim count up to this level
     */
    fun getCumulativeClaimCount(allLevels: Map<Int, LevelRewardConfig>, currentLevel: Int): Int {
        return allLevels.entries
            .filter { it.key <= currentLevel }
            .sumOf { it.value.claimCount }
    }

    /**
     * Gets the highest value for a stat up to this level
     */
    fun getHighestValue(
        allLevels: Map<Int, LevelRewardConfig>,
        currentLevel: Int,
        selector: (LevelRewardConfig) -> Number
    ): Number {
        return allLevels.entries
            .filter { it.key <= currentLevel }
            .maxOfOrNull { selector(it.value).toDouble() } ?: 0.0
    }
}

/**
 * Milestone reward configuration
 */
data class MilestoneRewardConfig(
    val broadcast: Boolean = false,
    val message: String = "",
    val coins: Int = 0,
    val items: List<MilestoneItemRewardConfig> = emptyList()
)

/**
 * Item reward for milestones
 */
data class MilestoneItemRewardConfig(
    val material: Material,
    val amount: Int = 1,
    val displayName: String? = null,
    val lore: List<String> = emptyList()
)

/**
 * Activity tracking configuration
 */
data class ActivityTrackingConfig(
    val enabled: Boolean = true,
    val weeklyReset: Boolean = true,
    val resetDay: DayOfWeek = DayOfWeek.MONDAY,
    val weights: ActivityWeightsConfig = ActivityWeightsConfig()
)

/**
 * Activity score weights
 */
data class ActivityWeightsConfig(
    val memberCount: Int = 10,
    val activeMembers: Int = 50,
    val claimsOwned: Int = 20,
    val killsThisWeek: Int = 25,
    val bankDepositsThisWeek: Int = 5,
    val relationsFormed: Int = 75,
    val warsParticipated: Int = 200
) {
    /**
     * Calculates activity score based on weights
     */
    fun calculateScore(
        members: Int,
        active: Int,
        claims: Int,
        kills: Int,
        bankDeposits: Int,
        relations: Int,
        wars: Int
    ): Int {
        return (members * memberCount) +
                (active * activeMembers) +
                (claims * claimsOwned) +
                (kills * killsThisWeek) +
                ((bankDeposits / 100) * bankDepositsThisWeek) +
                (relations * relationsFormed) +
                (wars * warsParticipated)
    }
}

/**
 * Leaderboard configuration
 */
data class LeaderboardConfig(
    val enabled: Boolean = true,
    val updateIntervalMinutes: Int = 5,
    val displaySize: Int = 10,
    val types: List<LeaderboardType> = listOf(
        LeaderboardType.LEVEL,
        LeaderboardType.TOTAL_XP,
        LeaderboardType.WEEKLY_ACTIVITY,
        LeaderboardType.BANK_BALANCE,
        LeaderboardType.MEMBER_COUNT
    )
)

/**
 * Leaderboard types
 */
enum class LeaderboardType {
    LEVEL,
    TOTAL_XP,
    WEEKLY_ACTIVITY,
    MONTHLY_ACTIVITY,
    BANK_BALANCE,
    MEMBER_COUNT,
    WAR_VICTORIES
}

/**
 * Day of week for activity resets
 */
enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
