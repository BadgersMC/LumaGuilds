package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.services.BankAutomationService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
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
    private val lang: LangService by inject()
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
    private var activeAutomations: MutableList<Component> = mutableListOf()

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
            activeAutomations.add(lang.gui("menu.bank_automation.active.scheduled_deposits"))
        }
        if (autoRewardsEnabled) {
            activeAutomations.add(lang.gui("menu.bank_automation.active.auto_rewards"))
        }
        if (recurringPaymentsEnabled) {
            activeAutomations.add(lang.gui("menu.bank_automation.active.recurring_payments"))
        }
        if (interestRate > 0) {
            activeAutomations.add(lang.gui("menu.bank_automation.active.interest", "rate" to String.format("%.1f", interestRate * 100)))
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(5, MenuTitleBuilder.build(guild.guiTheme, 5, lang.guiTitle("menu.bank_automation.title", "guild" to guild.name)))
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
            lang.gui("menu.bank_automation.navigation.bank.name"),
            listOf(lang.gui("menu.bank_automation.navigation.bank.description"))
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Back to statistics button
        val statsItem = createMenuItem(
            Material.BOOK,
            lang.gui("menu.bank_automation.navigation.statistics.name"),
            listOf(lang.gui("menu.bank_automation.navigation.statistics.description"))
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 1, 0)

        // Save settings button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            lang.gui("menu.bank_automation.navigation.save.name"),
            listOf(lang.gui("menu.bank_automation.navigation.save.description"))
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
            lang.gui("menu.bank_automation.navigation.close.name"),
            listOf(lang.gui("menu.bank_automation.navigation.close.description"))
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
            lang.gui("menu.bank_automation.scheduled.name"),
            listOf(
                toggleStatus(scheduledDepositsEnabled),
                lang.gui("menu.bank_automation.scheduled.description"),
                lang.gui("menu.bank_automation.common.toggle")
            )
        )
        val scheduledGuiItem = GuiItem(scheduledItem) { event ->
            event.isCancelled = true
            scheduledDepositsEnabled = !scheduledDepositsEnabled
            if (scheduledDepositsEnabled) {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.scheduled_enabled"))
            } else {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.scheduled_disabled"))
            }
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(scheduledGuiItem, 0, 0)

        // Auto-rewards toggle
        val rewardsItem = createMenuItem(
            if (autoRewardsEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            lang.gui("menu.bank_automation.rewards.name"),
            listOf(
                toggleStatus(autoRewardsEnabled),
                lang.gui("menu.bank_automation.rewards.description"),
                lang.gui("menu.bank_automation.common.toggle")
            )
        )
        val rewardsGuiItem = GuiItem(rewardsItem) { event ->
            event.isCancelled = true
            autoRewardsEnabled = !autoRewardsEnabled
            if (autoRewardsEnabled) {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.rewards_enabled"))
            } else {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.rewards_disabled"))
            }
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(rewardsGuiItem, 1, 0)

        // Budget alerts — opens the dedicated Budget Management menu
        val alertsItem = createMenuItem(
            Material.BELL,
            lang.gui("menu.bank_automation.alerts.name"),
            listOf(
                lang.gui("menu.bank_automation.alerts.description"),
                lang.gui("menu.bank_automation.alerts.periods"),
                lang.gui("menu.bank_automation.alerts.action")
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
            lang.gui("menu.bank_automation.recurring.name"),
            listOf(
                toggleStatus(recurringPaymentsEnabled),
                lang.gui("menu.bank_automation.recurring.description"),
                lang.gui("menu.bank_automation.common.toggle")
            )
        )
        val recurringGuiItem = GuiItem(recurringItem) { event ->
            event.isCancelled = true
            recurringPaymentsEnabled = !recurringPaymentsEnabled
            if (recurringPaymentsEnabled) {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.recurring_enabled"))
            } else {
                player.sendMessage(lang.msg("menu.bank_automation.feedback.recurring_disabled"))
            }
            checkActiveAutomations()
            updateAutomationDisplay()
            gui.update()
        }
        automationPane.addItem(recurringGuiItem, 3, 0)

        // Interest rate setting
        val interestItem = createMenuItem(
            Material.GOLD_INGOT,
            lang.gui("menu.bank_automation.interest.name"),
            listOf(
                lang.gui("menu.bank_automation.interest.current", "rate" to String.format("%.1f", interestRate * 100)),
                lang.gui("menu.bank_automation.interest.description"),
                lang.gui("menu.bank_automation.interest.action")
            )
        )
        val interestGuiItem = GuiItem(interestItem) { event ->
            event.isCancelled = true
            inputMode = "interestRate"
            chatInputListener.startInputMode(player, this@GuildBankAutomationMenu)
            player.sendMessage(lang.msg("menu.bank_automation.feedback.interest_prompt"))
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
            lang.gui("menu.bank_automation.setup.rewards.name"),
            listOf(
                lang.gui("menu.bank_automation.setup.rewards.description"),
                lang.gui("menu.bank_automation.setup.rewards.conditions"),
                lang.gui("menu.bank_automation.setup.rewards.basis")
            )
        )
        val rewardSetupGuiItem = GuiItem(rewardSetupItem) { event ->
            event.isCancelled = true
            // TODO: Open reward setup menu
            player.sendMessage(lang.msg("menu.bank_automation.feedback.rewards_coming_soon"))
        }
        rewardsPane.addItem(rewardSetupGuiItem, 0, 0)

        // Alert threshold configuration
        val alertConfigItem = createMenuItem(
            Material.BELL,
            lang.gui("menu.bank_automation.setup.alerts.name"),
            listOf(
                lang.gui("menu.bank_automation.setup.alerts.description"),
                lang.gui("menu.bank_automation.setup.alerts.notifications"),
                lang.gui("menu.bank_automation.setup.alerts.messages")
            )
        )
        val alertConfigGuiItem = GuiItem(alertConfigItem) { event ->
            event.isCancelled = true
            // TODO: Open alert configuration menu
            player.sendMessage(lang.msg("menu.bank_automation.feedback.alerts_coming_soon"))
        }
        rewardsPane.addItem(alertConfigGuiItem, 1, 0)

        // Recurring payment setup
        val paymentSetupItem = createMenuItem(
            Material.CLOCK,
            lang.gui("menu.bank_automation.setup.recurring.name"),
            listOf(
                lang.gui("menu.bank_automation.setup.recurring.description"),
                lang.gui("menu.bank_automation.setup.recurring.schedules"),
                lang.gui("menu.bank_automation.setup.recurring.recipients")
            )
        )
        val paymentSetupGuiItem = GuiItem(paymentSetupItem) { event ->
            event.isCancelled = true
            // TODO: Open recurring payment setup
            player.sendMessage(lang.msg("menu.bank_automation.feedback.recurring_coming_soon"))
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
            lang.gui("menu.bank_automation.summary.active.name"),
            activeAutomations.take(3).ifEmpty { listOf(lang.gui("menu.bank_automation.summary.active.empty")) }
        )
        automationPane.addItem(GuiItem(statusItem), 6, 0)

        // Automation count
        val countItem = createMenuItem(
            Material.PAPER,
            lang.gui("menu.bank_automation.summary.name"),
            listOf(
                lang.gui("menu.bank_automation.summary.count", "count" to activeAutomations.size),
                lang.gui("menu.bank_automation.summary.action")
            )
        )
        val countGuiItem = GuiItem(countItem) { event ->
            event.isCancelled = true
            // TODO: Show detailed automation list
            player.sendMessage(lang.msg("menu.bank_automation.feedback.active", "automations" to activeAutomations.joinToString(", ")))
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
        } ?: lang.gui("menu.bank_automation.status.pending_first_accrual")

        val nextRunItem = createMenuItem(
            Material.CLOCK,
            lang.gui("menu.bank_automation.status.next.name"),
            listOf(
                nextRunText,
                lang.gui("menu.bank_automation.status.next.period", "hours" to interestPeriodHours()),
                lang.gui("menu.bank_automation.status.next.description")
            )
        )
        rewardsPane.addItem(GuiItem(nextRunItem), 4, 0)

        // Automation health status (derived from persisted settings, not hardcoded)
        val activeCount = activeAutomations.size
        val statusLore = if (activeCount > 0) {
            listOf(
                lang.gui("menu.bank_automation.status.health.configured", "count" to activeCount),
                lang.gui("menu.bank_automation.status.health.interest", "rate" to String.format("%.2f", interestRate * 100), "hours" to interestPeriodHours()),
                lang.gui("menu.bank_automation.status.health.toggles", "scheduled" to toggleWord(scheduledDepositsEnabled), "rewards" to toggleWord(autoRewardsEnabled))
            )
        } else {
            listOf(lang.gui("menu.bank_automation.status.health.empty"), lang.gui("menu.bank_automation.status.health.hint"))
        }
        val healthItem = createMenuItem(
            if (activeCount > 0) Material.GREEN_WOOL else Material.GRAY_WOOL,
            lang.gui("menu.bank_automation.status.health.name"),
            statusLore
        )
        rewardsPane.addItem(GuiItem(healthItem), 5, 0)

        // Recent automation activity
        val recentActivity = listOf(
            lang.gui("menu.bank_automation.status.configuration.interest", "hours" to interestPeriodHours()),
            lang.gui("menu.bank_automation.status.configuration.audit", "days" to auditRetentionDays()),
            lang.gui("menu.bank_automation.status.configuration.scheduler")
        )

        val activityItem = createMenuItem(
            Material.BOOK,
            lang.gui("menu.bank_automation.status.configuration.name"),
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
            player.sendMessage(lang.msg("menu.bank_automation.feedback.saved"))
        } else {
            player.sendMessage(lang.msg("menu.bank_automation.feedback.save_failed"))
        }
    }

    // ChatInputHandler interface methods (REQ-010)
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "interestRate" -> {
                val rate = input.trim().toDoubleOrNull()
                if (rate == null || rate < 0.0 || rate > 1.0) {
                    player.sendMessage(lang.msg("menu.bank_automation.feedback.invalid_interest"))
                } else {
                    interestRate = rate
                    player.sendMessage(lang.msg("menu.bank_automation.feedback.interest_set", "rate" to String.format("%.2f", rate * 100)))
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
        player.sendMessage(lang.msg("menu.bank_automation.feedback.interest_cancelled"))
    }

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

    private fun toggleStatus(enabled: Boolean): Component = if (enabled) {
        lang.gui("menu.bank_automation.common.status.enabled")
    } else {
        lang.gui("menu.bank_automation.common.status.disabled")
    }

    private fun toggleWord(enabled: Boolean): Component = if (enabled) {
        lang.gui("menu.bank_automation.common.enabled_word")
    } else {
        lang.gui("menu.bank_automation.common.disabled_word")
    }
}
