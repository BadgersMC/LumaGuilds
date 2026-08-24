package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val peaceIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        // Get active wars this guild is involved in
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
        val warOpponents = activeWars.map { war ->
            val opponentId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponentGuild = guildRepository.getById(opponentId)
            lang.bedrock(
                "bedrock.peace_agreement.opponent",
                "guild" to (opponentGuild?.name ?: lang.bedrock("bedrock.peace_agreement.unknown_guild"))
            )
        }

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.peace_agreement.title", "guild" to guild.name))
            .apply { peaceIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.peace_agreement.description"))
            .apply {
                if (warOpponents.isEmpty()) {
                    label(lang.bedrock("bedrock.peace_agreement.no_wars"))
                } else {
                    dropdown(
                        lang.bedrock("bedrock.peace_agreement.select_war"),
                        warOpponents
                    )
                    input(
                        lang.bedrock("bedrock.peace_agreement.terms.label"),
                        lang.bedrock("bedrock.peace_agreement.terms.placeholder"),
                        ""
                    )
                    input(
                        lang.bedrock("bedrock.peace_agreement.reparations.label"),
                        lang.bedrock("bedrock.peace_agreement.reparations.placeholder"),
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
                    player.sendMessage(lang.msg("bedrock.peace_agreement.feedback.invalid_war"))
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
            player.sendMessage(lang.msg("bedrock.peace_agreement.feedback.proposed"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.peace_agreement.feedback.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
