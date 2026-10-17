package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.MemberContribution
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Paged Bedrock view of every member contribution returned by BankService. */
class BedrockGuildMemberContributionsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val bankService: BankService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private var page = 0

    override fun getForm(): Form {
        val contributions = bankService.getMemberContributions(guild.id)
            .sortedByDescending(MemberContribution::netContribution)
        val pageCount = maxOf(1, (contributions.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pageCount - 1)
        val pageItems = contributions
            .drop(page * PAGE_SIZE)
            .take(PAGE_SIZE)

        val content = if (pageItems.isEmpty()) {
            lang.bedrock("bedrock.bank.contributions.empty")
        } else {
            val rows = pageItems.mapIndexed { index, contribution ->
                renderContribution(page * PAGE_SIZE + index + 1, contribution)
            }.joinToString("\n")
            listOf(
                lang.bedrock(
                    "bedrock.bank.contributions.page_header",
                    "page" to page + 1,
                    "pages" to pageCount,
                    "count" to contributions.size
                ),
                rows
            ).joinToString("\n\n")
        }

        var builder = SimpleForm.builder()
            .title(lang.bedrock("bedrock.bank.contributions.title", "guild" to guild.name))
            .content(content)
        val actions = mutableListOf<() -> Unit>()

        if (page > 0) {
            builder = builder.button(lang.bedrock("bedrock.bank.contributions.button.previous"))
            actions += { runOnServerThread { page--; open() } }
        }
        if (page + 1 < pageCount) {
            builder = builder.button(lang.bedrock("bedrock.bank.contributions.button.next"))
            actions += { runOnServerThread { page++; open() } }
        }

        builder = builder.button(lang.bedrock("bedrock.bank.contributions.button.refresh"))
        actions += { runOnServerThread { open() } }
        builder = builder.button(lang.bedrock("bedrock.bank.contributions.button.back"))
        actions += { runOnServerThread { bedrockNavigator.goBack() } }

        return builder
            .validResultHandler { response ->
                onFormResponseReceived()
                actions.getOrNull(response.clickedButtonId())?.invoke()
            }
            .closedOrInvalidResultHandler { _, _ ->
                runOnServerThread {
                    onFormResponseReceived()
                    bedrockNavigator.goBack()
                }
            }
            .build()
    }

    private fun runOnServerThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    private fun renderContribution(position: Int, contribution: MemberContribution): String {
        val memberName = contribution.playerName
            ?: lang.bedrock("bedrock.bank.contributions.unknown_player")
        return when {
            contribution.netContribution > 0 -> lang.bedrock(
                "bedrock.bank.contributions.row.positive",
                "position" to position,
                "player" to memberName,
                "net" to contribution.netContribution,
                "deposits" to contribution.totalDeposits,
                "withdrawals" to contribution.totalWithdrawals
            )
            contribution.netContribution < 0 -> lang.bedrock(
                "bedrock.bank.contributions.row.negative",
                "position" to position,
                "player" to memberName,
                "net" to contribution.netContribution,
                "deposits" to contribution.totalDeposits,
                "withdrawals" to contribution.totalWithdrawals
            )
            else -> lang.bedrock(
                "bedrock.bank.contributions.row.neutral",
                "position" to position,
                "player" to memberName,
                "net" to contribution.netContribution,
                "deposits" to contribution.totalDeposits,
                "withdrawals" to contribution.totalWithdrawals
            )
        }
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private const val PAGE_SIZE = 10
    }
}
