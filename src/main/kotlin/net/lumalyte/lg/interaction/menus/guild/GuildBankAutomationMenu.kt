package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.services.BankAutomationService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Guild Bank Automation menu with scheduled tasks, rewards, and alerts (REQ-010)
 */
class GuildBankAutomationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent, ChatInputHandler {

    private val bankService: BankService by inject()
    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val bankAutomationService: BankAutomationService by inject()
    private val configService: net.lumalyte.lg.application.services.ConfigService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var automationPane: StaticPane
    private lateinit var rewardsPane: StaticPane

    // Automation settings (persisted per guild via BankSettingsRepository)
    private var scheduledDepositsEnabled: Boolean = false
    private var autoRewardsEnabled: Boolean = true
    private var recurringPaymentsEnabled: Boolean = false
    private var interestRate: Double = 0.02 // 2% per compound period (fraction)

    // Active input mode for chat-based configuration
    private var inputMode: String? = null

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
     * Load automation settings from the persisted per-guild settings (REQ-010).
     */
    private fun loadAutomationSettings() {
        val settings = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        scheduledDepositsEnabled = settings.scheduledDepositsEnabled
        autoRewardsEnabled = settings.autoRewardsEnabled
        recurringPaymentsEnabled = settings.recurringPaymentsEnabled
        interestRate = settings.interestRate
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

        // Save settings button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Save Automation Settings",
            listOf("Apply current automation configuration")
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            // saveAutomationSettings() reports the upsert result itself — no
            // unconditional success message here (would contradict a failure).
            saveAutomationSettings()
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
            if (scheduledDepositsEnabled) {
                player.sendMessage("§aScheduled Deposits enabled.")
            } else {
                player.sendMessage("§cScheduled Deposits disabled.")
            }
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
            if (autoRewardsEnabled) {
                player.sendMessage("§aAuto-Rewards Distribution enabled.")
            } else {
                player.sendMessage("§cAuto-Rewards Distribution disabled.")
            }
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(rewardsGuiItem, 1, 0)

        // Budget alerts — opens the dedicated Budget Management menu
        val alertsItem = createMenuItem(
            Material.BELL,
            "Budget Alerts",
            listOf(
                "Configure spending limits and alerts",
                "Set monthly, weekly, and daily budgets",
                "Click to open Budget Management"
            )
        )
        val alertsGuiItem = GuiItem(alertsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankBudgetMenu(menuNavigator, player, guild))
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
            if (recurringPaymentsEnabled) {
                player.sendMessage("§aRecurring Payments enabled.")
            } else {
                player.sendMessage("§cRecurring Payments disabled.")
            }
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
            inputMode = "interestRate"
            chatInputListener.startInputMode(player, this@GuildBankAutomationMenu)
            player.sendMessage("§eType the interest rate as a decimal (e.g. 0.02 = 2% per compound period). Type 'cancel' to abort.")
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
     * Update automation status display (REQ-010): real next-run time + honest status.
     */
    private fun updateAutomationStatus() {
        // Real next run: last accrual + compound period, or the periodic scheduler cadence
        val nextRun = bankAutomationService.getNextInterestRun(guild.id)
        val nextRunText = nextRun?.let {
            it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))
        } ?: "Pending first accrual"

        val nextRunItem = createMenuItem(
            Material.CLOCK,
            "Next Interest Accrual",
            listOf(
                nextRunText,
                "Interest accrues every ${interestPeriodHours()} hours",
                "Per guild balance at the configured rate"
            )
        )
        rewardsPane.addItem(GuiItem(nextRunItem), 4, 0)

        // Automation health status (derived from persisted settings, not hardcoded)
        val activeCount = activeAutomations.size
        val statusLore = if (activeCount > 0) {
            listOf(
                "Status: ${activeCount} automation(s) configured",
                "Interest rate: ${String.format("%.2f", interestRate * 100)}% per ${interestPeriodHours()}h",
                "Scheduled deposits: ${if (scheduledDepositsEnabled) "ON" else "OFF"} | Auto-rewards: ${if (autoRewardsEnabled) "ON" else "OFF"}"
            )
        } else {
            listOf("Status: No automations configured", "Toggle settings above to enable")
        }
        val healthItem = createMenuItem(
            if (activeCount > 0) Material.GREEN_WOOL else Material.GRAY_WOOL,
            "Automation Status",
            statusLore
        )
        rewardsPane.addItem(GuiItem(healthItem), 5, 0)

        // Recent automation activity
        val recentActivity = listOf(
            "Interest accrual: every ${interestPeriodHours()}h",
            "Audit retention: ${auditRetentionDays()} days",
            "Scheduler: periodic (5 min checks)"
        )

        val activityItem = createMenuItem(
            Material.BOOK,
            "Automation Configuration",
            recentActivity
        )
        rewardsPane.addItem(GuiItem(activityItem), 6, 1)
    }

    private fun interestPeriodHours(): Int =
        configService.loadConfig().bank.interestCompoundPeriodHours

    private fun auditRetentionDays(): Int =
        configService.loadConfig().bank.auditLogRetentionDays

    /**
     * Update automation display with latest data
     */
    private fun updateAutomationDisplay() {
        // Clear and recreate automation pane to reflect toggle changes
        automationPane.clear()
        setupAutomationSettings()

        // Update other displays
        checkActiveAutomations()
        updateActiveAutomations()
        updateAutomationStatus()
    }

    /**
     * Save automation settings (REQ-010): persists all knobs per guild.
     */
    private fun saveAutomationSettings() {
        val current = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        val updated = current.copy(
            scheduledDepositsEnabled = scheduledDepositsEnabled,
            autoRewardsEnabled = autoRewardsEnabled,
            recurringPaymentsEnabled = recurringPaymentsEnabled,
            interestRate = interestRate
        )
        val saved = bankSettingsRepository.upsert(updated)
        if (saved) {
            player.sendMessage("§aAutomation settings saved!")
        } else {
            player.sendMessage("§cFailed to save automation settings.")
        }
    }

    // ChatInputHandler interface methods (REQ-010)
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "interestRate" -> {
                val rate = input.trim().toDoubleOrNull()
                if (rate == null || rate < 0.0 || rate > 1.0) {
                    player.sendMessage("§cInvalid interest rate. Enter a decimal between 0 and 1 (e.g. 0.02 = 2%).")
                } else {
                    interestRate = rate
                    player.sendMessage("§aInterest rate set to ${String.format("%.2f", rate * 100)}% per compound period.")
                }
            }
            else -> return
        }
        inputMode = null
        checkActiveAutomations()
        updateAutomationDisplay()
        gui.update()
    }

    override fun onCancel(player: Player) {
        inputMode = null
        player.sendMessage("§eInterest rate input cancelled.")
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack.of(material)
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
