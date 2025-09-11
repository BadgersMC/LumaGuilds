package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.application.services.PerkType
import java.time.Instant
import java.util.UUID

/**
 * Represents a guild's experience and leveling data.
 */
data class GuildProgression(
    val guildId: UUID,
    val totalExperience: Int = 0,
    val currentLevel: Int = 1,
    val experienceThisLevel: Int = 0,
    val experienceForNextLevel: Int = 1000,
    val lastLevelUp: Instant? = null,
    val totalLevelUps: Int = 0,
    val unlockedPerks: Set<PerkType> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Checks if the guild can level up.
     */
    val canLevelUp: Boolean
        get() = experienceThisLevel >= experienceForNextLevel

    /**
     * Gets the progress percentage to the next level (0.0 to 1.0).
     */
    val levelProgress: Double
        get() = if (experienceForNextLevel > 0) {
            experienceThisLevel.toDouble() / experienceForNextLevel.toDouble()
        } else {
            1.0
        }

    /**
     * Gets the experience needed to reach the next level.
     */
    val experienceToNextLevel: Int
        get() = maxOf(0, experienceForNextLevel - experienceThisLevel)

    companion object {
        /**
         * Creates a new guild progression with default values.
         */
        fun create(guildId: UUID): GuildProgression {
            return GuildProgression(
                guildId = guildId,
                experienceForNextLevel = calculateExperienceForLevel(2),
                unlockedPerks = setOf(PerkType.CUSTOM_BANNER_COLORS, PerkType.ANIMATED_EMOJIS) // Always unlocked
            )
        }

        /**
         * Calculates the experience required for a specific level.
         * Uses a balanced growth formula that starts reasonable and has a gradual taper-off.
         * Formula: baseXP * (level^1.3) + (level * 200) for competitive but fair progression
         */
        fun calculateExperienceForLevel(level: Int): Int {
            if (level <= 1) return 0
            val baseXP = 800.0
            val exponent = 1.3 // Gentler curve than 1.5
            val linearBonus = level * 200 // Adds linear growth to prevent harsh walls
            return (baseXP * Math.pow(level.toDouble() - 1, exponent) + linearBonus).toInt()
        }

        /**
         * Calculates the total experience required to reach a specific level.
         */
        fun calculateTotalExperienceForLevel(targetLevel: Int): Int {
            var total = 0
            for (level in 2..targetLevel) {
                total += calculateExperienceForLevel(level)
            }
            return total
        }

        /**
         * Determines the level for a given total experience amount.
         */
        fun calculateLevelFromExperience(totalExperience: Int): Int {
            var level = 1
            var remainingXP = totalExperience

            while (remainingXP >= calculateExperienceForLevel(level + 1)) {
                remainingXP -= calculateExperienceForLevel(level + 1)
                level++
            }

            return level
        }
    }
}

/**
 * Represents an experience transaction for a guild.
 */
data class ExperienceTransaction(
    val id: UUID = UUID.randomUUID(),
    val guildId: UUID,
    val amount: Int,
    val source: ExperienceSource,
    val description: String? = null,
    val actorId: UUID? = null,
    val timestamp: Instant = Instant.now()
) {
    init {
        require(amount >= 0) { "Experience amount cannot be negative" }
    }
}

/**
 * Represents a perk configuration for a specific level.
 */
data class LevelPerkConfig(
    val level: Int,
    val unlockedPerks: Set<PerkType>,
    val claimBlockBonus: Int = 0,
    val claimCountBonus: Int = 0,
    val bankInterestRate: Double = 0.0,
    val bankBalanceLimit: Int = 0, // Maximum bank balance allowed
    val homeLimitBonus: Int = 0
) {
    companion object {
        /**
         * Gets the default perk configuration for all levels, conditionally replacing claim-related perks when claims are disabled.
         */
        fun getDefaultConfigs(claimsEnabled: Boolean = true): Map<Int, LevelPerkConfig> {
            return if (claimsEnabled) {
                // Default configs with claim perks
                mapOf(
                    1 to LevelPerkConfig(1, setOf(PerkType.CUSTOM_BANNER_COLORS, PerkType.ANIMATED_EMOJIS), bankBalanceLimit = 50000), // Always unlocked
                    2 to LevelPerkConfig(2, setOf(PerkType.INCREASED_CLAIM_BLOCKS), claimBlockBonus = 100, bankBalanceLimit = 75000),
                    3 to LevelPerkConfig(3, setOf(PerkType.FASTER_CLAIM_REGEN), claimBlockBonus = 200, bankBalanceLimit = 100000),
                    5 to LevelPerkConfig(5, setOf(PerkType.BANK_INTEREST, PerkType.ANNOUNCEMENT_SOUND_EFFECTS), claimBlockBonus = 500, bankInterestRate = 0.01, bankBalanceLimit = 150000),
                    7 to LevelPerkConfig(7, setOf(PerkType.REDUCED_WITHDRAWAL_FEES), claimBlockBonus = 1000, claimCountBonus = 1, bankBalanceLimit = 200000),
                    10 to LevelPerkConfig(10, setOf(PerkType.SPECIAL_PARTICLES, PerkType.HOME_TELEPORT_SOUND_EFFECTS), claimBlockBonus = 2000, claimCountBonus = 2, bankInterestRate = 0.015, bankBalanceLimit = 300000),
                    15 to LevelPerkConfig(15, setOf(PerkType.ADDITIONAL_HOMES), claimBlockBonus = 5000, claimCountBonus = 3, homeLimitBonus = 1, bankBalanceLimit = 500000),
                    20 to LevelPerkConfig(20, setOf(PerkType.TELEPORT_COOLDOWN_REDUCTION, PerkType.WAR_DECLARATION_SOUND_EFFECTS), claimBlockBonus = 10000, claimCountBonus = 5, bankBalanceLimit = 750000),
                    25 to LevelPerkConfig(25, setOf(PerkType.HIGHER_BANK_BALANCE), claimBlockBonus = 20000, claimCountBonus = 8, bankInterestRate = 0.02, bankBalanceLimit = 1000000),
                    30 to LevelPerkConfig(30, setOf(PerkType.INCREASED_BANK_LIMIT), claimBlockBonus = 35000, claimCountBonus = 12, bankInterestRate = 0.025, bankBalanceLimit = 1500000, homeLimitBonus = 2)
                )
            } else {
                // Alternative configs when claims are disabled - focus on bank, homes, and other features
                mapOf(
                    1 to LevelPerkConfig(1, setOf(PerkType.CUSTOM_BANNER_COLORS, PerkType.ANIMATED_EMOJIS), bankBalanceLimit = 50000), // Always unlocked
                    2 to LevelPerkConfig(2, setOf(PerkType.HIGHER_BANK_BALANCE), bankBalanceLimit = 100000, bankInterestRate = 0.005), // Increased bank instead of claim blocks
                    3 to LevelPerkConfig(3, setOf(PerkType.BANK_INTEREST), bankBalanceLimit = 150000, bankInterestRate = 0.01), // Earlier bank interest
                    5 to LevelPerkConfig(5, setOf(PerkType.ANNOUNCEMENT_SOUND_EFFECTS, PerkType.SPECIAL_PARTICLES), bankBalanceLimit = 250000, bankInterestRate = 0.015), // Extra visual perks
                    7 to LevelPerkConfig(7, setOf(PerkType.REDUCED_WITHDRAWAL_FEES, PerkType.ADDITIONAL_HOMES), bankBalanceLimit = 400000, homeLimitBonus = 1), // Home bonus instead of claims
                    10 to LevelPerkConfig(10, setOf(PerkType.HOME_TELEPORT_SOUND_EFFECTS, PerkType.TELEPORT_COOLDOWN_REDUCTION), bankBalanceLimit = 600000, homeLimitBonus = 1), // Enhanced teleport perks
                    15 to LevelPerkConfig(15, setOf(PerkType.INCREASED_BANK_LIMIT), bankBalanceLimit = 1000000, bankInterestRate = 0.02, homeLimitBonus = 2), // Bank focus
                    20 to LevelPerkConfig(20, setOf(PerkType.WAR_DECLARATION_SOUND_EFFECTS), bankBalanceLimit = 1500000, bankInterestRate = 0.025, homeLimitBonus = 3), // War and home focus
                    25 to LevelPerkConfig(25, setOf(PerkType.HIGHER_BANK_BALANCE), bankBalanceLimit = 2500000, bankInterestRate = 0.03, homeLimitBonus = 4), // Maximum bank
                    30 to LevelPerkConfig(30, setOf(PerkType.INCREASED_BANK_LIMIT), bankBalanceLimit = 5000000, bankInterestRate = 0.035, homeLimitBonus = 5) // Ultimate bank limit
                )
            }
        }

        /**
         * Legacy method for backward compatibility - assumes claims are enabled
         */
        fun getDefaultConfigs(): Map<Int, LevelPerkConfig> {
            return getDefaultConfigs(true)
        }
    }
}

/**
 * Represents a guild's activity metrics for progression calculation.
 */
data class GuildActivityMetrics(
    val guildId: UUID,
    val memberCount: Int = 0,
    val activeMembers: Int = 0,
    val claimsOwned: Int = 0,
    val claimsCreatedThisWeek: Int = 0,
    val killsThisWeek: Int = 0,
    val deathsThisWeek: Int = 0,
    val bankDepositsThisWeek: Int = 0,
    val relationsFormed: Int = 0,
    val warsParticipated: Int = 0,
    val lastCalculated: Instant = Instant.now()
) {
    /**
     * Calculates an activity score based on various metrics.
     */
    val activityScore: Int
        get() {
            var score = 0

            // Base member activity
            score += memberCount * 10
            score += activeMembers * 50

            // Land ownership
            score += claimsOwned * 20
            score += claimsCreatedThisWeek * 100

            // Combat activity
            score += killsThisWeek * 25
            score += deathsThisWeek * 10

            // Economic activity
            score += (bankDepositsThisWeek / 100) * 5

            // Social activity
            score += relationsFormed * 75
            score += warsParticipated * 200

            return score
        }

    /**
     * Gets the activity per member ratio.
     */
    val activityPerMember: Double
        get() = if (memberCount > 0) {
            activityScore.toDouble() / memberCount.toDouble()
        } else {
            0.0
        }
}
