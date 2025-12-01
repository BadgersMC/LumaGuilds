package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.ObjectiveType
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

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val warIcon = BedrockFormUtils.createFormImage(config, config.guildWarsIconUrl, config.guildWarsIconPath)

        // Get list of guilds that can be targeted
        val allGuilds = guildRepository.getAll()
            .filter { it.id != guild.id } // Exclude own guild
            .filter { it.mode == GuildMode.HOSTILE } // Only hostile guilds
            .sortedBy { it.name }

        val guildNames = allGuilds.map { it.name }
        val durationOptions = listOf("1 day", "3 days", "7 days", "14 days", "30 days")

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.war.declare.title")} - ${guild.name}")
            .apply { warIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.war.declare.description"))
            .dropdown(
                bedrockLocalization.getBedrockString(player, "guild.war.target"),
                guildNames
            )
            .dropdown(
                bedrockLocalization.getBedrockString(player, "guild.war.duration"),
                durationOptions,
                2 // Default to 7 days
            )
            .input(
                bedrockLocalization.getBedrockString(player, "guild.war.terms"),
                bedrockLocalization.getBedrockString(player, "guild.war.terms.placeholder"),
                ""
            )
            .toggle(
                bedrockLocalization.getBedrockString(player, "guild.war.objective.territory"),
                false
            )
            .toggle(
                bedrockLocalization.getBedrockString(player, "guild.war.objective.kills"),
                true // Default enabled
            )
            .validResultHandler { response ->
                if (guildNames.isEmpty()) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.no.targets"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val targetIndex = response.asDropdown(1)
                val durationIndex = response.asDropdown(2)
                val terms = response.asInput(3) ?: ""
                val territoryObjective = response.asToggle(4)
                val killsObjective = response.asToggle(5)

                val targetGuild = allGuilds.getOrNull(targetIndex)
                if (targetGuild == null) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.invalid.target"))
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
                        description = "Capture 5 enemy claims"
                    ))
                }
                if (killsObjective) {
                    objectives.add(WarObjective(
                        type = ObjectiveType.KILLS,
                        targetValue = 100,
                        description = "Kill 100 enemy players"
                    ))
                }

                handleWarDeclaration(targetGuild, duration, objectives, terms)
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
        terms: String
    ) {
        // Validate guild can declare war
        if (guild.mode != GuildMode.HOSTILE) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.peaceful.mode"))
            bedrockNavigator.goBack()
            return
        }

        // Check if already at war with this guild
        val existingWar = warService.getWarsForGuild(guild.id)
            .any { (it.declaringGuildId == targetGuild.id || it.defendingGuildId == targetGuild.id) && it.isActive }

        if (existingWar) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.already.at.war"))
            bedrockNavigator.goBack()
            return
        }

        // Declare war
        val war = warService.declareWar(
            declaringGuildId = guild.id,
            defendingGuildId = targetGuild.id,
            duration = duration,
            objectives = objectives,
            actorId = player.uniqueId
        )

        if (war != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.declared.success", targetGuild.name))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.war.declared.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
