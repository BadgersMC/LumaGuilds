package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PerkType
import net.lumalyte.lg.config.*
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Service for loading and managing progression.yml configuration
 */
class ProgressionConfigService(private val plugin: Plugin) {

    private var progressionConfig: ProgressionSystemConfig? = null
    private lateinit var configFile: File
    private lateinit var yamlConfig: FileConfiguration

    /**
     * Loads progression.yml from the plugin data folder
     */
    fun loadProgressionConfig(): ProgressionSystemConfig {
        // Ensure progression.yml exists
        configFile = File(plugin.dataFolder, "progression.yml")
        if (!configFile.exists()) {
            plugin.saveResource("progression.yml", false)
        }

        yamlConfig = YamlConfiguration.loadConfiguration(configFile)

        // Load the configuration
        progressionConfig = ProgressionSystemConfig(
            enabled = yamlConfig.getBoolean("enabled", true),
            claimsBased = yamlConfig.getBoolean("claims_based", true),
            maxLevel = yamlConfig.getInt("max_level", 30),
            leveling = loadLevelingFormula(),
            rateLimiting = loadRateLimiting(),
            experienceSources = loadExperienceSources(),
            levelsClaims = loadLevelRewards("levels_claims"),
            levelsNoClaims = loadLevelRewards("levels_no_claims"),
            milestones = loadMilestones(),
            activity = loadActivityTracking(),
            leaderboards = loadLeaderboards()
        )

        return progressionConfig!!
    }

    /**
     * Reloads the progression configuration
     */
    fun reloadProgressionConfig(): ProgressionSystemConfig {
        return loadProgressionConfig()
    }

    /**
     * Gets the currently loaded progression config
     */
    fun getProgressionConfig(): ProgressionSystemConfig {
        return progressionConfig ?: loadProgressionConfig()
    }

    private fun loadLevelingFormula(): LevelingFormulaConfig {
        return LevelingFormulaConfig(
            baseXp = yamlConfig.getDouble("leveling.base_xp", 800.0),
            levelExponent = yamlConfig.getDouble("leveling.level_exponent", 1.3),
            linearBonus = yamlConfig.getInt("leveling.linear_bonus", 200)
        )
    }

    private fun loadRateLimiting(): RateLimitingConfig {
        return RateLimitingConfig(
            xpCooldownMs = yamlConfig.getLong("rate_limiting.xp_cooldown_ms", 5000L),
            maxXpPerBatch = yamlConfig.getInt("rate_limiting.max_xp_per_batch", 50)
        )
    }

    private fun loadExperienceSources(): ExperienceSourcesConfig {
        return ExperienceSourcesConfig(
            guild = loadGuildExperienceSources(),
            player = loadPlayerExperienceSources()
        )
    }

    private fun loadGuildExperienceSources(): GuildExperienceSourcesConfig {
        val base = "experience_sources.guild"
        return GuildExperienceSourcesConfig(
            bankDeposit = ExperienceSourceConfig(
                enabled = yamlConfig.getBoolean("$base.bank_deposit.enabled", true),
                xp = 0,
                xpPer100Coins = yamlConfig.getInt("$base.bank_deposit.xp_per_100_coins", 1)
            ),
            memberJoined = loadExperienceSource("$base.member_joined", 50),
            warWon = loadExperienceSource("$base.war_won", 500),
            warLost = loadExperienceSource("$base.war_lost", 100),
            claimCreated = loadExperienceSource("$base.claim_created", 100),
            allianceFormed = loadExperienceSource("$base.alliance_formed", 150),
            partyCreated = loadExperienceSource("$base.party_created", 25)
        )
    }

    private fun loadPlayerExperienceSources(): PlayerExperienceSourcesConfig {
        val base = "experience_sources.player"
        return PlayerExperienceSourcesConfig(
            playerKill = loadExperienceSource("$base.player_kill", 25),
            mobKill = loadExperienceSource("$base.mob_kill", 2),
            cropHarvest = loadExperienceSource("$base.crop_harvest", 1),
            blockBreak = loadExperienceSource("$base.block_break", 1),
            blockPlace = loadExperienceSource("$base.block_place", 1),
            crafting = loadExperienceSource("$base.crafting", 2),
            smelting = loadExperienceSource("$base.smelting", 2),
            brewing = loadExperienceSource("$base.brewing", 3),
            fishing = loadExperienceSource("$base.fishing", 3),
            enchanting = loadExperienceSource("$base.enchanting", 10)
        )
    }

    private fun loadExperienceSource(path: String, defaultXp: Int): ExperienceSourceConfig {
        return ExperienceSourceConfig(
            enabled = yamlConfig.getBoolean("$path.enabled", true),
            xp = yamlConfig.getInt("$path.xp", defaultXp),
            xpPer100Coins = 0
        )
    }

    private fun loadLevelRewards(sectionName: String): Map<Int, LevelRewardConfig> {
        val rewards = mutableMapOf<Int, LevelRewardConfig>()
        val section = yamlConfig.getConfigurationSection(sectionName) ?: return emptyMap()

        for (levelKey in section.getKeys(false)) {
            val level = levelKey.toIntOrNull() ?: continue
            val levelPath = "$sectionName.$levelKey"

            rewards[level] = LevelRewardConfig(
                perks = loadPerks("$levelPath.perks"),
                claimBlocks = yamlConfig.getInt("$levelPath.claim_blocks", 0),
                claimCount = yamlConfig.getInt("$levelPath.claim_count", 0),
                bankLimit = yamlConfig.getInt("$levelPath.bank_limit", 0),
                interestRate = yamlConfig.getDouble("$levelPath.interest_rate", 0.0),
                homes = yamlConfig.getInt("$levelPath.homes", 1),
                members = yamlConfig.getInt("$levelPath.members", 10),
                warSlots = yamlConfig.getInt("$levelPath.war_slots", 3),
                withdrawalFeeMultiplier = yamlConfig.getDouble("$levelPath.withdrawal_fee_multiplier", 1.0),
                homeCooldownMultiplier = yamlConfig.getDouble("$levelPath.home_cooldown_multiplier", 1.0),
                warCostMultiplier = yamlConfig.getDouble("$levelPath.war_cost_multiplier", 1.0)
            )
        }

        return rewards
    }

    private fun loadPerks(path: String): List<PerkType> {
        val perkStrings = yamlConfig.getStringList(path)
        return perkStrings.mapNotNull { perkString ->
            try {
                PerkType.valueOf(perkString)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Unknown perk type: $perkString")
                null
            }
        }
    }

    private fun loadMilestones(): Map<Int, MilestoneRewardConfig> {
        val milestones = mutableMapOf<Int, MilestoneRewardConfig>()
        val section = yamlConfig.getConfigurationSection("milestones") ?: return emptyMap()

        for (levelKey in section.getKeys(false)) {
            val level = levelKey.toIntOrNull() ?: continue
            val milestonePath = "milestones.$levelKey"

            milestones[level] = MilestoneRewardConfig(
                broadcast = yamlConfig.getBoolean("$milestonePath.broadcast", false),
                message = yamlConfig.getString("$milestonePath.message", ""),
                coins = yamlConfig.getInt("$milestonePath.coins", 0),
                items = loadMilestoneItems("$milestonePath.items")
            )
        }

        return milestones
    }

    private fun loadMilestoneItems(path: String): List<MilestoneItemRewardConfig> {
        val items = mutableListOf<MilestoneItemRewardConfig>()
        val itemsSection = yamlConfig.getConfigurationSection(path) ?: return emptyList()

        for (itemKey in itemsSection.getKeys(false)) {
            val itemPath = "$path.$itemKey"
            val materialString = yamlConfig.getString("$itemPath.material") ?: continue

            try {
                val material = Material.valueOf(materialString)
                items.add(
                    MilestoneItemRewardConfig(
                        material = material,
                        amount = yamlConfig.getInt("$itemPath.amount", 1),
                        displayName = yamlConfig.getString("$itemPath.display_name"),
                        lore = yamlConfig.getStringList("$itemPath.lore")
                    )
                )
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Unknown material type: $materialString")
            }
        }

        return items
    }

    private fun loadActivityTracking(): ActivityTrackingConfig {
        return ActivityTrackingConfig(
            enabled = yamlConfig.getBoolean("activity.enabled", true),
            weeklyReset = yamlConfig.getBoolean("activity.weekly_reset", true),
            resetDay = loadDayOfWeek(yamlConfig.getString("activity.reset_day", "MONDAY")),
            weights = loadActivityWeights()
        )
    }

    private fun loadActivityWeights(): ActivityWeightsConfig {
        val base = "activity.weights"
        return ActivityWeightsConfig(
            memberCount = yamlConfig.getInt("$base.member_count", 10),
            activeMembers = yamlConfig.getInt("$base.active_members", 50),
            claimsOwned = yamlConfig.getInt("$base.claims_owned", 20),
            killsThisWeek = yamlConfig.getInt("$base.kills_this_week", 25),
            bankDepositsThisWeek = yamlConfig.getInt("$base.bank_deposits_this_week", 5),
            relationsFormed = yamlConfig.getInt("$base.relations_formed", 75),
            warsParticipated = yamlConfig.getInt("$base.wars_participated", 200)
        )
    }

    private fun loadDayOfWeek(dayString: String): DayOfWeek {
        return try {
            DayOfWeek.valueOf(dayString.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Unknown day of week: $dayString, defaulting to MONDAY")
            DayOfWeek.MONDAY
        }
    }

    private fun loadLeaderboards(): LeaderboardConfig {
        val typeStrings = yamlConfig.getStringList("leaderboards.types")
        val types = typeStrings.mapNotNull { typeString ->
            try {
                LeaderboardType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Unknown leaderboard type: $typeString")
                null
            }
        }

        return LeaderboardConfig(
            enabled = yamlConfig.getBoolean("leaderboards.enabled", true),
            updateIntervalMinutes = yamlConfig.getInt("leaderboards.update_interval_minutes", 5),
            displaySize = yamlConfig.getInt("leaderboards.display_size", 10),
            types = types.ifEmpty {
                listOf(
                    LeaderboardType.LEVEL,
                    LeaderboardType.TOTAL_XP,
                    LeaderboardType.WEEKLY_ACTIVITY,
                    LeaderboardType.BANK_BALANCE,
                    LeaderboardType.MEMBER_COUNT
                )
            }
        )
    }

    /**
     * Migrates old progression config from config.yml to progression.yml
     */
    fun migrateFromMainConfig(mainConfig: FileConfiguration): Boolean {
        if (configFile.exists()) {
            plugin.logger.info("progression.yml already exists, skipping migration")
            return false
        }

        plugin.logger.info("Migrating progression settings from config.yml to progression.yml...")

        // Create progression.yml with default values
        plugin.saveResource("progression.yml", false)
        yamlConfig = YamlConfiguration.loadConfiguration(configFile)

        // Migrate XP values from old config
        yamlConfig.set("experience_sources.guild.bank_deposit.xp_per_100_coins",
            mainConfig.getInt("progression.bank_deposit_xp_per_100", 1))
        yamlConfig.set("experience_sources.guild.member_joined.xp",
            mainConfig.getInt("progression.member_joined_xp", 50))
        yamlConfig.set("experience_sources.guild.war_won.xp",
            mainConfig.getInt("progression.war_won_xp", 500))
        yamlConfig.set("experience_sources.player.player_kill.xp",
            mainConfig.getInt("progression.player_kill_xp", 25))
        yamlConfig.set("experience_sources.player.mob_kill.xp",
            mainConfig.getInt("progression.mob_kill_xp", 2))
        yamlConfig.set("experience_sources.player.crop_harvest.xp",
            mainConfig.getInt("progression.crop_break_xp", 1))
        yamlConfig.set("experience_sources.player.block_break.xp",
            mainConfig.getInt("progression.block_break_xp", 1))
        yamlConfig.set("experience_sources.player.block_place.xp",
            mainConfig.getInt("progression.block_place_xp", 1))
        yamlConfig.set("experience_sources.player.crafting.xp",
            mainConfig.getInt("progression.crafting_xp", 2))
        yamlConfig.set("experience_sources.player.smelting.xp",
            mainConfig.getInt("progression.smelting_xp", 2))
        yamlConfig.set("experience_sources.player.fishing.xp",
            mainConfig.getInt("progression.fishing_xp", 3))
        yamlConfig.set("experience_sources.player.enchanting.xp",
            mainConfig.getInt("progression.enchanting_xp", 10))
        yamlConfig.set("experience_sources.guild.claim_created.xp",
            mainConfig.getInt("progression.claim_created_xp", 100))

        // Migrate leveling formula
        yamlConfig.set("leveling.base_xp", mainConfig.getDouble("progression.base_xp", 800.0))
        yamlConfig.set("leveling.level_exponent", mainConfig.getDouble("progression.level_exponent", 1.3))
        yamlConfig.set("leveling.linear_bonus", mainConfig.getInt("progression.linear_bonus_per_level", 200))

        // Migrate rate limiting
        yamlConfig.set("rate_limiting.xp_cooldown_ms", mainConfig.getLong("progression.xp_cooldown_ms", 5000L))
        yamlConfig.set("rate_limiting.max_xp_per_batch", mainConfig.getInt("progression.max_xp_per_batch", 50))

        try {
            yamlConfig.save(configFile)
            plugin.logger.info("Successfully migrated progression settings to progression.yml")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save migrated progression.yml: ${e.message}")
            return false
        }
    }
}
