package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.PerkType
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.api.events.GuildLevelUpEvent
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ProgressionServiceBukkit(
    private val progressionRepository: ProgressionRepository,
    private val guildRepository: net.lumalyte.lg.application.persistence.GuildRepository,
    private val memberRepository: net.lumalyte.lg.application.persistence.MemberRepository,
    private val configService: ConfigService,
    private val progressionConfigService: ProgressionConfigService,
    private val plugin: org.bukkit.plugin.Plugin,
    private val lang: LangService,
    private val permanentExperienceService: PermanentExperienceService,
) : ProgressionService {

    private val logger = LoggerFactory.getLogger(ProgressionServiceBukkit::class.java)

    // Memoized curve, invalidated when the config values change (reload-safe).
    private var curveCacheKey: String? = null
    private var curveCache: ProgressionCurve? = null

    /** The config-driven leveling curve — single source of truth for XP math. */
    private fun curve(): ProgressionCurve {
        val config = configService.loadConfig().progression
        val key = "${config.baseXp}|${config.levelExponent}|${config.linearBonusPerLevel}"
        curveCache?.let { cached ->
            if (curveCacheKey == key) return cached
        }
        val built = ProgressionCurve.from(config)
        curveCache = built
        curveCacheKey = key
        return built
    }

    override fun awardExperience(guildId: UUID, experience: Int, source: ExperienceSource): Int? {
        if (experience <= 0) return null
        val mainConfig = configService.loadConfig()
        if ((source == ExperienceSource.CLAIM_CREATED || source == ExperienceSource.CLAIM_DESTROYED) &&
            !mainConfig.claimsEnabled
        ) return null

        val configured = mainConfig.progression.sourcePolicies.getValue(source)
        val rawXpPolicy = configured.copy(awardXp = 1)
        return when (val result = permanentExperienceService.award(
            ExperienceAwardRequest(
                guildId = guildId,
                actorId = null,
                source = source,
                units = experience,
                occurredAt = Instant.now(),
            ),
            rawXpPolicy,
        )) {
            is ExperienceAwardResult.Awarded -> result.leveledUpTo
            is ExperienceAwardResult.NoAllowance -> null
            is ExperienceAwardResult.Rejected -> null
        }
    }

    override fun awardUncappedSystemExperience(
        guildId: UUID,
        experience: Int,
        source: ExperienceSource,
    ): Int? {
        require(source == ExperienceSource.WEEKLY_ACTIVITY || source == ExperienceSource.ADMIN_BONUS) {
            "Only trusted system sources may bypass caps"
        }
        if (experience <= 0) return null
        val policy = ExperiencePolicy(source, source.defaultPool, 1, 0, CapPeriod.UNLIMITED, true)
        return when (val result = permanentExperienceService.award(
            ExperienceAwardRequest(guildId, null, source, experience, Instant.now()),
            policy,
        )) {
            is ExperienceAwardResult.Awarded -> result.leveledUpTo
            is ExperienceAwardResult.NoAllowance -> null
            is ExperienceAwardResult.Rejected -> null
        }
    }

    override fun getExperienceForNextLevel(currentLevel: Int): Int =
        curve().experienceForNextLevel(currentLevel)

    override fun removeExperience(guildId: UUID, amount: Int, source: ExperienceSource): Int {
        try {
            if (amount <= 0) return getLevelFromExperience(progressionRepository.getGuildProgression(guildId)?.totalExperience ?: 0)
            val progression = progressionRepository.getGuildProgression(guildId)
                ?: GuildProgression.create(guildId, getExperienceForNextLevel(1))

            val newTotalExperience = (progression.totalExperience - amount).coerceAtLeast(0)
            val newLevel = getLevelFromExperience(newTotalExperience)

            val updatedProgression = progression.copy(
                totalExperience = newTotalExperience,
                currentLevel = newLevel,
                experienceThisLevel = getExperienceInCurrentLevel(newTotalExperience),
                experienceForNextLevel = getExperienceForNextLevel(newLevel),
                lastUpdated = Instant.now()
            )
            val saved = progressionRepository.saveGuildProgression(updatedProgression)
            if (!saved) {
                logger.error("Failed to save guild progression for guild $guildId (strike XP penalty)")
                return progression.currentLevel
            }
            progressionRepository.recordExperienceTransaction(
                ExperienceTransaction(
                    guildId = guildId,
                    source = source,
                    amount = -amount,
                    description = "Strike penalty: removed $amount XP"
                )
            )
            syncGuildLevelField(guildId, newLevel)
            logger.info("Guild $guildId XP reduced by $amount -> level $newLevel (strike penalty)")
            return newLevel
        } catch (e: Exception) {
            logger.error("Error reducing experience for guild $guildId (strike penalty)", e)
            return progressionRepository.getGuildProgression(guildId)?.currentLevel ?: 1
        }
    }

    override fun reduceLevel(guildId: UUID, levels: Int, source: ExperienceSource): Int {
        try {
            val progression = progressionRepository.getGuildProgression(guildId)
                ?: GuildProgression.create(guildId, getExperienceForNextLevel(1))

            val currentLevel = progression.currentLevel
            val targetLevel = (currentLevel - levels).coerceAtLeast(1)
            if (targetLevel == currentLevel) return currentLevel

            // Set total XP to the threshold of the target level (start of that level).
            val newTotalExperience = getTotalExperienceForLevel(targetLevel)
            val updatedProgression = progression.copy(
                totalExperience = newTotalExperience,
                currentLevel = targetLevel,
                experienceThisLevel = getExperienceInCurrentLevel(newTotalExperience),
                experienceForNextLevel = getExperienceForNextLevel(targetLevel),
                lastUpdated = Instant.now()
            )
            val saved = progressionRepository.saveGuildProgression(updatedProgression)
            if (!saved) {
                logger.error("Failed to save guild progression for guild $guildId (strike level penalty)")
                return currentLevel
            }
            progressionRepository.recordExperienceTransaction(
                ExperienceTransaction(
                    guildId = guildId,
                    source = source,
                    amount = -(progression.totalExperience - newTotalExperience),
                    description = "Strike penalty: reduced level $currentLevel -> $targetLevel"
                )
            )
            syncGuildLevelField(guildId, targetLevel)
            logger.info("Guild $guildId level reduced $currentLevel -> $targetLevel (strike penalty)")
            return targetLevel
        } catch (e: Exception) {
            logger.error("Error reducing level for guild $guildId (strike penalty)", e)
            return progressionRepository.getGuildProgression(guildId)?.currentLevel ?: 1
        }
    }

    override fun getTotalExperienceForLevel(targetLevel: Int): Int =
        curve().totalExperienceForLevel(targetLevel)

    override fun getLevelFromExperience(totalExperience: Int): Int =
        curve().levelFromExperience(totalExperience)

    override fun getLevelProgress(totalExperience: Int): Pair<Int, Int> {
        val currentLevel = getLevelFromExperience(totalExperience)
        val experienceInCurrentLevel = getExperienceInCurrentLevel(totalExperience)
        val experienceForNextLevel = getExperienceForNextLevel(currentLevel)
        
        return Pair(experienceInCurrentLevel, experienceForNextLevel)
    }

    /**
     * Gets the experience within the current level (0 to experienceForNextLevel-1).
     */
    private fun getExperienceInCurrentLevel(totalExperience: Int): Int =
        curve().experienceInCurrentLevel(totalExperience)

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
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        var totalBonus = 0
        for (level in 1..progression.currentLevel) {
            totalBonus += levelRewards[level]?.claimBlocks ?: 0
        }
        return totalBonus
    }

    override fun getMaxClaimCount(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 0
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        var totalBonus = 0
        for (level in 1..progression.currentLevel) {
            totalBonus += levelRewards[level]?.claimCount ?: 0
        }
        return totalBonus
    }

    override fun getBankInterestRate(guildId: UUID): Double {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 0.0
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        var maxRate = 0.0
        for (level in 1..progression.currentLevel) {
            val rate = levelRewards[level]?.interestRate ?: 0.0
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
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the highest home limit for the current level or below
        var maxHomes = 1 // Default base
        for (level in 1..progression.currentLevel) {
            val homes = levelRewards[level]?.homes ?: 1
            if (homes > maxHomes) maxHomes = homes
        }
        return maxHomes
    }

    override fun getMaxBankBalance(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 50000 // Default
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the highest bank limit for the current level or below
        var maxLimit = 50000 // Default base
        for (level in 1..progression.currentLevel) {
            val limit = levelRewards[level]?.bankLimit ?: 0
            if (limit > maxLimit) maxLimit = limit
        }
        return maxLimit
    }

    override fun getMaxMembers(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 10 // Default
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the highest member limit for the current level or below
        var maxMembers = 10 // Default base
        for (level in 1..progression.currentLevel) {
            val members = levelRewards[level]?.members ?: 10
            if (members > maxMembers) maxMembers = members
        }
        return maxMembers
    }

    override fun getWithdrawalFeeMultiplier(guildId: UUID): Double {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 1.0 // Default (no reduction)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the lowest multiplier (best reduction) for the current level or below
        var multiplier = 1.0 // Default (no reduction)
        for (level in 1..progression.currentLevel) {
            val levelMultiplier = levelRewards[level]?.withdrawalFeeMultiplier ?: 1.0
            if (levelMultiplier < multiplier) multiplier = levelMultiplier
        }
        return multiplier
    }

    override fun getHomeCooldownMultiplier(guildId: UUID): Double {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 1.0 // Default (no reduction)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the lowest multiplier (best reduction) for the current level or below
        var multiplier = 1.0 // Default (no reduction)
        for (level in 1..progression.currentLevel) {
            val levelMultiplier = levelRewards[level]?.homeCooldownMultiplier ?: 1.0
            if (levelMultiplier < multiplier) multiplier = levelMultiplier
        }
        return multiplier
    }

    override fun getMaxWars(guildId: UUID): Int {
        val progression = progressionRepository.getGuildProgression(guildId) ?: return 3 // Default
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()

        // Get the highest war slots for the current level or below
        var maxWars = 3 // Default base
        for (level in 1..progression.currentLevel) {
            val wars = levelRewards[level]?.warSlots ?: 3
            if (wars > maxWars) maxWars = wars
        }
        return maxWars
    }

    override fun syncGuildLevels(): Int {
        var updated = 0
        try {
            for (guild in guildRepository.getAll()) {
                val progression = progressionRepository.getGuildProgression(guild.id) ?: continue
                val computed = getLevelFromExperience(progression.totalExperience)
                if (computed != guild.level) {
                    if (guildRepository.update(guild.copy(level = computed))) {
                        updated++
                        logger.info("Backfilled Guild.level for ${guild.name} (${guild.id}): ${guild.level} -> $computed")
                    } else {
                        logger.warn("Backfill failed to update Guild.level for ${guild.name} (${guild.id}): ${guild.level} -> $computed")
                    }
                }
                if (computed != progression.currentLevel) {
                    val saved = progressionRepository.saveGuildProgression(
                        progression.copy(
                            currentLevel = computed,
                            experienceThisLevel = progression.totalExperience - getTotalExperienceForLevel(computed),
                            experienceForNextLevel = getExperienceForNextLevel(computed),
                            lastUpdated = Instant.now()
                        )
                    )
                    if (!saved) {
                        logger.warn("Backfill failed to update GuildProgression for ${guild.id}: currentLevel ${progression.currentLevel} -> $computed")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to sync guild levels", e)
        }
        return updated
    }

    private fun syncGuildLevelField(guildId: UUID, newLevel: Int) {
        try {
            val guild = guildRepository.getById(guildId) ?: return
            if (guild.level != newLevel) {
                if (!guildRepository.update(guild.copy(level = newLevel))) {
                    logger.warn("Failed to persist Guild.level for $guildId: ${guild.level} -> $newLevel")
                }
            }
        } catch (e: Exception) {
            // Don't let a stale Guild.level write fail the XP award.
            logger.error("Failed to sync Guild.level for $guildId to $newLevel", e)
        }
    }

    override fun processLevelUp(guildId: UUID, newLevel: Int): List<PerkType> {
        val newPerks = getPerksForLevel(newLevel)

        // Notifications, perk effects, and GuildLevelUpEvent all touch Bukkit API and
        // must run on the primary thread. awardExperience may be invoked from a virtual
        // thread (see ProgressionEventListener), so bounce the side effects when needed.
        val sideEffects = Runnable {
            notifyGuildMembers(guildId, newLevel, newPerks)
            applyPerkEffects(guildId, newPerks)
            Bukkit.getPluginManager().callEvent(GuildLevelUpEvent(guildId, newLevel))
        }
        if (Bukkit.isPrimaryThread()) {
            sideEffects.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, sideEffects)
        }

        return newPerks
    }

    /**
     * Notifies all online guild members about the level up.
     */
    private fun notifyGuildMembers(guildId: UUID, newLevel: Int, newPerks: List<PerkType>) {
        try {
            val guild = guildRepository.getById(guildId) ?: return

            // Get all online members
            val guildMembers = memberRepository.getByGuild(guildId).map { it.playerId }.toSet()
            val onlineMembers = Bukkit.getOnlinePlayers().filter { player ->
                guildMembers.contains(player.uniqueId)
            }
            
            // Send notifications
            for (player in onlineMembers) {
                // Send title and subtitle using Adventure API
                val title = Title.title(
                    lang.msg("notification.progression.level_up.title"),
                    lang.msg("notification.progression.level_up.subtitle", "guild" to guild.name, "level" to newLevel),
                    Title.Times.times(
                        Duration.ofMillis(500),  // fadeIn (10 ticks = 500ms)
                        Duration.ofSeconds(3),   // stay (60 ticks = 3s)
                        Duration.ofMillis(500)   // fadeOut (10 ticks = 500ms)
                    )
                )
                player.showTitle(title)
                
                // Send chat message
                player.sendMessage(lang.msg("notification.progression.level_up.divider"))
                player.sendMessage(lang.msg("notification.progression.level_up.header"))
                player.sendMessage(lang.msg("notification.progression.level_up.blank"))
                player.sendMessage(lang.msg("notification.progression.level_up.guild", "guild" to guild.name))
                player.sendMessage(lang.msg("notification.progression.level_up.level", "level" to newLevel))
                
                if (newPerks.isNotEmpty()) {
                    player.sendMessage(lang.msg("notification.progression.level_up.perks"))
                    for (perk in newPerks) {
                        player.sendMessage(lang.msg("notification.progression.level_up.perk", "perk" to perk.getDisplayName()))
                    }
                }
                
                player.sendMessage(lang.msg("notification.progression.level_up.divider"))
                
                // Play sound effects based on perks
                if (hasAnnouncementSoundEffects(guildId)) {
                    player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
                } else {
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                }
            }
            
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
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
            PerkType.ALLY_HOME_ACCESS -> "Ally Home Teleportation"
        }
    }

    override fun getDailySourceXp(guildId: UUID): Map<ExperienceSource, Int> {
        return try {
            val todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            val transactions = progressionRepository.getExperienceTransactions(guildId, 1000)
                .filter { it.timestamp >= todayStart }
                .groupBy { it.source }
                .mapValues { (_, txns) -> txns.sumOf { it.amount.coerceAtLeast(0) } }
            ExperienceSource.entries.associateWith { transactions[it] ?: 0 }
        } catch (e: Exception) {
            logger.error("Error calculating daily source XP for guild $guildId", e)
            emptyMap()
        }
    }

    override fun getDailyCap(source: ExperienceSource): Int {
        return when (source) {
            ExperienceSource.BANK_DEPOSIT -> 1000
            ExperienceSource.MEMBER_JOINED -> 200
            ExperienceSource.WAR_WON -> 500
            ExperienceSource.WAR_LOST -> 200
            ExperienceSource.PLAYER_KILL -> 1000
            ExperienceSource.MOB_KILL -> 800
            ExperienceSource.CROP_BREAK -> 500
            ExperienceSource.BLOCK_BREAK -> 600
            ExperienceSource.BLOCK_PLACE -> 600
            ExperienceSource.CRAFTING -> 400
            ExperienceSource.SMELTING -> 400
            ExperienceSource.FISHING -> 300
            ExperienceSource.ENCHANTING -> 300
            ExperienceSource.CLAIM_CREATED -> 500
            ExperienceSource.CLAIM_DESTROYED -> 0
            ExperienceSource.WEEKLY_ACTIVITY -> 0
            ExperienceSource.ADMIN_BONUS -> Int.MAX_VALUE
            else -> 0
        }
    }
}
