package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
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

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val statsIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.statistics.title")} - ${guild.name}")
            .apply { statsIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.statistics.description"))
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.statistics.overview.header")))
            .label(createOverviewSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.statistics.activity.header")))
            .label(createActivitySection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.statistics.economy.header")))
            .label(createEconomySection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.statistics.territory.header")))
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
        return "§e§l$title"
    }

    private fun createOverviewSection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val totalMembers = members.size
        val onlineMembers = members.count { member ->
            try {
                player.server.getPlayer(member.playerId)?.isOnline == true
            } catch (e: Exception) {
                false
            }
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val createdDate = formatter.format(guild.createdAt)

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.members.total")}: $totalMembers
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.members.online")}: $onlineMembers
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.created.date")}: $createdDate
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.level")}: 1
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.experience")}: 0/800
        """.trimMargin()
    }

    private fun createActivitySection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val lastActivity = members.maxOfOrNull { it.joinedAt } ?: guild.createdAt
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val lastSeen = formatter.format(lastActivity)

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.activity.recent")}: Active
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.activity.last.seen")}: $lastSeen
        """.trimMargin()
    }

    private fun createEconomySection(): String {
        val balance = bankService.getBalance(guild.id)
        // For now, we'll show placeholder values for transactions
        val totalTransactions = 0
        val averageTransaction = 0.0

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.balance")}: $balance coins
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.transactions")}: $totalTransactions
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.average")}: $averageTransaction coins
        """.trimMargin()
    }

    private fun createTerritorySection(): String {
        // Placeholder values for territory statistics
        val totalClaims = 0
        val controlledArea = 0
        val powerLevel = 1

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.claims")}: $totalClaims
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.area")}: ${controlledArea} blocks
            |${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.power")}: $powerLevel
        """.trimMargin()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only statistics menu, just close it
        onFormResponseReceived()
    }
}
