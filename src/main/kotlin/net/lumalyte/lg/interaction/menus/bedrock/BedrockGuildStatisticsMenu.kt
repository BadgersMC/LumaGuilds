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
        return "§6§l━━━ $title §r§6━━━"
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

        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val createdDate = formatter.format(guild.createdAt.atZone(java.time.ZoneId.systemDefault()))

        return """
            |§b${bedrockLocalization.getBedrockString(player, "guild.statistics.members.total")}§7: §f$totalMembers
            |§a${bedrockLocalization.getBedrockString(player, "guild.statistics.members.online")}§7: §f$onlineMembers
            |§e${bedrockLocalization.getBedrockString(player, "guild.statistics.created.date")}§7: §f$createdDate
            |§d${bedrockLocalization.getBedrockString(player, "guild.statistics.level")}§7: §f1
            |§3${bedrockLocalization.getBedrockString(player, "guild.statistics.experience")}§7: §f0/800
        """.trimMargin()
    }

    private fun createActivitySection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val lastActivity = members.maxOfOrNull { it.joinedAt } ?: guild.createdAt
        val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        val lastSeen = formatter.format(lastActivity)

        return """
            |§a${bedrockLocalization.getBedrockString(player, "guild.statistics.activity.recent")}§7: §fActive
            |§e${bedrockLocalization.getBedrockString(player, "guild.statistics.activity.last.seen")}§7: §f$lastSeen
        """.trimMargin()
    }

    private fun createEconomySection(): String {
        val balance = bankService.getBalance(guild.id)
        // For now, we'll show placeholder values for transactions
        val totalTransactions = 0
        val averageTransaction = 0.0

        return """
            |§6${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.balance")}§7: §e$balance §7coins
            |§b${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.transactions")}§7: §f$totalTransactions
            |§3${bedrockLocalization.getBedrockString(player, "guild.statistics.economy.average")}§7: §f$averageTransaction §7coins
        """.trimMargin()
    }

    private fun createTerritorySection(): String {
        // Placeholder values for territory statistics
        val totalClaims = 0
        val controlledArea = 0
        val powerLevel = 1

        return """
            |§d${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.claims")}§7: §f$totalClaims
            |§5${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.area")}§7: §f${controlledArea} §7blocks
            |§c${bedrockLocalization.getBedrockString(player, "guild.statistics.territory.power")}§7: §f$powerLevel
        """.trimMargin()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only statistics menu, just close it
        onFormResponseReceived()
    }
}
