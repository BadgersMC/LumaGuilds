package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild member contributions menu using Cumulus SimpleForm
 * Shows net contributions for each member
 */
class BedrockGuildMemberContributionsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get member contributions
        val contributions = bankService.getMemberContributions(guild.id)
            .sortedByDescending { it.netContribution }
            .take(10) // Show top 10 contributors

        val content = if (contributions.isEmpty()) {
            lang.legacy("bedrock.bank.contributions.empty")
        } else {
            val rows = contributions.mapIndexed { index, contribution ->
                val memberName = Bukkit.getOfflinePlayer(contribution.playerId).name
                    ?: lang.raw("bedrock.bank.contributions.unknown_player")
                val key = when {
                    contribution.netContribution > 0 -> "bedrock.bank.contributions.row.positive"
                    contribution.netContribution < 0 -> "bedrock.bank.contributions.row.negative"
                    else -> "bedrock.bank.contributions.row.neutral"
                }
                when (key) {
                    "bedrock.bank.contributions.row.positive" -> lang.legacy(
                        "bedrock.bank.contributions.row.positive",
                        "position" to index + 1,
                        "player" to memberName,
                        "net" to contribution.netContribution,
                        "deposits" to contribution.totalDeposits,
                        "withdrawals" to contribution.totalWithdrawals
                    )
                    "bedrock.bank.contributions.row.negative" -> lang.legacy(
                        "bedrock.bank.contributions.row.negative",
                        "position" to index + 1,
                        "player" to memberName,
                        "net" to contribution.netContribution,
                        "deposits" to contribution.totalDeposits,
                        "withdrawals" to contribution.totalWithdrawals
                    )
                    else -> lang.legacy(
                        "bedrock.bank.contributions.row.neutral",
                        "position" to index + 1,
                        "player" to memberName,
                        "net" to contribution.netContribution,
                        "deposits" to contribution.totalDeposits,
                        "withdrawals" to contribution.totalWithdrawals
                    )
                }
            }.joinToString("\n")
            lang.legacy("bedrock.bank.contributions.content", "contributors" to rows)
        }

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.bank.contributions.title", "guild" to guild.name))
            .content(content)
            .button(lang.raw("bedrock.bank.contributions.button.refresh"))
            .button(lang.raw("bedrock.bank.contributions.button.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> {
                        // Refresh
                        bedrockNavigator.openMenu(BedrockGuildMemberContributionsMenu(menuNavigator, player, guild, logger))
                    }
                    1 -> {
                        // Back
                        bedrockNavigator.goBack()
                    }
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
