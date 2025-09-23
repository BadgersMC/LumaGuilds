package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.logging.Logger

/**
 * Bedrock Edition guild progression info menu using Cumulus CustomForm
 * Displays comprehensive guild level, experience, perks, and benefits information
 */
class BedrockGuildProgressionInfoMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val progressionService: ProgressionService by inject()
    private val progressionRepository: ProgressionRepository by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val progressionIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.progression.title")} - ${guild.name}")
            .apply { progressionIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.progression.description"))
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.progression.current.level")))
            .label(createLevelAndExperienceSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.progression.perks.unlocked")))
            .label(createUnlockedPerksSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.progression.perks.available")))
            .label(createAvailablePerksSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.progression.benefits.header")))
            .label(createBenefitsSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.progression.activity.header")))
            .label(createActivitySection())
            .validResultHandler { response ->
                // Read-only menu, just close
                bedrockNavigator.goBack()
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun createSectionHeader(title: String): String {
        return "§e§l$title"
    }

    private fun createLevelAndExperienceSection(): String {
        val progression = progressionRepository.getGuildProgression(guild.id)
        val currentLevel = progression?.currentLevel ?: guild.level
        val totalExperience = progression?.totalExperience ?: 0
        val experienceThisLevel = progression?.experienceThisLevel ?: 0
        val experienceForNextLevel = progression?.experienceForNextLevel ?: progressionService.getExperienceForNextLevel(currentLevel)

        val progressPercent = if (experienceForNextLevel > 0) {
            (experienceThisLevel.toDouble() / experienceForNextLevel.toDouble() * 100).toInt()
        } else {
            100
        }

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.progression.current.level")}: $currentLevel
            |${bedrockLocalization.getBedrockString(player, "guild.progression.total.experience")}: $totalExperience XP
            |${bedrockLocalization.getBedrockString(player, "guild.progression.experience.this.level")}: $experienceThisLevel XP
            |${bedrockLocalization.getBedrockString(player, "guild.progression.experience.to.next")}: $experienceForNextLevel XP
            |${bedrockLocalization.getBedrockString(player, "guild.progression.progress")}: $progressPercent%
        """.trimMargin()
    }

    private fun createUnlockedPerksSection(): String {
        val unlockedPerks = progressionService.getUnlockedPerks(guild.id)

        if (unlockedPerks.isEmpty()) {
            return bedrockLocalization.getBedrockString(player, "guild.progression.perks.none")
        }

        val perkList = unlockedPerks.joinToString("\n• ") { "• ${getLocalizedPerkName(it)}" }
        return perkList
    }

    private fun createAvailablePerksSection(): String {
        val nextLevel = (progressionRepository.getGuildProgression(guild.id)?.currentLevel ?: guild.level) + 1
        val nextLevelPerks = progressionService.getPerksForLevel(nextLevel)

        if (nextLevelPerks.isEmpty()) {
            return bedrockLocalization.getBedrockString(player, "guild.progression.perks.more")
        }

        val perkList = nextLevelPerks.joinToString("\n• ") { "• ${getLocalizedPerkName(it)}" }
        return "Level $nextLevel:\n$perkList"
    }

    private fun createBenefitsSection(): String {
        val maxClaimBlocks = progressionService.getMaxClaimBlocks(guild.id)
        val maxHomes = progressionService.getMaxHomes(guild.id)
        val bankInterestRate = progressionService.getBankInterestRate(guild.id)

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.progression.benefits.max.claims")}: ${if (maxClaimBlocks >= Int.MAX_VALUE) bedrockLocalization.getBedrockString(player, "guild.progression.benefits.unlimited") else maxClaimBlocks.toString()}
            |${bedrockLocalization.getBedrockString(player, "guild.progression.benefits.max.homes")}: $maxHomes
            |${bedrockLocalization.getBedrockString(player, "guild.progression.benefits.bank.interest")}: ${(bankInterestRate * 100).toInt()}%
        """.trimMargin()
    }

    private fun createActivitySection(): String {
        // Calculate this week's activity
        val weekStart = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS)
        val weekEnd = Instant.now()

        val activityScore = progressionService.calculateWeeklyActivityScore(guild.id, weekStart, weekEnd)
        val percentile = progressionService.getActivityPercentile(guild.id, net.lumalyte.lg.application.services.ActivityPeriod.WEEKLY)

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.progression.activity.score")}: $activityScore
            |${bedrockLocalization.getBedrockString(player, "guild.progression.activity.percentile")}: ${percentile.toInt()}%
        """.trimMargin()
    }

    private fun getLocalizedPerkName(perk: net.lumalyte.lg.application.services.PerkType): String {
        return when (perk) {
            // Claim perks
            net.lumalyte.lg.application.services.PerkType.INCREASED_CLAIM_BLOCKS -> "Increased Claim Blocks"
            net.lumalyte.lg.application.services.PerkType.INCREASED_CLAIM_COUNT -> "Increased Claim Count"
            net.lumalyte.lg.application.services.PerkType.FASTER_CLAIM_REGEN -> "Faster Claim Regeneration"

            // Bank perks
            net.lumalyte.lg.application.services.PerkType.HIGHER_BANK_BALANCE -> "Higher Bank Balance"
            net.lumalyte.lg.application.services.PerkType.BANK_INTEREST -> "Bank Interest"
            net.lumalyte.lg.application.services.PerkType.INCREASED_BANK_LIMIT -> "Increased Bank Limit"
            net.lumalyte.lg.application.services.PerkType.REDUCED_WITHDRAWAL_FEES -> "Reduced Withdrawal Fees"

            // Home perks
            net.lumalyte.lg.application.services.PerkType.ADDITIONAL_HOMES -> "Additional Homes"
            net.lumalyte.lg.application.services.PerkType.TELEPORT_COOLDOWN_REDUCTION -> "Teleport Cooldown Reduction"
            net.lumalyte.lg.application.services.PerkType.HOME_TELEPORT_SOUND_EFFECTS -> "Home Teleport Sound Effects"

            // Audio/Visual perks
            net.lumalyte.lg.application.services.PerkType.CUSTOM_BANNER_COLORS -> "Custom Banner Colors"
            net.lumalyte.lg.application.services.PerkType.ANIMATED_EMOJIS -> "Animated Emojis"
            net.lumalyte.lg.application.services.PerkType.SPECIAL_PARTICLES -> "Special Particles"
            net.lumalyte.lg.application.services.PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> "Announcement Sound Effects"
            net.lumalyte.lg.application.services.PerkType.WAR_DECLARATION_SOUND_EFFECTS -> "War Declaration Sound Effects"
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
