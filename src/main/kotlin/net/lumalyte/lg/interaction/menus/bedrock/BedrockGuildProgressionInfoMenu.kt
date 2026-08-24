package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val progressionIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.legacy("bedrock.progression.title", "guild" to guild.name))
            .apply { progressionIcon?.let { icon(it) } }
            .label(lang.raw("bedrock.progression.description"))
            .label(createSectionHeader(lang.raw("bedrock.progression.header.level")))
            .label(createLevelAndExperienceSection())
            .label(createSectionHeader(lang.raw("bedrock.progression.header.unlocked")))
            .label(createUnlockedPerksSection())
            .label(createSectionHeader(lang.raw("bedrock.progression.header.available")))
            .label(createAvailablePerksSection())
            .label(createSectionHeader(lang.raw("bedrock.progression.header.benefits")))
            .label(createBenefitsSection())
            .label(createSectionHeader(lang.raw("bedrock.progression.header.activity")))
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
        return lang.legacy("bedrock.progression.header.format", "title" to title)
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

        return when {
            progressPercent >= 75 -> lang.legacy(
                "bedrock.progression.level.green",
                "level" to currentLevel,
                "total_experience" to totalExperience,
                "level_experience" to experienceThisLevel,
                "next_experience" to experienceForNextLevel,
                "progress" to progressPercent
            )
            progressPercent >= 50 -> lang.legacy(
                "bedrock.progression.level.yellow",
                "level" to currentLevel,
                "total_experience" to totalExperience,
                "level_experience" to experienceThisLevel,
                "next_experience" to experienceForNextLevel,
                "progress" to progressPercent
            )
            progressPercent >= 25 -> lang.legacy(
                "bedrock.progression.level.gold",
                "level" to currentLevel,
                "total_experience" to totalExperience,
                "level_experience" to experienceThisLevel,
                "next_experience" to experienceForNextLevel,
                "progress" to progressPercent
            )
            else -> lang.legacy(
                "bedrock.progression.level.red",
                "level" to currentLevel,
                "total_experience" to totalExperience,
                "level_experience" to experienceThisLevel,
                "next_experience" to experienceForNextLevel,
                "progress" to progressPercent
            )
        }
    }

    private fun createUnlockedPerksSection(): String {
        val unlockedPerks = progressionService.getUnlockedPerks(guild.id)

        if (unlockedPerks.isEmpty()) {
            return lang.legacy("bedrock.progression.perks.none")
        }

        val perkList = unlockedPerks.joinToString("\n") {
            lang.legacy("bedrock.progression.perks.unlocked_row", "perk" to getLocalizedPerkName(it))
        }
        return perkList
    }

    private fun createAvailablePerksSection(): String {
        val nextLevel = (progressionRepository.getGuildProgression(guild.id)?.currentLevel ?: guild.level) + 1
        val nextLevelPerks = progressionService.getPerksForLevel(nextLevel)

        if (nextLevelPerks.isEmpty()) {
            return lang.legacy("bedrock.progression.perks.more")
        }

        val perkList = nextLevelPerks.joinToString("\n") {
            lang.legacy("bedrock.progression.perks.available_row", "perk" to getLocalizedPerkName(it))
        }
        return lang.legacy("bedrock.progression.perks.next_level", "level" to nextLevel, "perks" to perkList)
    }

    private fun createBenefitsSection(): String {
        val maxClaimBlocks = progressionService.getMaxClaimBlocks(guild.id)
        val maxHomes = progressionService.getMaxHomes(guild.id)
        val bankInterestRate = progressionService.getBankInterestRate(guild.id)

        val claims = if (maxClaimBlocks >= Int.MAX_VALUE) {
            lang.raw("bedrock.progression.benefits.unlimited")
        } else {
            maxClaimBlocks.toString()
        }
        return lang.legacy(
            "bedrock.progression.benefits.summary",
            "claims" to claims,
            "homes" to maxHomes,
            "interest" to (bankInterestRate * 100).toInt()
        )
    }

    private fun createActivitySection(): String {
        // Calculate this week's activity
        val weekStart = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS)
        val weekEnd = Instant.now()

        val activityScore = progressionService.calculateWeeklyActivityScore(guild.id, weekStart, weekEnd)
        val percentile = progressionService.getActivityPercentile(guild.id, net.lumalyte.lg.application.services.ActivityPeriod.WEEKLY)

        return lang.legacy(
            "bedrock.progression.activity.summary",
            "activity_score" to activityScore,
            "percentile" to percentile.toInt()
        )
    }

    private fun getLocalizedPerkName(perk: net.lumalyte.lg.domain.values.PerkType): String {
        return when (perk) {
            // Claim perks
            net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_BLOCKS -> lang.raw("bedrock.progression.perk.increased_claim_blocks")
            net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_COUNT -> lang.raw("bedrock.progression.perk.increased_claim_count")
            net.lumalyte.lg.domain.values.PerkType.FASTER_CLAIM_REGEN -> lang.raw("bedrock.progression.perk.faster_claim_regen")

            // Bank perks
            net.lumalyte.lg.domain.values.PerkType.HIGHER_BANK_BALANCE -> lang.raw("bedrock.progression.perk.higher_bank_balance")
            net.lumalyte.lg.domain.values.PerkType.BANK_INTEREST -> lang.raw("bedrock.progression.perk.bank_interest")
            net.lumalyte.lg.domain.values.PerkType.INCREASED_BANK_LIMIT -> lang.raw("bedrock.progression.perk.increased_bank_limit")
            net.lumalyte.lg.domain.values.PerkType.REDUCED_WITHDRAWAL_FEES -> lang.raw("bedrock.progression.perk.reduced_withdrawal_fees")

            // Home perks
            net.lumalyte.lg.domain.values.PerkType.ADDITIONAL_HOMES -> lang.raw("bedrock.progression.perk.additional_homes")
            net.lumalyte.lg.domain.values.PerkType.TELEPORT_COOLDOWN_REDUCTION -> lang.raw("bedrock.progression.perk.teleport_cooldown_reduction")
            net.lumalyte.lg.domain.values.PerkType.HOME_TELEPORT_SOUND_EFFECTS -> lang.raw("bedrock.progression.perk.home_teleport_sound_effects")

            // Audio/Visual perks
            net.lumalyte.lg.domain.values.PerkType.CUSTOM_BANNER_COLORS -> lang.raw("bedrock.progression.perk.custom_banner_colors")
            net.lumalyte.lg.domain.values.PerkType.ANIMATED_EMOJIS -> lang.raw("bedrock.progression.perk.animated_emojis")
            net.lumalyte.lg.domain.values.PerkType.SPECIAL_PARTICLES -> lang.raw("bedrock.progression.perk.special_particles")
            net.lumalyte.lg.domain.values.PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> lang.raw("bedrock.progression.perk.announcement_sound_effects")
            net.lumalyte.lg.domain.values.PerkType.WAR_DECLARATION_SOUND_EFFECTS -> lang.raw("bedrock.progression.perk.war_declaration_sound_effects")
            net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS -> lang.raw("bedrock.progression.perk.ally_home_access")
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
