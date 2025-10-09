package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Guild Bank Budget Management menu with spending limits and alerts
 */
class GuildBankBudgetMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val messageService: MessageService
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var budgetPane: StaticPane
    private lateinit var alertsPane: StaticPane

    // Budget data
    private var monthlyBudget: Int = 0
    private var weeklyBudget: Int = 0
    private var dailyBudget: Int = 0
    private var budgetAlerts: MutableList<String> = mutableListOf()

    init {
        loadBudgetSettings()
        calculateAlerts()
        initializeGui()
    }

    override fun open() {
        // Check if player has permission to access budget management
        if (!hasBudgetManagementPermission()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to access budget management!")
            return
        }

        updateBudgetDisplay()
        gui.show(player)
    }

    /**
     * Load budget settings from database
     */
    private fun loadBudgetSettings() {
        val monthly = bankService.getBudgetByCategory(guild.id, net.lumalyte.lg.domain.entities.BudgetCategory.GENERAL_EXPENSES)
        if (monthly != null) {
            monthlyBudget = monthly.allocatedAmount
        } else {
            monthlyBudget = 10000
        }

        // For simplicity, using the same budget for all categories initially
        // In a full implementation, you'd have separate budgets for different categories
        weeklyBudget = monthlyBudget / 4
        dailyBudget = weeklyBudget / 7
    }

    /**
     * Calculate budget alerts and warnings
     */
    private fun calculateAlerts() {
        budgetAlerts.clear()

        val transactions = bankService.getTransactionHistory(guild.id, null)
        val now = Instant.now()

        // Calculate spending for different periods
        val monthStart = now.truncatedTo(ChronoUnit.DAYS).minus(30, ChronoUnit.DAYS)
        val weekStart = now.truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS)
        val dayStart = now.truncatedTo(ChronoUnit.DAYS)

        val monthlySpending = transactions
            .filter { it.timestamp.isAfter(monthStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        val weeklySpending = transactions
            .filter { it.timestamp.isAfter(weekStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        val dailySpending = transactions
            .filter { it.timestamp.isAfter(dayStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        // Check budget thresholds
        val monthlyPercent = if (monthlyBudget > 0) (monthlySpending.toDouble() / monthlyBudget) * 100 else 0.0
        val weeklyPercent = if (weeklyBudget > 0) (weeklySpending.toDouble() / weeklyBudget) * 100 else 0.0
        val dailyPercent = if (dailyBudget > 0) (dailySpending.toDouble() / dailyBudget) * 100 else 0.0

        if (monthlyPercent >= 90) budgetAlerts.add("Monthly budget at ${String.format("%.1f", monthlyPercent)}%")
        if (weeklyPercent >= 80) budgetAlerts.add("Weekly budget at ${String.format("%.1f", weeklyPercent)}%")
        if (dailyPercent >= 75) budgetAlerts.add("Daily budget at ${String.format("%.1f", dailyPercent)}%")

        if (monthlyPercent >= 100) budgetAlerts.add("⚠️ Monthly budget exceeded!")
        if (weeklyPercent >= 100) budgetAlerts.add("⚠️ Weekly budget exceeded!")
        if (dailyPercent >= 100) budgetAlerts.add("⚠️ Daily budget exceeded!")
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Budget Management - ${guild.name}"))
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Create main navigation pane
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create budget settings pane
        budgetPane = StaticPane(0, 1, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(budgetPane)

        // Create alerts pane
        alertsPane = StaticPane(0, 3, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(alertsPane)

        setupNavigation()
        setupBudgetSettings()
        setupAlerts()
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

        // Back to statistics button
        val statsItem = createMenuItem(
            Material.BOOK,
            getLocalizedString(LocalizationKeys.MENU_BANK_STATS_TITLE),
            listOf("Return to statistics")
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 1, 0)

        // Save button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Save Budget Settings",
            listOf("Save current budget limits")
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            saveBudgetSettings()
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Budget settings saved!")
        }
        mainPane.addItem(saveGuiItem, 7, 0)

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
     * Setup budget settings controls
     */
    private fun setupBudgetSettings() {
        // Monthly budget
        val monthlyItem = createMenuItem(
            Material.CHEST,
            "Monthly Budget",
            listOf(
                "Current: ${monthlyBudget} coins",
                "Click to set monthly spending limit",
                "Tracks spending over 30 days",
                if (hasBudgetManagementPermission()) "" else "<red>⚠️ Requires MANAGE_BUDGETS permission"
            )
        )
        val monthlyGuiItem = GuiItem(monthlyItem) { event ->
            event.isCancelled = true
            if (hasBudgetManagementPermission()) {
                // TODO: Open amount input for monthly budget
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Monthly budget setting coming soon!")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage budgets!")
            }
        }
        budgetPane.addItem(monthlyGuiItem, 0, 0)

        // Weekly budget
        val weeklyItem = createMenuItem(
            Material.TRAPPED_CHEST,
            "Weekly Budget",
            listOf(
                "Current: ${weeklyBudget} coins",
                "Click to set weekly spending limit",
                "Tracks spending over 7 days",
                if (hasBudgetManagementPermission()) "" else "<red>⚠️ Requires MANAGE_BUDGETS permission"
            )
        )
        val weeklyGuiItem = GuiItem(weeklyItem) { event ->
            event.isCancelled = true
            if (hasBudgetManagementPermission()) {
                // TODO: Open amount input for weekly budget
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Weekly budget setting coming soon!")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage budgets!")
            }
        }
        budgetPane.addItem(weeklyGuiItem, 1, 0)

        // Daily budget
        val dailyItem = createMenuItem(
            Material.ENDER_CHEST,
            "Daily Budget",
            listOf(
                "Current: ${dailyBudget} coins",
                "Click to set daily spending limit",
                "Tracks spending over 24 hours",
                if (hasBudgetManagementPermission()) "" else "<red>⚠️ Requires MANAGE_BUDGETS permission"
            )
        )
        val dailyGuiItem = GuiItem(dailyItem) { event ->
            event.isCancelled = true
            if (hasBudgetManagementPermission()) {
                // TODO: Open amount input for daily budget
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Daily budget setting coming soon!")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage budgets!")
            }
        }
        budgetPane.addItem(dailyGuiItem, 2, 0)

        // Budget status display
        updateBudgetStatus()
    }

    /**
     * Setup alerts and notifications
     */
    private fun setupAlerts() {
        if (budgetAlerts.isEmpty()) {
            val noAlertsItem = createMenuItem(
                Material.GREEN_WOOL,
                "No Budget Alerts",
                listOf(
                    "All budgets are within limits",
                    "Keep up the good financial management!"
                )
            )
            alertsPane.addItem(GuiItem(noAlertsItem), 0, 0)
        } else {
            // Display alerts
            budgetAlerts.take(5).forEachIndexed { index, alert ->
                val alertItem = createMenuItem(
                    if (alert.contains("⚠️")) Material.RED_WOOL else Material.YELLOW_WOOL,
                    "Budget Alert",
                    listOf(alert, "Monitor spending closely")
                )
                alertsPane.addItem(GuiItem(alertItem), index % 9, index / 9)
            }
        }
    }

    /**
     * Update budget status display
     */
    private fun updateBudgetStatus() {
        val transactions = bankService.getTransactionHistory(guild.id, null)
        val now = Instant.now()

        // Calculate current spending
        val monthStart = now.minus(30, ChronoUnit.DAYS)
        val weekStart = now.minus(7, ChronoUnit.DAYS)
        val dayStart = now.minus(1, ChronoUnit.DAYS)

        val monthlySpent = transactions
            .filter { it.timestamp.isAfter(monthStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        val weeklySpent = transactions
            .filter { it.timestamp.isAfter(weekStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        val dailySpent = transactions
            .filter { it.timestamp.isAfter(dayStart) && it.type.name == "WITHDRAWAL" }
            .sumOf { it.amount }

        // Status items
        val monthlyStatusItem = createMenuItem(
            getBudgetStatusMaterial(monthlySpent, monthlyBudget),
            "Monthly Status",
            listOf(
                "Spent: ${monthlySpent}/${monthlyBudget} coins",
                "${String.format("%.1f", (monthlySpent.toDouble() / monthlyBudget) * 100)}% used",
                "${monthlyBudget - monthlySpent} coins remaining"
            )
        )
        budgetPane.addItem(GuiItem(monthlyStatusItem), 4, 0)

        val weeklyStatusItem = createMenuItem(
            getBudgetStatusMaterial(weeklySpent, weeklyBudget),
            "Weekly Status",
            listOf(
                "Spent: ${weeklySpent}/${weeklyBudget} coins",
                "${String.format("%.1f", (weeklySpent.toDouble() / weeklyBudget) * 100)}% used",
                "${weeklyBudget - weeklySpent} coins remaining"
            )
        )
        budgetPane.addItem(GuiItem(weeklyStatusItem), 5, 0)

        val dailyStatusItem = createMenuItem(
            getBudgetStatusMaterial(dailySpent, dailyBudget),
            "Daily Status",
            listOf(
                "Spent: ${dailySpent}/${dailyBudget} coins",
                "${String.format("%.1f", (dailySpent.toDouble() / dailyBudget) * 100)}% used",
                "${dailyBudget - dailySpent} coins remaining"
            )
        )
        budgetPane.addItem(GuiItem(dailyStatusItem), 6, 0)
    }

    /**
     * Get material based on budget status
     */
    private fun getBudgetStatusMaterial(spent: Int, budget: Int): Material {
        if (budget == 0) return Material.GRAY_WOOL

        val percentage = (spent.toDouble() / budget) * 100
        return when {
            percentage >= 100 -> Material.RED_WOOL
            percentage >= 75 -> Material.ORANGE_WOOL
            percentage >= 50 -> Material.YELLOW_WOOL
            else -> Material.GREEN_WOOL
        }
    }

    /**
     * Update budget display
     */
    private fun updateBudgetDisplay() {
        // Update is handled by individual setup methods
        calculateAlerts()
        setupAlerts()
        updateBudgetStatus()
    }

    /**
     * Save budget settings to database
     */
    private fun saveBudgetSettings() {
        try {
            val now = java.time.Instant.now()
            val monthEnd = now.plus(30, java.time.temporal.ChronoUnit.DAYS)

            val saved = bankService.setBudget(
                guild.id,
                net.lumalyte.lg.domain.entities.BudgetCategory.GENERAL_EXPENSES,
                monthlyBudget,
                now,
                monthEnd
            )

            if (saved != null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Budget settings saved successfully!")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to save budget settings!")
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error saving budget settings: ${e.message}")
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

    /**
     * Check if player has permission to access budget management
     */
    private fun hasBudgetManagementPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BUDGETS)
    }
}
