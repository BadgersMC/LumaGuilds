package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Guild Bank Statistics and Analytics menu with financial insights and trends
 */
class GuildBankStatisticsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val lang: LangService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var overviewPane: StaticPane
    private lateinit var trendsPane: StaticPane
    private lateinit var memberPane: StaticPane

    // Analytics data
    private lateinit var bankStats: BankStats
    private var spendingTrends: Map<String, Double> = emptyMap()
    private var memberContributions: Map<String, Int> = emptyMap()
    private var recentActivity: List<Component> = emptyList()

    init {
        loadAnalyticsData()
        initializeGui()
    }

    override fun open() {
        updateAnalyticsDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Handle refresh requests
        if (data == "refresh") {
            loadAnalyticsData()
            updateAnalyticsDisplay()
            gui.update()
        }
    }

    /**
     * Load all analytics data
     */
    private fun loadAnalyticsData() {
        bankStats = bankService.getBankStats(guild.id)
        spendingTrends = calculateSpendingTrends()
        memberContributions = calculateMemberContributions()
        recentActivity = getRecentActivity()
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.bank.stats.title")))
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // Create main navigation pane
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create overview pane (top section)
        overviewPane = StaticPane(0, 1, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(overviewPane)

        // Create trends pane (middle section)
        trendsPane = StaticPane(0, 3, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(trendsPane)

        // Create member analytics pane (bottom section)
        memberPane = StaticPane(0, 4, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(memberPane)

        setupNavigation()
        setupOverview()
        setupTrends()
        setupMemberAnalytics()
    }

    /**
     * Setup navigation buttons
     */
    private fun setupNavigation() {
        // Back to bank button
        val backItem = createMenuItem(
            Material.ARROW,
            getLocalizedString("menu.bank.back_to_control_panel"),
            listOf(lang.gui("menu.bank.history.navigation.back_description"))
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Back to history button
        val historyItem = createMenuItem(
            Material.BOOK,
            lang.gui("menu.bank.stats.navigation.history_name"),
            listOf(lang.gui("menu.bank.stats.navigation.history_description"))
        )
        val historyGuiItem = GuiItem(historyItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(historyGuiItem, 1, 0)

        // Refresh button
        val refreshItem = createMenuItem(
            Material.CLOCK,
            lang.gui("menu.bank.stats.refresh.data"),
            listOf(lang.gui("menu.bank.stats.navigation.refresh_description"))
        )
        val refreshGuiItem = GuiItem(refreshItem) { event ->
            event.isCancelled = true
            loadAnalyticsData()
            updateAnalyticsDisplay()
            gui.update()
            player.sendMessage(lang.msg("menu.bank.stats.feedback.refreshed"))
        }
        mainPane.addItem(refreshGuiItem, 7, 0)

        // Security & Audit button
        val securityItem = createMenuItem(
            Material.SHIELD,
            lang.gui("menu.bank.stats.navigation.security_name"),
            listOf(
                lang.gui("menu.bank.stats.navigation.security_fraud"),
                lang.gui("menu.bank.stats.navigation.security_authorization"),
                lang.gui("menu.bank.stats.navigation.security_emergency")
            )
        )
        val securityGuiItem = GuiItem(securityItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankSecurityMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(securityGuiItem, 6, 0)

        // Automation & Rewards button
        val automationItem = createMenuItem(
            Material.REDSTONE,
            lang.gui("menu.bank.navigation.automation_name"),
            listOf(
                lang.gui("menu.bank.stats.navigation.automation_tasks"),
                lang.gui("menu.bank.stats.navigation.automation_rewards"),
                lang.gui("menu.bank.stats.navigation.automation_alerts")
            )
        )
        val automationGuiItem = GuiItem(automationItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankAutomationMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(automationGuiItem, 5, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString("menu.bank.close"),
            listOf(lang.gui("menu.common.close_description"))
        )
        val closeGuiItem = GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }
        mainPane.addItem(closeGuiItem, 8, 0)
    }

    /**
     * Setup overview statistics
     */
    private fun setupOverview() {
        // Current balance
        val balanceItem = createMenuItem(
            Material.EMERALD_BLOCK,
            lang.gui("menu.bank.balance.title"),
            listOf(
                lang.gui("menu.bank.stats.overview.balance", "amount" to bankStats.currentBalance),
                lang.gui("menu.bank.stats.overview.total_transactions", "count" to bankStats.totalTransactions),
                lang.gui("menu.bank.stats.overview.transaction_volume", "amount" to bankStats.transactionVolume)
            )
        )
        overviewPane.addItem(GuiItem(balanceItem), 0, 0)

        // Deposits vs Withdrawals
        val deposits = bankStats.totalDeposits
        val withdrawals = bankStats.totalWithdrawals
        val netFlow = deposits - withdrawals

        val netFlowItem = createMenuItem(
            if (netFlow >= 0) Material.LIME_WOOL else Material.RED_WOOL,
            lang.gui("menu.bank.stats.overview.net_flow_name"),
            listOf(
                if (netFlow >= 0) lang.gui("menu.bank.stats.overview.net_flow_positive", "amount" to netFlow) else lang.gui("menu.bank.stats.overview.net_flow_negative", "amount" to netFlow),
                lang.gui("menu.bank.stats.overview.deposits", "amount" to deposits),
                lang.gui("menu.bank.stats.overview.withdrawals", "amount" to withdrawals)
            )
        )
        overviewPane.addItem(GuiItem(netFlowItem), 1, 0)

        // Activity level
        val activityLevel = calculateActivityLevel()
        val activityItem = createMenuItem(
            Material.COMPASS,
            lang.gui("menu.bank.stats.overview.activity_name"),
            listOf(
                activityLevel,
                lang.gui("menu.bank.stats.overview.activity_basis"),
                lang.gui("menu.bank.stats.overview.activity_period")
            )
        )
        overviewPane.addItem(GuiItem(activityItem), 2, 0)

        // Average transaction
        val avgTransaction = if (bankStats.totalTransactions > 0) {
            bankStats.transactionVolume / bankStats.totalTransactions
        } else 0

        val avgItem = createMenuItem(
            Material.COMPARATOR,
            lang.gui("menu.bank.stats.overview.average_name"),
            listOf(
                lang.gui("menu.bank.stats.overview.average_amount", "amount" to avgTransaction),
                lang.gui("menu.bank.stats.overview.average_count", "count" to bankStats.totalTransactions)
            )
        )
        overviewPane.addItem(GuiItem(avgItem), 3, 0)

        // Top contributor
        val topContributor = memberContributions.maxByOrNull { it.value }
        val topItem = createMenuItem(
            Material.PLAYER_HEAD,
            lang.gui("menu.bank.stats.contributor.top"),
            if (topContributor != null) {
                listOf(
                    topContributor.key,
                    lang.gui("menu.bank.stats.member.contributed", "amount" to topContributor.value),
                    lang.gui("menu.bank.stats.overview.most_active")
                )
            } else {
                listOf(lang.gui("menu.bank.stats.overview.no_contributions"))
            }
        )
        overviewPane.addItem(GuiItem(topItem), 4, 0)

        // Recent activity summary — clicking navigates to full transaction history
        val recentItem = createMenuItem(
            Material.WRITABLE_BOOK,
            lang.gui("menu.bank.stats.activity.recent"),
            recentActivity.take(3).map { lang.gui("menu.bank.stats.overview.recent_activity_row", "activity" to it) } +
                listOf(lang.gui("menu.common.blank"), lang.gui("menu.bank.history.open_action"))
        )
        val recentGuiItem = GuiItem(recentItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        overviewPane.addItem(recentGuiItem, 5, 1)
    }

    /**
     * Setup spending trends analysis
     */
    private fun setupTrends() {
        // Weekly trend
        val weeklyTrend = spendingTrends["weekly"] ?: 0.0
        val weeklyItem = createMenuItem(
            if (weeklyTrend >= 0) Material.GREEN_WOOL else Material.RED_WOOL,
            lang.gui("menu.bank.stats.trend.weekly"),
            listOf(
                lang.gui("menu.bank.stats.trends.weekly_change", "change" to signedDecimal(weeklyTrend)),
                lang.gui("menu.bank.stats.trends.weekly_comparison"),
                lang.gui("menu.bank.stats.trends.volume_basis")
            )
        )
        trendsPane.addItem(GuiItem(weeklyItem), 0, 0)

        // Monthly comparison
        val monthlyTrend = spendingTrends["monthly"] ?: 0.0
        val monthlyItem = createMenuItem(
            if (monthlyTrend >= 0) Material.BLUE_WOOL else Material.ORANGE_WOOL,
            lang.gui("menu.bank.stats.trend.monthly"),
            listOf(
                lang.gui("menu.bank.stats.trends.monthly_growth", "growth" to signedDecimal(monthlyTrend)),
                lang.gui("menu.bank.stats.trends.monthly_analysis"),
                lang.gui("menu.bank.stats.trends.activity_indicator")
            )
        )
        trendsPane.addItem(GuiItem(monthlyItem), 1, 0)

        // Peak activity day
        val peakDay = spendingTrends["peak_day"] ?: 0.0
        val peakItem = createMenuItem(
            Material.CLOCK,
            lang.gui("menu.bank.stats.trend.peak"),
            listOf(
                lang.gui("menu.bank.stats.trends.peak_average", "count" to peakDay.toInt()),
                lang.gui("menu.bank.stats.trends.peak_period"),
                lang.gui("menu.bank.stats.trends.guild_indicator")
            )
        )
        trendsPane.addItem(GuiItem(peakItem), 2, 0)

        // Budget management
        val budgetItem = createMenuItem(
            Material.GOLD_INGOT,
            getLocalizedString("menu.bank.stats.budget.status"),
            listOf(
                lang.gui("menu.bank.stats.budget.manage"),
                lang.gui("menu.bank.stats.budget.periods"),
                lang.gui("menu.bank.stats.budget.tracking")
            )
        )
        val budgetGuiItem = GuiItem(budgetItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankBudgetMenu(menuNavigator, player, guild))
        }
        trendsPane.addItem(budgetGuiItem, 3, 0)
    }

    /**
     * Setup member contribution analytics
     */
    private fun setupMemberAnalytics() {
        // Top 5 contributors
        val topContributors = memberContributions.entries
            .sortedByDescending { it.value }
            .take(5)

        topContributors.forEachIndexed { index, (memberName, amount) ->
            if (index < 5) {
                val rank = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "#${index + 1}"
                }

                val contributorItem = createMenuItem(
                    Material.PLAYER_HEAD,
                    Component.text("$rank $memberName"),
                    listOf(
                        lang.gui("menu.bank.stats.member.contributed", "amount" to amount),
                        lang.gui("menu.bank.stats.member.active")
                    )
                )
                memberPane.addItem(GuiItem(contributorItem), index, 0)
            }
        }

        // Member activity summary
        val totalMembers = memberService.getMemberCount(guild.id)
        val activeContributors = memberContributions.count { it.value > 0 }
        val inactiveMembers = totalMembers - activeContributors

        // Calculate participation rate, avoiding division by zero
        val participationRate = if (totalMembers > 0) {
            String.format("%.1f", (activeContributors.toDouble() / totalMembers) * 100)
        } else {
            "0.0"
        }

        val summaryItem = createMenuItem(
            Material.BOOK,
            lang.gui("menu.bank.stats.member.summary"),
            listOf(
                lang.gui("menu.bank.stats.member.total", "count" to totalMembers),
                lang.gui("menu.bank.stats.member.active_count", "count" to activeContributors),
                lang.gui("menu.bank.stats.member.inactive_count", "count" to inactiveMembers),
                lang.gui("menu.bank.stats.member.participation", "rate" to participationRate)
            )
        )
        memberPane.addItem(GuiItem(summaryItem), 6, 0)

        // Tax collection info
        val taxItem = createMenuItem(
            Material.IRON_INGOT,
            lang.gui("menu.bank.stats.tax.collection"),
            listOf(
                lang.gui("menu.bank.stats.tax.unavailable"),
                lang.gui("menu.bank.stats.tax.maintenance"),
                lang.gui("menu.bank.stats.tax.projects")
            )
        )
        val taxGuiItem = GuiItem(taxItem) { event ->
            event.isCancelled = true
            player.sendMessage(lang.msg("menu.bank.stats.tax.feedback"))
        }
        memberPane.addItem(taxGuiItem, 7, 0)
    }

    /**
     * Update analytics display with latest data
     */
    private fun updateAnalyticsDisplay() {
        // The panes are already set up with current data
        // This method can be used for real-time updates if needed
    }

    /**
     * Calculate spending trends and analytics
     */
    private fun calculateSpendingTrends(): Map<String, Double> {
        val transactions = bankService.getTransactionHistory(guild.id, null)
        if (transactions.isEmpty()) return emptyMap()

        val now = Instant.now()
        val oneWeekAgo = now.minus(7, ChronoUnit.DAYS)
        val oneMonthAgo = now.minus(30, ChronoUnit.DAYS)

        // Calculate weekly trend
        val thisWeekTransactions = transactions.filter { it.timestamp.isAfter(oneWeekAgo) }
        val lastWeekTransactions = transactions.filter {
            it.timestamp.isAfter(oneMonthAgo) && it.timestamp.isBefore(oneWeekAgo)
        }

        val thisWeekVolume = thisWeekTransactions.sumOf { it.amount }
        val lastWeekVolume = lastWeekTransactions.sumOf { it.amount }
        val weeklyChange = if (lastWeekVolume > 0) {
            ((thisWeekVolume - lastWeekVolume).toDouble() / lastWeekVolume) * 100
        } else 0.0

        // Calculate monthly growth
        val monthlyVolume = transactions.filter { it.timestamp.isAfter(oneMonthAgo) }.sumOf { it.amount }
        val monthlyGrowth = if (bankStats.transactionVolume > monthlyVolume) {
            ((monthlyVolume.toDouble() / (bankStats.transactionVolume - monthlyVolume)) * 100) - 100
        } else 0.0

        // Calculate peak activity
        val dailyActivity = transactions.groupBy {
            LocalDateTime.ofInstant(it.timestamp, ZoneId.systemDefault()).toLocalDate()
        }.mapValues { it.value.size }

        val avgDailyActivity = if (dailyActivity.isNotEmpty()) {
            dailyActivity.values.average()
        } else 0.0

        return mapOf(
            "weekly" to weeklyChange,
            "monthly" to monthlyGrowth,
            "peak_day" to avgDailyActivity
        )
    }

    /**
     * Calculate member contributions
     */
    private fun calculateMemberContributions(): Map<String, Int> {
        val transactions = bankService.getTransactionHistory(guild.id, null)

        return transactions
            .filter { it.type == TransactionType.DEPOSIT }
            .groupBy {
                val player = Bukkit.getOfflinePlayer(it.actorId)
                player.name ?: lang.raw("menu.bank.history.value.unknown")
            }
            .mapValues { (_, transactions) ->
                transactions.sumOf { it.amount }
            }
    }

    /**
     * Get recent activity summary
     */
    private fun getRecentActivity(): List<Component> {
        val transactions = bankService.getTransactionHistory(guild.id, 10)

        return transactions.map { transaction ->
            val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: lang.raw("menu.bank.history.value.unknown")
            val action = when (transaction.type) {
                TransactionType.DEPOSIT -> lang.gui("menu.bank.stats.activity.deposited", "amount" to transaction.amount)
                TransactionType.WITHDRAWAL -> lang.gui("menu.bank.stats.activity.withdrew", "amount" to transaction.amount)
                TransactionType.FEE -> lang.gui("menu.bank.stats.activity.paid_fee", "amount" to transaction.amount)
                TransactionType.DEDUCTION -> lang.gui("menu.bank.stats.activity.deducted", "amount" to transaction.amount)
            }
            lang.gui("menu.bank.stats.activity.row", "player" to actorName, "action" to action)
        }
    }

    /**
     * Calculate activity level based on transaction frequency
     */
    private fun calculateActivityLevel(): Component {
        val transactions = bankService.getTransactionHistory(guild.id, null)
        val now = Instant.now()
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)

        val recentTransactions = transactions.filter { it.timestamp.isAfter(thirtyDaysAgo) }
        val transactionsPerDay = recentTransactions.size / 30.0

        return when {
            transactionsPerDay >= 5 -> lang.gui("menu.bank.stats.activity.very_high")
            transactionsPerDay >= 2 -> lang.gui("menu.bank.stats.activity.high")
            transactionsPerDay >= 0.5 -> lang.gui("menu.bank.stats.activity.moderate")
            transactionsPerDay > 0 -> lang.gui("menu.bank.stats.activity.low")
            else -> lang.gui("menu.bank.stats.activity.none")
        }
    }

    private fun signedDecimal(value: Double): String =
        (if (value >= 0) "+" else "") + String.format("%.1f", value)

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: Component, lore: List<*>): ItemStack {
        val item = ItemStack.of(material)
        val meta = item.itemMeta

        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        if (lore.isNotEmpty()) {
            val loreComponents = lore.map { line ->
                when (line) {
                    is Component -> line
                    else -> Component.text(line.toString()).color(NamedTextColor.GRAY)
                }.decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(loreComponents)
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Get localized string with optional parameters
     */
    private fun getLocalizedString(key: String): Component {
        return lang.gui(key)
    }
}

