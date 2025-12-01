package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition peace agreement menu using Cumulus CustomForm
 * Allows proposing peace with terms and reparations
 */
class BedrockPeaceAgreementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val warService: WarService by inject()
    private val guildRepository: GuildRepository by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val peaceIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        // Get active wars this guild is involved in
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
        val warOpponents = activeWars.map { war ->
            val opponentId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildRepository.getById(opponentId)
            "vs ${opponentGuild?.name ?: "Unknown"}"
        }

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.peace.title")} - ${guild.name}")
            .apply { peaceIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.peace.description"))
            .apply {
                if (warOpponents.isEmpty()) {
                    label(bedrockLocalization.getBedrockString(player, "guild.peace.no.wars"))
                } else {
                    dropdown(
                        bedrockLocalization.getBedrockString(player, "guild.peace.select.war"),
                        warOpponents
                    )
                    input(
                        bedrockLocalization.getBedrockString(player, "guild.peace.terms"),
                        bedrockLocalization.getBedrockString(player, "guild.peace.terms.placeholder"),
                        ""
                    )
                    input(
                        bedrockLocalization.getBedrockString(player, "guild.peace.reparations"),
                        bedrockLocalization.getBedrockString(player, "guild.peace.reparations.placeholder"),
                        "0"
                    )
                }
            }
            .validResultHandler { response ->
                if (warOpponents.isEmpty()) {
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val warIndex = response.asDropdown(1)
                val terms = response.asInput(2) ?: ""
                val reparationsStr = response.asInput(3) ?: "0"
                val reparations = reparationsStr.toIntOrNull() ?: 0

                val selectedWar = activeWars.getOrNull(warIndex)
                if (selectedWar == null) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.peace.invalid.war"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                handlePeaceProposal(selectedWar.id, terms, reparations)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handlePeaceProposal(warId: java.util.UUID, terms: String, reparations: Int) {
        // Propose peace with money offering
        val offering = if (reparations > 0) {
            net.lumalyte.lg.domain.entities.PeaceOffering(money = reparations)
        } else {
            null
        }

        val peace = warService.proposePeaceAgreement(
            warId = warId,
            proposingGuildId = guild.id,
            peaceTerms = terms,
            offering = offering
        )

        if (peace != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.peace.proposed.success"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.peace.proposed.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
