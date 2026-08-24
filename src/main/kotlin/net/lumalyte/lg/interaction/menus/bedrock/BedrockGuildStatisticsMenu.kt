package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild statistics menu using Cumulus CustomForm
 * Displays comprehensive guild statistics and performance metrics
 */
class BedrockGuildStatisticsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val statsIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.statistics.title", "guild" to guild.name))
            .apply { statsIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.statistics.description"))
            .label(createSectionHeader(lang.bedrock("bedrock.statistics.header.overview")))
            .label(createOverviewSection())
            .label(createSectionHeader(lang.bedrock("bedrock.statistics.header.activity")))
            .label(createActivitySection())
            .label(createSectionHeader(lang.bedrock("bedrock.statistics.header.economy")))
            .label(createEconomySection())
            .label(createSectionHeader(lang.bedrock("bedrock.statistics.header.territory")))
            .label(createTerritorySection())
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
        return lang.bedrock("bedrock.statistics.header.format", "title" to title)
    }

    private fun createOverviewSection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val totalMembers = members.size
        val onlineMembers = members.count { member ->
            try {
                player.server.getPlayer(member.playerId)?.isOnline == true
            } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                false
            }
        }

        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.statistics.date_format.overview"))
        val createdDate = formatter.format(guild.createdAt.atZone(java.time.ZoneId.systemDefault()))

        return lang.bedrock(
            "bedrock.statistics.overview",
            "total_members" to totalMembers,
            "online_members" to onlineMembers,
            "created" to createdDate,
            "level" to guild.level,
            "experience" to "0/800"
        )
    }

    private fun createActivitySection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val lastActivity = members.maxOfOrNull { it.joinedAt } ?: guild.createdAt
        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.statistics.date_format.activity"))
        val lastSeen = formatter.format(lastActivity)

        return lang.bedrock("bedrock.statistics.activity", "status" to lang.bedrock("bedrock.statistics.value.active"), "last_seen" to lastSeen)
    }

    private fun createEconomySection(): String {
        val stats = bankService.getBankStats(guild.id)
        val averageTransaction = if (stats.totalTransactions > 0) {
            stats.transactionVolume.toDouble() / stats.totalTransactions
        } else {
            0.0
        }
        return lang.bedrock(
            "bedrock.statistics.economy",
            "balance" to stats.currentBalance,
            "transactions" to stats.totalTransactions,
            "average" to String.format("%.1f", averageTransaction)
        )
    }

    private fun createTerritorySection(): String {
        // Placeholder values for territory statistics
        val totalClaims = 0
        val controlledArea = 0
        val powerLevel = 1

        return lang.bedrock(
            "bedrock.statistics.territory",
            "claims" to totalClaims,
            "area" to controlledArea,
            "power" to powerLevel
        )
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only statistics menu, just close it
        onFormResponseReceived()
    }
}
