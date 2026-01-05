package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.domain.values.LocalizationKeys
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
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
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
    private var recentActivity: List<String> = emptyList()

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
        gui = ChestGui(6, getLocalizedString(LocalizationKeys.MENU_BANK_STATS_TITLE, guild.name))
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
            getLocalizedString(LocalizationKeys.MENU_BANK_BACK_TO_CONTROL_PANEL),
            listOf("Return to guild bank")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Back to history button
        val historyItem = createMenuItem(
            Material.BOOK,
            "Transaction History",
            listOf("View detailed transaction history")
        )
        val historyGuiItem = GuiItem(historyItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(historyGuiItem, 1, 0)

        // Refresh button
        val refreshItem = createMenuItem(
            Material.CLOCK,
            "Refresh Data",
            listOf("Update statistics with latest data")
        )
        val refreshGuiItem = GuiItem(refreshItem) { event ->
            event.isCancelled = true
            loadAnalyticsData()
            updateAnalyticsDisplay()
            gui.update()
            player.sendMessage("§aStatistics refreshed!")
        }
        mainPane.addItem(refreshGuiItem, 7, 0)

        // Security & Audit button
        val securityItem = createMenuItem(
            Material.SHIELD,
            "Security & Audit",
            listOf(
                "Fraud detection and security monitoring",
                "Dual authorization settings",
                "Emergency transaction controls"
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
            "Automation & Rewards",
            listOf(
                "Scheduled tasks and automated processes",
                "Auto-reward distribution system",
                "Budget alerts and recurring payments"
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
            getLocalizedString(LocalizationKeys.MENU_BANK_CLOSE),
            listOf("Close menu")
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
            "Current Balance",
            listOf(
                "${bankStats.currentBalance} coins",
                "Total Transactions: ${bankStats.totalTransactions}",
                "Transaction Volume: ${bankStats.transactionVolume} coins"
            )
        )
        overviewPane.addItem(GuiItem(balanceItem), 0, 0)

        // Deposits vs Withdrawals
        val deposits = bankStats.totalDeposits
        val withdrawals = bankStats.totalWithdrawals
        val netFlow = deposits - withdrawals

        val netFlowItem = createMenuItem(
            if (netFlow >= 0) Material.LIME_WOOL else Material.RED_WOOL,
            "Net Cash Flow",
            listOf(
                "${if (netFlow >= 0) "+" else ""}${netFlow} coins",
                "Deposits: +${deposits} coins",
                "Withdrawals: -${withdrawals} coins"
            )
        )
        overviewPane.addItem(GuiItem(netFlowItem), 1, 0)

        // Activity level
        val activityLevel = calculateActivityLevel()
        val activityItem = createMenuItem(
            Material.COMPASS,
            "Activity Level",
            listOf(
                activityLevel,
                "Based on transaction frequency",
                "Last 30 days"
            )
        )
        overviewPane.addItem(GuiItem(activityItem), 2, 0)

        // Average transaction
        val avgTransaction = if (bankStats.totalTransactions > 0) {
            bankStats.transactionVolume / bankStats.totalTransactions
        } else 0

        val avgItem = createMenuItem(
            Material.COMPARATOR,
            "Average Transaction",
            listOf(
                "${avgTransaction} coins",
                "Across all ${bankStats.totalTransactions} transactions"
            )
        )
        overviewPane.addItem(GuiItem(avgItem), 3, 0)

        // Top contributor
        val topContributor = memberContributions.maxByOrNull { it.value }
        val topItem = createMenuItem(
            Material.PLAYER_HEAD,
            "Top Contributor",
            if (topContributor != null) {
                listOf(
                    topContributor.key,
                    "${topContributor.value} coins contributed",
                    "Most active member"
                )
            } else {
                listOf("No contributions yet")
            }
        )
        overviewPane.addItem(GuiItem(topItem), 4, 0)

        // Recent activity summary
        val recentItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Recent Activity",
            recentActivity.take(3).map { "• $it" }
        )
        overviewPane.addItem(GuiItem(recentItem), 5, 1)
    }

    /**
     * Setup spending trends analysis
     */
    private fun setupTrends() {
        // Weekly trend
        val weeklyTrend = spendingTrends["weekly"] ?: 0.0
        val weeklyItem = createMenuItem(
            if (weeklyTrend >= 0) Material.GREEN_WOOL else Material.RED_WOOL,
            "Weekly Trend",
            listOf(
                "${if (weeklyTrend >= 0) "+" else ""}${String.format("%.1f", weeklyTrend)}% change",
                "Compared to previous week",
                "Based on transaction volume"
            )
        )
        trendsPane.addItem(GuiItem(weeklyItem), 0, 0)

        // Monthly comparison
        val monthlyTrend = spendingTrends["monthly"] ?: 0.0
        val monthlyItem = createMenuItem(
            if (monthlyTrend >= 0) Material.BLUE_WOOL else Material.ORANGE_WOOL,
            "Monthly Growth",
            listOf(
                "${if (monthlyTrend >= 0) "+" else ""}${String.format("%.1f", monthlyTrend)}% growth",
                "30-day transaction analysis",
                "Activity trend indicator"
            )
        )
        trendsPane.addItem(GuiItem(monthlyItem), 1, 0)

        // Peak activity day
        val peakDay = spendingTrends["peak_day"] ?: 0.0
        val peakItem = createMenuItem(
            Material.CLOCK,
            "Peak Activity",
            listOf(
                "${peakDay.toInt()} transactions/day average",
                "Most active period",
                "Guild activity indicator"
            )
        )
        trendsPane.addItem(GuiItem(peakItem), 2, 0)

        // Budget management
        val budgetItem = createMenuItem(
            Material.GOLD_INGOT,
            getLocalizedString(LocalizationKeys.MENU_BANK_STATS_BUDGET_STATUS),
            listOf(
                "Manage spending limits and alerts",
                "Set monthly, weekly, and daily budgets",
                "Track budget usage and get alerts"
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
                    "$rank $memberName",
                    listOf(
                        "${amount} coins contributed",
                        "Active guild member"
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
            "Member Summary",
            listOf(
                "Total Members: $totalMembers",
                "Active Contributors: $activeContributors",
                "Inactive: $inactiveMembers",
                "$participationRate% participation rate"
            )
        )
        memberPane.addItem(GuiItem(summaryItem), 6, 0)

        // Tax collection info (placeholder)
        val taxItem = createMenuItem(
            Material.IRON_INGOT,
            "Tax Collection",
            listOf(
                "Automatic tax system coming soon",
                "Collect maintenance fees",
                "Fund guild projects"
            )
        )
        memberPane.addItem(GuiItem(taxItem), 7, 0)
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
                player.name ?: "Unknown"
            }
            .mapValues { (_, transactions) ->
                transactions.sumOf { it.amount }
            }
    }

    /**
     * Get recent activity summary
     */
    private fun getRecentActivity(): List<String> {
        val transactions = bankService.getTransactionHistory(guild.id, 10)

        return transactions.map { transaction ->
            val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: "Unknown"
            val action = when (transaction.type) {
                TransactionType.DEPOSIT -> "deposited ${transaction.amount}"
                TransactionType.WITHDRAWAL -> "withdrew ${transaction.amount}"
                TransactionType.FEE -> "paid ${transaction.amount} fee"
                TransactionType.DEDUCTION -> "deducted ${transaction.amount}"
            }
            "$actorName $action"
        }
    }

    /**
     * Calculate activity level based on transaction frequency
     */
    private fun calculateActivityLevel(): String {
        val transactions = bankService.getTransactionHistory(guild.id, null)
        val now = Instant.now()
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)

        val recentTransactions = transactions.filter { it.timestamp.isAfter(thirtyDaysAgo) }
        val transactionsPerDay = recentTransactions.size / 30.0

        return when {
            transactionsPerDay >= 5 -> "Very High Activity"
            transactionsPerDay >= 2 -> "High Activity"
            transactionsPerDay >= 0.5 -> "Moderate Activity"
            transactionsPerDay > 0 -> "Low Activity"
            else -> "No Recent Activity"
        }
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text(name)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))

        if (lore.isNotEmpty()) {
            val loreComponents = lore.map { line ->
                Component.text(line)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(loreComponents)
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Get localized string with optional parameters
     */
    private fun getLocalizedString(key: String, vararg params: Any?): String {
        return localizationProvider.get(player.uniqueId, key, *params)
    }
}

