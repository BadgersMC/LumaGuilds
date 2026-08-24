package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankService
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
    private val bankService: BankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
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
            .input(
                lang.bedrock("bedrock.war_declaration.terms.label"),
                lang.bedrock("bedrock.war_declaration.terms.placeholder"),
                ""
            )
            .toggle(
                lang.bedrock("bedrock.war_declaration.objective.territory"),
                false
            )
            .toggle(
                lang.bedrock("bedrock.war_declaration.objective.kills"),
                true // Default enabled
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

                val targetIndex = response.asDropdown(1)
                val durationIndex = response.asDropdown(2)
                val terms = response.asInput(3) ?: ""
                val territoryObjective = response.asToggle(4)
                val killsObjective = response.asToggle(5)
                // Skip label at index 6
                val wagerSlider = response.asSlider(7)
                val wagerInput = response.asInput(8) ?: ""

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
                    objectives.add(WarObjective(
                        type = ObjectiveType.CLAIMS_CAPTURED,
                        targetValue = 5,
                        description = lang.bedrock("bedrock.war_declaration.objective.territory_description")
                    ))
                }
                if (killsObjective) {
                    objectives.add(WarObjective(
                        type = ObjectiveType.KILLS,
                        targetValue = 100,
                        description = lang.bedrock("bedrock.war_declaration.objective.kills_description")
                    ))
                }

                // Parse wager amount (prefer custom input over slider)
                val wagerAmount = if (wagerInput.isNotBlank()) {
                    wagerInput.toIntOrNull() ?: 0
                } else {
                    wagerSlider.toInt()
                }

                handleWarDeclaration(targetGuild, duration, objectives, terms, wagerAmount)
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
        wagerAmount: Int
    ) {
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
            actorId = player.uniqueId
        )

        if (declaration != null) {
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.sent", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("bedrock.war_declaration.feedback.duration", "days" to duration.toDays()))
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
