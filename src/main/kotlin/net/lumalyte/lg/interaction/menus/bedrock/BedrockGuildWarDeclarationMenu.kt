package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.WarObjective
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.util.logging.Logger

/**
 * Bedrock Edition guild war declaration menu using Cumulus CustomForm
 * Allows declaring war with guild selection and basic configuration
 */
class BedrockGuildWarDeclarationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val warService: WarService by inject()
    private val guildRepository: GuildRepository by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val bankService: BankService by inject()
    private val seasonalElo: net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            return CustomForm.builder()
                .title(lang.bedrock("bedrock.war_declaration.title", "guild" to guild.name))
                .label(lang.bedrock("bedrock.war_declaration.feedback.no_permission"))
                .validResultHandler { _ -> bedrockNavigator.goBack() }
                .closedOrInvalidResultHandler { _, _ -> bedrockNavigator.goBack() }
                .build()
        }

        val config = getBedrockConfig()
        val mainConfig = configService.loadConfig()
        val claimsEnabled = mainConfig.claimsEnabled
        val warIcon = BedrockFormUtils.createFormImage(config, config.guildWarsIconUrl, config.guildWarsIconPath)

        // Get list of guilds that can be targeted
        val allGuilds = guildRepository.getAll()
            .filter { it.id != guild.id } // Exclude own guild
            .filter { it.mode == GuildMode.HOSTILE } // Only hostile guilds
            .sortedBy { it.name }

        val guildNames = allGuilds.map { it.name }
        val guildOptions = if (guildNames.isEmpty()) listOf(lang.bedrock("bedrock.war_declaration.no_target_option")) else guildNames
        val durationOptions = listOf(
            lang.bedrock("bedrock.war_declaration.duration.one_day"),
            lang.bedrock("bedrock.war_declaration.duration.three_days"),
            lang.bedrock("bedrock.war_declaration.duration.seven_days"),
            lang.bedrock("bedrock.war_declaration.duration.fourteen_days"),
            lang.bedrock("bedrock.war_declaration.duration.thirty_days")
        )
        val guildBalance = bankService.getBalance(guild.id)
        val killCap = warService.getWarKillWinTarget().coerceAtLeast(1)
        val killTargets = (listOf(5, 10, 25, 50).filter { it <= killCap } + killCap)
            .filter { it > 0 }
            .distinct()
            .sorted()
        val survivalTargets = listOf(12, 24, 48, 72)
        val claimTargets = listOf(1, 3, 5, 10)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.war_declaration.title", "guild" to guild.name))
            .apply { warIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.war_declaration.description"))
            .dropdown(
                lang.bedrock("bedrock.war_declaration.target"),
                guildOptions
            )
            .dropdown(
                lang.bedrock("bedrock.war_declaration.duration.label"),
                durationOptions,
                2 // Default to 7 days
            )
            .toggle(
                lang.bedrock("bedrock.war_declaration.rated.label"),
                false
            )
            .input(
                lang.bedrock("bedrock.war_declaration.terms.label"),
                lang.bedrock("bedrock.war_declaration.terms.placeholder"),
                ""
            )
            .apply {
                if (claimsEnabled) {
                    toggle(
                        lang.bedrock("bedrock.war_declaration.objective.territory"),
                        false
                    )
                    dropdown(
                        lang.bedrock("bedrock.war_declaration.objective.territory_target"),
                        claimTargets.map { lang.bedrock("bedrock.war_declaration.objective.claim_target", "count" to it) },
                        2
                    )
                }
            }
            .toggle(
                lang.bedrock("bedrock.war_declaration.objective.kills"),
                true
            )
            .dropdown(
                lang.bedrock("bedrock.war_declaration.objective.kills_target"),
                killTargets.map { lang.bedrock("bedrock.war_declaration.objective.kill_target", "count" to it) },
                killTargets.indexOf(killCap).coerceAtLeast(0)
            )
            .toggle(
                lang.bedrock("bedrock.war_declaration.objective.survival"),
                false
            )
            .dropdown(
                lang.bedrock("bedrock.war_declaration.objective.survival_target"),
                survivalTargets.map { lang.bedrock("bedrock.war_declaration.objective.survival_hours", "count" to it) },
                1
            )
            .label(
                lang.bedrock("bedrock.war_declaration.wager.info", "balance" to guildBalance)
            )
            .slider(
                lang.bedrock("bedrock.war_declaration.wager.amount"),
                0f,
                guildBalance.toFloat().coerceAtMost(100000f),
                100f,
                0f
            )
            .input(
                lang.bedrock("bedrock.war_declaration.wager.custom_label"),
                lang.bedrock("bedrock.war_declaration.wager.custom_placeholder"),
                ""
            )
            .validResultHandler { response ->
                if (guildNames.isEmpty()) {
                    player.sendMessage(lang.msg("bedrock.war_declaration.feedback.no_targets"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val targetIndex = response.next() as? Int ?: 0
                val durationIndex = response.next() as? Int ?: 2
                val rated = response.next() as? Boolean ?: false
                val terms = response.next() as? String ?: ""

                val territoryObjective = if (claimsEnabled) {
                    response.next() as? Boolean ?: false
                } else {
                    false
                }
                val territoryTarget = if (claimsEnabled) {
                    claimTargets.getOrElse(response.next() as? Int ?: 2) { 5 }
                } else {
                    0
                }

                val killsObjective = response.next() as? Boolean ?: true
                val killTarget = killTargets.getOrElse(response.next() as? Int ?: 0) { killCap }
                val survivalObjective = response.next() as? Boolean ?: false
                val survivalTarget = survivalTargets.getOrElse(response.next() as? Int ?: 1) { 24 }
                val wagerSlider = response.next() as? Float ?: 0f
                val wagerInput = response.next() as? String ?: ""

                val targetGuild = allGuilds.getOrNull(targetIndex)
                if (targetGuild == null) {
                    player.sendMessage(lang.msg("bedrock.war_declaration.feedback.invalid_target"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val duration = when (durationIndex) {
                    0 -> Duration.ofDays(1)
                    1 -> Duration.ofDays(3)
                    2 -> Duration.ofDays(7)
                    3 -> Duration.ofDays(14)
                    4 -> Duration.ofDays(30)
                    else -> Duration.ofDays(7)
                }

                val objectives = mutableSetOf<WarObjective>()
                if (territoryObjective) {
                    objectives += WarObjective(
                        type = ObjectiveType.CLAIMS_CAPTURED,
                        targetValue = territoryTarget,
                        description = lang.bedrock(
                            "bedrock.war_declaration.objective.territory_description",
                            "count" to territoryTarget
                        )
                    )
                }
                if (killsObjective) {
                    objectives += WarObjective(
                        type = ObjectiveType.KILLS,
                        targetValue = killTarget,
                        description = lang.bedrock(
                            "bedrock.war_declaration.objective.kills_description",
                            "count" to killTarget
                        )
                    )
                }
                if (survivalObjective) {
                    objectives += WarObjective(
                        type = ObjectiveType.TIME_SURVIVAL,
                        targetValue = survivalTarget,
                        description = lang.bedrock(
                            "bedrock.war_declaration.objective.survival_description",
                            "count" to survivalTarget
                        )
                    )
                }

                val wagerAmount = if (wagerInput.isNotBlank()) {
                    wagerInput.toIntOrNull() ?: 0
                } else {
                    wagerSlider.toInt()
                }

                handleWarDeclaration(targetGuild, duration, objectives, terms, wagerAmount, rated)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleWarDeclaration(
        targetGuild: Guild,
        duration: Duration,
        objectives: Set<WarObjective>,
        terms: String,
        wagerAmount: Int,
        rated: Boolean,
    ) {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.no_permission"))
            bedrockNavigator.goBack()
            return
        }

        // Validate guild can declare war
        if (guild.mode != GuildMode.HOSTILE) {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.peaceful"))
            bedrockNavigator.goBack()
            return
        }

        // Check if already at war with this guild
        val existingWar = warService.getWarsForGuild(guild.id)
            .any { (it.declaringGuildId == targetGuild.id || it.defendingGuildId == targetGuild.id) && it.isActive }

        if (existingWar) {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.already_at_war"))
            bedrockNavigator.goBack()
            return
        }

        if (rated) {
            val own = seasonalElo.view(guild.id)
            val opponent = seasonalElo.view(targetGuild.id)
            if (own?.eligible != true || opponent?.eligible != true || own.chapterId != opponent.chapterId) {
                player.sendMessage(lang.msg("bedrock.war_declaration.feedback.rated_unavailable"))
                bedrockNavigator.goBack()
                return
            }
        }

        // REQ-024: no auto-accept — every declaration goes through the accept/decline
        // flow. REQ-039: escrow is handled by the war service on acceptance; the menu
        // no longer moves bank funds itself.
        val declaration = warService.createWarDeclaration(
            declaringGuildId = guild.id,
            defendingGuildId = targetGuild.id,
            duration = duration,
            objectives = objectives,
            wagerAmount = wagerAmount,
            terms = if (terms.isNotBlank()) terms else null,
            actorId = player.uniqueId,
            rated = rated,
        )

        if (declaration != null) {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.sent", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.duration", "days" to duration.toDays()))
            if (declaration.isRated) {
                player.sendMessage(lang.msg("bedrock.war_declaration.feedback.rated_created"))
            } else {
                player.sendMessage(lang.msg("bedrock.war_declaration.feedback.unrated_created"))
            }
            if (objectives.isNotEmpty()) {
                player.sendMessage(lang.msg("bedrock.war_declaration.feedback.objectives", "count" to objectives.size))
            }
            if (wagerAmount > 0) {
                player.sendMessage(lang.msg("bedrock.war_declaration.feedback.wager", "amount" to wagerAmount))
            }
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.awaiting"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
