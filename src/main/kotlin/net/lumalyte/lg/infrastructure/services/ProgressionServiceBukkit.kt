package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.*
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.pow

class ProgressionServiceBukkit(
    private val progressionRepository: ProgressionRepository,
    private val guildService: GuildService,
    private val configService: ConfigService,
    private val warService: WarService
) : ProgressionService {

    private val logger = LoggerFactory.getLogger(ProgressionServiceBukkit::class.java)

    override fun awardExperience(guildId: UUID, experience: Int, source: ExperienceSource): Int? {
        try {
            // Check for war farming cooldown
            if (warService.isGuildInWarFarmingCooldown(guildId)) {
                logger.info("Blocked EXP award for guild $guildId due to war farming cooldown")
                return null // Don't award EXP if guild is in cooldown
            }

            // Check if claims are enabled for claim-related sources
            val mainConfig = configService.loadConfig()
            if ((source == ExperienceSource.CLAIM_CREATED || source == ExperienceSource.CLAIM_DESTROYED)
                && !mainConfig.claimsEnabled) {
                logger.info("Blocked EXP award for guild $guildId due to claims being disabled (source: $source)")
                return null // Don't award EXP for claim sources if claims are disabled
            }

            // Get or create guild progression
            val progression = progressionRepository.getGuildProgression(guildId) 
                ?: GuildProgression.create(guildId)

            val oldLevel = progression.currentLevel
            val newTotalExperience = progression.totalExperience + experience

            // Calculate new level and experience distribution
            val newLevel = getLevelFromExperience(newTotalExperience)
            val newExperienceThisLevel = getExperienceInCurrentLevel(newTotalExperience)
            val newExperienceForNextLevel = getExperienceForNextLevel(newLevel)

            // Create updated progression
            val updatedProgression = progression.copy(
                totalExperience = newTotalExperience,
                currentLevel = newLevel,
                experienceThisLevel = newExperienceThisLevel,
                experienceForNextLevel = newExperienceForNextLevel,
                lastLevelUp = if (newLevel > oldLevel) Instant.now() else progression.lastLevelUp,
                totalLevelUps = if (newLevel > oldLevel) progression.totalLevelUps + (newLevel - oldLevel) else progression.totalLevelUps,
                lastUpdated = Instant.now()
            )

            // Save progression
            val saved = progressionRepository.saveGuildProgression(updatedProgression)
            if (!saved) {
                logger.error("Failed to save guild progression for guild $guildId")
                return null
            }

            // Record experience transaction
            val transaction = ExperienceTransaction(
                guildId = guildId,
                source = source,
                amount = experience,
                description = "XP from $source"
            )
            progressionRepository.recordExperienceTransaction(transaction)

            // Process level up if occurred
            if (newLevel > oldLevel) {
                logger.info("Guild $guildId leveled up from $oldLevel to $newLevel (gained $experience XP from $source)")
                processLevelUp(guildId, newLevel)
                return newLevel
            }

            // Debug logging removed - issue is solved
            return null

        } catch (e: Exception) {
            logger.error("Error awarding experience to guild $guildId", e)
            return null
        }
    }

    override fun getExperienceForNextLevel(currentLevel: Int): Int {
        val config = configService.loadConfig().progression
        
        // Use the configurable leveling curve
        val baseXp = config.baseXp
        val exponent = config.levelExponent
        val linearBonus = config.linearBonusPerLevel
        
        // Calculate XP needed for next level: base * (level^exponent) + (level * linearBonus)
        val nextLevel = currentLevel + 1
        return (baseXp * nextLevel.toDouble().pow(exponent) + (nextLevel * linearBonus)).toInt()
    }

    override fun getTotalExperienceForLevel(targetLevel: Int): Int {
        if (targetLevel <= 1) return 0
        
        var totalXp = 0
        for (level in 1 until targetLevel) {
            totalXp += getExperienceForNextLevel(level)
        }
        return totalXp
    }

    override fun getLevelFromExperience(totalExperience: Int): Int {
        if (totalExperience <= 0) return 1
        
        var currentLevel = 1
        var experienceUsed = 0
        
        while (true) {
            val xpNeeded = getExperienceForNextLevel(currentLevel)
            if (experienceUsed + xpNeeded > totalExperience) {
                break
            }
            experienceUsed += xpNeeded
            currentLevel++
            
            // Safety check to prevent infinite loops
            if (currentLevel > 100) {
                logger.warn("Level calculation exceeded maximum level (100) for experience $totalExperience")
                break
            }
        }
        
        return currentLevel
    }

    override fun getLevelProgress(totalExperience: Int): Pair<Int, Int> {
        val currentLevel = getLevelFromExperience(totalExperience)
        val experienceInCurrentLevel = getExperienceInCurrentLevel(totalExperience)
        val experienceForNextLevel = getExperienceForNextLevel(currentLevel)
        
        return Pair(experienceInCurrentLevel, experienceForNextLevel)
    }

    /**
     * Gets the experience within the current level (0 to experienceForNextLevel-1).
     */
    private fun getExperienceInCurrentLevel(totalExperience: Int): Int {
        val currentLevel = getLevelFromExperience(totalExperience)
        val totalXpForCurrentLevel = getTotalExperienceForLevel(currentLevel)
        return totalExperience - totalXpForCurrentLevel
    }

    override fun getPerksForLevel(level: Int): List<PerkType> {
        val configs = LevelPerkConfig.getDefaultConfigs(configService.loadConfig().claimsEnabled)
        return configs[level]?.unlockedPerks?.toList() ?: emptyList()
    }

    override fun hasPerkUnlocked(guildId: UUID, perkType: PerkType): Boolean {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return false
        return getUnlockedPerks(guildId).contains(perkType)
    }

    override fun getUnlockedPerks(guildId: UUID): List<PerkType> {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return emptyList()
        
        val allPerks = mutableListOf<PerkType>()
        for (level in 1..progression.currentLevel) {
            allPerks.addAll(getPerksForLevel(level))
        }
        
        return allPerks.distinct()
    }

    override fun getMaxClaimBlocks(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 0
        val configs = LevelPerkConfig.getDefaultConfigs(configService.loadConfig().claimsEnabled)
        
        var totalBonus = 0
        for (level in 1..progression.currentLevel) {
            totalBonus += configs[level]?.claimBlockBonus ?: 0
        }
        return totalBonus
    }

    override fun getMaxClaimCount(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 0
        val configs = LevelPerkConfig.getDefaultConfigs(configService.loadConfig().claimsEnabled)
        
        var totalBonus = 0
        for (level in 1..progression.currentLevel) {
            totalBonus += configs[level]?.claimCountBonus ?: 0
        }
        return totalBonus
    }

    override fun getBankInterestRate(guildId: UUID): Double {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 0.0
        val configs = LevelPerkConfig.getDefaultConfigs(configService.loadConfig().claimsEnabled)
        
        var maxRate = 0.0
        for (level in 1..progression.currentLevel) {
            val rate = configs[level]?.bankInterestRate ?: 0.0
            if (rate > maxRate) maxRate = rate
        }
        return maxRate
    }

    override fun calculateWeeklyActivityScore(guildId: UUID, weekStart: Instant, weekEnd: Instant): Int {
        // Get experience transactions for the week
        val transactions = progressionRepository.getExperienceTransactions(guildId, 1000)
            .filter { it.timestamp in weekStart..weekEnd }
        
        // Calculate weighted score based on different activities
        var score = 0
        val mainConfig = configService.loadConfig()
        for (transaction in transactions) {
            score += when (transaction.source) {
                ExperienceSource.MEMBER_JOINED -> transaction.amount * 2 // High value activity
                ExperienceSource.WAR_WON -> transaction.amount * 3 // Very high value
                ExperienceSource.BANK_DEPOSIT -> transaction.amount * 1 // Standard value
                ExperienceSource.CLAIM_CREATED -> {
                    if (mainConfig.claimsEnabled) transaction.amount * 2 else 0 // High value, but only if claims enabled
                }
                else -> transaction.amount // Standard value
            }
        }
        
        return score
    }

    override fun getTopActiveGuilds(period: ActivityPeriod, limit: Int): List<Pair<UUID, Int>> {
        val now = Instant.now()
        val periodStart = when (period) {
            ActivityPeriod.DAILY -> now.minus(1, ChronoUnit.DAYS)
            ActivityPeriod.WEEKLY -> now.minus(7, ChronoUnit.DAYS)
            ActivityPeriod.MONTHLY -> now.minus(30, ChronoUnit.DAYS)
            ActivityPeriod.ALL_TIME -> Instant.EPOCH
        }
        
        // Get all guild progressions and calculate activity scores
        val allMetrics = progressionRepository.getAllActivityMetrics(1000)
        val guildScores = mutableListOf<Pair<UUID, Int>>()
        
        for (metrics in allMetrics) {
            val score = calculateWeeklyActivityScore(metrics.guildId, periodStart, now)
            guildScores.add(Pair(metrics.guildId, score))
        }
        
        return guildScores
            .sortedByDescending { it.second }
            .take(limit)
    }

    override fun getActivityPercentile(guildId: UUID, period: ActivityPeriod): Double {
        val allScores = getTopActiveGuilds(period, 1000)
        val guildScore = allScores.find { it.first == guildId }?.second ?: 0
        
        if (allScores.isEmpty()) return 0.0
        
        val betterThanCount = allScores.count { it.second < guildScore }
        return (betterThanCount.toDouble() / allScores.size.toDouble()) * 100.0
    }

    override fun resetWeeklyActivity(): Int {
        return progressionRepository.resetAllActivityMetrics()
    }

    override fun getMaxHomes(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 1
        val configs = LevelPerkConfig.getDefaultConfigs(configService.loadConfig().claimsEnabled)
        
        var totalBonus = 0
        for (level in 1..progression.currentLevel) {
            totalBonus += configs[level]?.homeLimitBonus ?: 0
        }
        return 1 + totalBonus // Base 1 home + bonus
    }

    override fun processLevelUp(guildId: UUID, newLevel: Int): List<PerkType> {
        val newPerks = getPerksForLevel(newLevel)
        
        // Send notifications to all online guild members
        notifyGuildMembers(guildId, newLevel, newPerks)
        
        // Apply any immediate effects of new perks
        applyPerkEffects(guildId, newPerks)
        
        return newPerks
    }

    /**
     * Notifies all online guild members about the level up.
     */
    private fun notifyGuildMembers(guildId: UUID, newLevel: Int, newPerks: List<PerkType>) {
        try {
            val guild = guildService.getGuild(guildId) ?: return
            
            // Get all online members
            val onlineMembers = Bukkit.getOnlinePlayers().filter { player ->
                guildService.getPlayerGuilds(player.uniqueId).any { it.id == guildId }
            }
            
            // Send notifications
            for (player in onlineMembers) {
                // Send title and subtitle using Adventure API
                val title = Title.title(
                    Component.text("⭐ GUILD LEVEL UP! ⭐", NamedTextColor.GOLD),
                    Component.text()
                        .append(Component.text(guild.name, NamedTextColor.YELLOW))
                        .append(Component.text(" reached level ", NamedTextColor.GRAY))
                        .append(Component.text(newLevel.toString(), NamedTextColor.YELLOW))
                        .build(),
                    Title.Times.times(
                        Duration.ofMillis(500),  // fadeIn (10 ticks = 500ms)
                        Duration.ofSeconds(3),   // stay (60 ticks = 3s)
                        Duration.ofMillis(500)   // fadeOut (10 ticks = 500ms)
                    )
                )
                player.showTitle(title)
                
                // Send chat message
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                player.sendMessage("§6⭐ §lGUILD LEVEL UP! §6⭐")
                player.sendMessage("")
                player.sendMessage("§7Guild: §e${guild.name}")
                player.sendMessage("§7New Level: §e$newLevel")
                
                if (newPerks.isNotEmpty()) {
                    player.sendMessage("§7New Perks:")
                    for (perk in newPerks) {
                        player.sendMessage("§7  • §a${perk.getDisplayName()}")
                    }
                }
                
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                
                // Play sound effects based on perks
                if (hasAnnouncementSoundEffects(guildId)) {
                    player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
                } else {
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error notifying guild members about level up", e)
        }
    }

    /**
     * Applies immediate effects of newly unlocked perks.
     */
    private fun applyPerkEffects(guildId: UUID, newPerks: List<PerkType>) {
        // Most perks are passive and don't need immediate application
        // But we could add specific logic here for certain perks if needed
        
        for (perk in newPerks) {
            when (perk) {
                PerkType.BANK_INTEREST -> {
                    logger.info("Guild $guildId unlocked bank interest perk")
                    // Bank interest is handled by the bank service automatically
                }
                PerkType.ADDITIONAL_HOMES -> {
                    logger.info("Guild $guildId unlocked additional homes perk")
                    // Home limits are checked dynamically when setting homes
                }
                else -> {
                    // Most perks are passive
                }
            }
        }
    }

    /**
     * Checks if the guild has announcement sound effects unlocked.
     */
    private fun hasAnnouncementSoundEffects(guildId: UUID): Boolean {
        return hasPerkUnlocked(guildId, PerkType.ANNOUNCEMENT_SOUND_EFFECTS)
    }

    /**
     * Extension function to get display name for perk types.
     */
    private fun PerkType.getDisplayName(): String {
        return when (this) {
            PerkType.HIGHER_BANK_BALANCE -> "Higher Bank Balance Limit"
            PerkType.BANK_INTEREST -> "Bank Interest Earnings"
            PerkType.INCREASED_BANK_LIMIT -> "Increased Bank Limit"
            PerkType.REDUCED_WITHDRAWAL_FEES -> "Reduced Withdrawal Fees"
            PerkType.ADDITIONAL_HOMES -> "Additional Home Locations"
            PerkType.TELEPORT_COOLDOWN_REDUCTION -> "Faster Teleport Cooldowns"
            PerkType.HOME_TELEPORT_SOUND_EFFECTS -> "Home Teleport Sound Effects"
            PerkType.SPECIAL_PARTICLES -> "Special Particle Effects"
            PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> "Announcement Sound Effects"
            PerkType.WAR_DECLARATION_SOUND_EFFECTS -> "War Declaration Sound Effects"
            PerkType.INCREASED_CLAIM_BLOCKS -> "More Claim Blocks"
            PerkType.INCREASED_CLAIM_COUNT -> "More Claims"
            PerkType.FASTER_CLAIM_REGEN -> "Faster Claim Regeneration"
            PerkType.CUSTOM_BANNER_COLORS -> "Custom Banner Colors"
            PerkType.ANIMATED_EMOJIS -> "Animated Emojis"
        }
    }
}
