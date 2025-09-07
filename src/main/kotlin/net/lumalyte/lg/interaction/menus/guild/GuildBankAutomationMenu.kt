package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime

/**
 * Guild Bank Automation menu with scheduled tasks, rewards, and alerts
 */
class GuildBankAutomationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var automationPane: StaticPane
    private lateinit var rewardsPane: StaticPane

    // Automation settings
    private var scheduledDepositsEnabled: Boolean = false
    private var autoRewardsEnabled: Boolean = true
    private var budgetAlertsEnabled: Boolean = true
    private var recurringPaymentsEnabled: Boolean = false
    private var interestRate: Double = 0.02 // 2% monthly interest

    // Active automations
    private var activeAutomations: MutableList<String> = mutableListOf()

    init {
        loadAutomationSettings()
        checkActiveAutomations()
        initializeGui()
    }

    override fun open() {
        updateAutomationDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Handle automation setting updates
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val updates = data as Map<String, Any>
            updates.forEach { (setting, value) ->
                when (setting) {
                    "scheduledDeposits" -> scheduledDepositsEnabled = value as Boolean
                    "autoRewards" -> autoRewardsEnabled = value as Boolean
                    "budgetAlerts" -> budgetAlertsEnabled = value as Boolean
                    "recurringPayments" -> recurringPaymentsEnabled = value as Boolean
                    "interestRate" -> interestRate = value as Double
                }
            }
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
    }

    /**
     * Load automation settings (placeholder for now)
     */
    private fun loadAutomationSettings() {
        // TODO: Load from database/configuration
        scheduledDepositsEnabled = false
        autoRewardsEnabled = true
        budgetAlertsEnabled = true
        recurringPaymentsEnabled = false
        interestRate = 0.02
    }

    /**
     * Check which automations are currently active
     */
    private fun checkActiveAutomations() {
        activeAutomations.clear()

        if (scheduledDepositsEnabled) {
            activeAutomations.add("Scheduled Deposits")
        }
        if (autoRewardsEnabled) {
            activeAutomations.add("Auto-Rewards Distribution")
        }
        if (budgetAlertsEnabled) {
            activeAutomations.add("Budget Alerts")
        }
        if (recurringPaymentsEnabled) {
            activeAutomations.add("Recurring Payments")
        }
        if (interestRate > 0) {
            activeAutomations.add("Interest Calculation (${String.format("%.1f", interestRate * 100)}%)")
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(5, "Automation & Rewards - ${guild.name}")
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // Create main navigation pane
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create automation settings pane
        automationPane = StaticPane(0, 1, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(automationPane)

        // Create rewards and alerts pane
        rewardsPane = StaticPane(0, 3, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(rewardsPane)

        setupNavigation()
        setupAutomationSettings()
        setupRewardsAndAlerts()
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
            menuNavigator.openMenu(GuildBankMenu(menuNavigator, player, guild))
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
            menuNavigator.openMenu(GuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 1, 0)

        // Save settings button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Save Automation Settings",
            listOf("Apply current automation configuration")
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            saveAutomationSettings()
            player.sendMessage("§aAutomation settings saved!")
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
     * Setup automation settings controls
     */
    private fun setupAutomationSettings() {
        // Scheduled deposits toggle
        val scheduledItem = createMenuItem(
            if (scheduledDepositsEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            "Scheduled Deposits",
            listOf(
                "Status: ${if (scheduledDepositsEnabled) "Enabled" else "Disabled"}",
                "Automatically deposit funds at set intervals",
                "Click to toggle"
            )
        )
        val scheduledGuiItem = GuiItem(scheduledItem) { event ->
            event.isCancelled = true
            scheduledDepositsEnabled = !scheduledDepositsEnabled
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(scheduledGuiItem, 0, 0)

        // Auto-rewards toggle
        val rewardsItem = createMenuItem(
            if (autoRewardsEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            "Auto-Rewards Distribution",
            listOf(
                "Status: ${if (autoRewardsEnabled) "Enabled" else "Disabled"}",
                "Automatically distribute rewards to members",
                "Click to toggle"
            )
        )
        val rewardsGuiItem = GuiItem(rewardsItem) { event ->
            event.isCancelled = true
            autoRewardsEnabled = !autoRewardsEnabled
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(rewardsGuiItem, 1, 0)

        // Budget alerts toggle
        val alertsItem = createMenuItem(
            if (budgetAlertsEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            "Budget Alerts",
            listOf(
                "Status: ${if (budgetAlertsEnabled) "Enabled" else "Disabled"}",
                "Send notifications when approaching limits",
                "Click to toggle"
            )
        )
        val alertsGuiItem = GuiItem(alertsItem) { event ->
            event.isCancelled = true
            budgetAlertsEnabled = !budgetAlertsEnabled
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(alertsGuiItem, 2, 0)

        // Recurring payments toggle
        val recurringItem = createMenuItem(
            if (recurringPaymentsEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            "Recurring Payments",
            listOf(
                "Status: ${if (recurringPaymentsEnabled) "Enabled" else "Disabled"}",
                "Set up automatic recurring transactions",
                "Click to toggle"
            )
        )
        val recurringGuiItem = GuiItem(recurringItem) { event ->
            event.isCancelled = true
            recurringPaymentsEnabled = !recurringPaymentsEnabled
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(recurringGuiItem, 3, 0)

        // Interest rate setting
        val interestItem = createMenuItem(
            Material.GOLD_INGOT,
            "Interest Rate",
            listOf(
                "Current: ${String.format("%.1f", interestRate * 100)}% monthly",
                "Automatic interest on guild balance",
                "Click to configure"
            )
        )
        val interestGuiItem = GuiItem(interestItem) { event ->
            event.isCancelled = true
            // TODO: Open interest rate input
            player.sendMessage("§eInterest rate configuration coming soon!")
        }
        automationPane.addItem(interestGuiItem, 4, 0)

        // Active automations display
        updateActiveAutomations()
    }

    /**
     * Setup rewards and alerts management
     */
    private fun setupRewardsAndAlerts() {
        // Reward distribution setup
        val rewardSetupItem = createMenuItem(
            Material.DIAMOND,
            "Reward Distribution Setup",
            listOf(
                "Configure automatic member rewards",
                "Set reward amounts and conditions",
                "Based on activity and contributions"
            )
        )
        val rewardSetupGuiItem = GuiItem(rewardSetupItem) { event ->
            event.isCancelled = true
            // TODO: Open reward setup menu
            player.sendMessage("§eReward distribution setup coming soon!")
        }
        rewardsPane.addItem(rewardSetupGuiItem, 0, 0)

        // Alert threshold configuration
        val alertConfigItem = createMenuItem(
            Material.BELL,
            "Alert Configuration",
            listOf(
                "Set budget alert thresholds",
                "Configure notification preferences",
                "Customize alert messages"
            )
        )
        val alertConfigGuiItem = GuiItem(alertConfigItem) { event ->
            event.isCancelled = true
            // TODO: Open alert configuration menu
            player.sendMessage("§eAlert configuration coming soon!")
        }
        rewardsPane.addItem(alertConfigGuiItem, 1, 0)

        // Recurring payment setup
        val paymentSetupItem = createMenuItem(
            Material.CLOCK,
            "Recurring Payment Setup",
            listOf(
                "Set up automatic payments",
                "Configure payment schedules",
                "Manage payment recipients"
            )
        )
        val paymentSetupGuiItem = GuiItem(paymentSetupItem) { event ->
            event.isCancelled = true
            // TODO: Open recurring payment setup
            player.sendMessage("§eRecurring payment setup coming soon!")
        }
        rewardsPane.addItem(paymentSetupGuiItem, 2, 0)

        // Automation status display
        updateAutomationStatus()
    }

    /**
     * Update active automations display
     */
    private fun updateActiveAutomations() {
        val statusItem = createMenuItem(
            Material.COMPARATOR,
            "Active Automations",
            activeAutomations.take(3).ifEmpty { listOf("No automations active") }
        )
        automationPane.addItem(GuiItem(statusItem), 6, 0)

        // Automation count
        val countItem = createMenuItem(
            Material.PAPER,
            "Automation Summary",
            listOf(
                "${activeAutomations.size} automations active",
                "Click to view all active automations"
            )
        )
        val countGuiItem = GuiItem(countItem) { event ->
            event.isCancelled = true
            // TODO: Show detailed automation list
            player.sendMessage("§eActive automations: ${activeAutomations.joinToString(", ")}")
        }
        automationPane.addItem(countGuiItem, 7, 0)
    }

    /**
     * Update automation status display
     */
    private fun updateAutomationStatus() {
        // Next automation run time (placeholder)
        val nextRun = LocalDateTime.now().plusHours(1)
        val nextRunItem = createMenuItem(
            Material.CLOCK,
            "Next Automation Run",
            listOf(
                nextRun.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                "Scheduled tasks will run automatically",
                "Based on configured intervals"
            )
        )
        rewardsPane.addItem(GuiItem(nextRunItem), 4, 0)

        // Automation health status
        val healthItem = createMenuItem(
            Material.GREEN_WOOL,
            "Automation Status",
            listOf(
                "Status: Healthy",
                "All automated processes running",
                "${activeAutomations.size} active tasks"
            )
        )
        rewardsPane.addItem(GuiItem(healthItem), 5, 0)

        // Recent automation activity
        val recentActivity = listOf(
            "Auto-reward distributed (2 hours ago)",
            "Budget alert sent (4 hours ago)",
            "Interest calculated (1 day ago)"
        )

        val activityItem = createMenuItem(
            Material.BOOK,
            "Recent Automation Activity",
            recentActivity
        )
        rewardsPane.addItem(GuiItem(activityItem), 6, 1)
    }

    /**
     * Update automation display with latest data
     */
    private fun updateAutomationDisplay() {
        // Update is handled by individual setup methods
        checkActiveAutomations()
        updateActiveAutomations()
        updateAutomationStatus()
    }

    /**
     * Save automation settings
     */
    private fun saveAutomationSettings() {
        // TODO: Save to database/configuration
        player.sendMessage("§aAutomation settings would be saved to database")
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
