package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get member contributions
        val contributions = bankService.getMemberContributions(guild.id)
            .sortedByDescending { it.netContribution }
            .take(10) // Show top 10 contributors

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "bank.contributions.title"))
            appendLine()

            if (contributions.isEmpty()) {
                appendLine(bedrockLocalization.getBedrockString(player, "bank.contributions.none"))
            } else {
                appendLine("§7=== ${bedrockLocalization.getBedrockString(player, "bank.contributions.top10")} ===")
                appendLine()
                contributions.forEachIndexed { index, contribution ->
                    val memberName = Bukkit.getOfflinePlayer(contribution.playerId).name ?: "Unknown"
                    val netAmount = contribution.netContribution
                    val color = when {
                        netAmount > 0 -> "§a"
                        netAmount < 0 -> "§c"
                        else -> "§7"
                    }
                    appendLine("§7${index + 1}. §f$memberName: $color$netAmount")
                    appendLine("§7   Deposits: §a+${contribution.totalDeposits} §7Withdrawals: §c-${contribution.totalWithdrawals}")
                }
            }
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "bank.contributions.menu.title")} - ${guild.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "bank.contributions.refresh"))
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
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
