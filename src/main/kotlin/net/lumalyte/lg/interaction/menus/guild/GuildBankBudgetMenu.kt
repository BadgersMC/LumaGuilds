package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.persistence.BankSettingsRepository
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
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Guild Bank Budget Management menu with spending limits and alerts (REQ-011)
 */
class GuildBankBudgetMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent, ChatInputHandler {

    private val bankService: BankService by inject()
    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val lang: LangService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var budgetPane: StaticPane
    private lateinit var alertsPane: StaticPane

    // Budget data (persisted per guild via BankSettingsRepository)
    private var monthlyBudget: Int = 0
    private var weeklyBudget: Int = 0
    private var dailyBudget: Int = 0
    private var budgetAlerts: MutableList<String> = mutableListOf()

    // Active input mode for chat-based configuration
    private var inputMode: String? = null

    init {
        loadBudgetSettings()
        calculateAlerts()
        initializeGui()
    }

    override fun open() {
        updateBudgetDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Handle budget updates
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val updates = data as Map<String, Int>
            updates.forEach { (period, amount) ->
                when (period) {
                    "monthly" -> monthlyBudget = amount
                    "weekly" -> weeklyBudget = amount
                    "daily" -> dailyBudget = amount
                }
            }
            calculateAlerts()
            updateBudgetDisplay()
            gui.update()
        }
    }

    /**
     * Load budget settings from the persisted per-guild settings (REQ-011).
     */
    private fun loadBudgetSettings() {
        val settings = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        monthlyBudget = settings.monthlyBudget
        weeklyBudget = settings.weeklyBudget
        dailyBudget = settings.dailyBudget
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

        if (monthlyPercent >= 90) budgetAlerts.add(lang.legacy("menu.bank_budget.alert.monthly", "percent" to String.format("%.1f", monthlyPercent)))
        if (weeklyPercent >= 80) budgetAlerts.add(lang.legacy("menu.bank_budget.alert.weekly", "percent" to String.format("%.1f", weeklyPercent)))
        if (dailyPercent >= 75) budgetAlerts.add(lang.legacy("menu.bank_budget.alert.daily", "percent" to String.format("%.1f", dailyPercent)))

        if (monthlyPercent >= 100) budgetAlerts.add(lang.raw("menu.bank_budget.alert.monthly_exceeded"))
        if (weeklyPercent >= 100) budgetAlerts.add(lang.raw("menu.bank_budget.alert.weekly_exceeded"))
        if (dailyPercent >= 100) budgetAlerts.add(lang.raw("menu.bank_budget.alert.daily_exceeded"))
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(5, MenuTitleBuilder.build(guild.guiTheme, 5, lang.legacy("menu.bank_budget.title", "guild" to guild.name)))
        gui.setOnGlobalClick { event -> event.isCancelled = true }

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
            getLocalizedString("menu.bank.back_to_control_panel"),
            listOf(lang.raw("menu.bank_budget.navigation.bank"))
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Back to statistics button
        val statsItem = createMenuItem(
            Material.BOOK,
            getLocalizedString("menu.bank.stats.title"),
            listOf(lang.raw("menu.bank_budget.navigation.statistics"))
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 1, 0)

        // Save button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            lang.raw("menu.bank_budget.navigation.save.name"),
            listOf(lang.raw("menu.bank_budget.navigation.save.description"))
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            // saveBudgetSettings() reports the upsert result itself — no
            // unconditional success message here (would contradict a failure).
            saveBudgetSettings()
        }
        mainPane.addItem(saveGuiItem, 7, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString("menu.bank.close"),
            listOf(lang.raw("menu.bank_budget.navigation.close"))
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
            lang.raw("menu.bank_budget.period.monthly.name"),
            listOf(
                lang.legacy("menu.bank_budget.period.current", "amount" to monthlyBudget),
                lang.raw("menu.bank_budget.period.monthly.action"),
                lang.raw("menu.bank_budget.period.monthly.range")
            )
        )
        val monthlyGuiItem = GuiItem(monthlyItem) { event ->
            event.isCancelled = true
            inputMode = "monthly"
            chatInputListener.startInputMode(player, this@GuildBankBudgetMenu)
            player.sendMessage(lang.msg("menu.bank_budget.prompt.monthly"))
        }
        budgetPane.addItem(monthlyGuiItem, 0, 0)

        // Weekly budget
        val weeklyItem = createMenuItem(
            Material.TRAPPED_CHEST,
            lang.raw("menu.bank_budget.period.weekly.name"),
            listOf(
                lang.legacy("menu.bank_budget.period.current", "amount" to weeklyBudget),
                lang.raw("menu.bank_budget.period.weekly.action"),
                lang.raw("menu.bank_budget.period.weekly.range")
            )
        )
        val weeklyGuiItem = GuiItem(weeklyItem) { event ->
            event.isCancelled = true
            inputMode = "weekly"
            chatInputListener.startInputMode(player, this@GuildBankBudgetMenu)
            player.sendMessage(lang.msg("menu.bank_budget.prompt.weekly"))
        }
        budgetPane.addItem(weeklyGuiItem, 1, 0)

        // Daily budget
        val dailyItem = createMenuItem(
            Material.ENDER_CHEST,
            lang.raw("menu.bank_budget.period.daily.name"),
            listOf(
                lang.legacy("menu.bank_budget.period.current", "amount" to dailyBudget),
                lang.raw("menu.bank_budget.period.daily.action"),
                lang.raw("menu.bank_budget.period.daily.range")
            )
        )
        val dailyGuiItem = GuiItem(dailyItem) { event ->
            event.isCancelled = true
            inputMode = "daily"
            chatInputListener.startInputMode(player, this@GuildBankBudgetMenu)
            player.sendMessage(lang.msg("menu.bank_budget.prompt.daily"))
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
                lang.raw("menu.bank_budget.alert.empty.name"),
                listOf(
                    lang.raw("menu.bank_budget.alert.empty.description"),
                    lang.raw("menu.bank_budget.alert.empty.encouragement")
                )
            )
            alertsPane.addItem(GuiItem(noAlertsItem), 0, 0)
        } else {
            // Display alerts
            budgetAlerts.take(5).forEachIndexed { index, alert ->
                val alertItem = createMenuItem(
                    if (alert.contains("⚠")) Material.RED_WOOL else Material.YELLOW_WOOL,
                    lang.raw("menu.bank_budget.alert.name"),
                    listOf(alert, lang.raw("menu.bank_budget.alert.monitor"))
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
            lang.raw("menu.bank_budget.status.monthly"),
            listOf(
                lang.legacy("menu.bank_budget.status.spent", "spent" to monthlySpent, "budget" to monthlyBudget),
                lang.legacy("menu.bank_budget.status.used", "percent" to String.format("%.1f", (monthlySpent.toDouble() / monthlyBudget) * 100)),
                lang.legacy("menu.bank_budget.status.remaining", "amount" to monthlyBudget - monthlySpent)
            )
        )
        budgetPane.addItem(GuiItem(monthlyStatusItem), 4, 0)

        val weeklyStatusItem = createMenuItem(
            getBudgetStatusMaterial(weeklySpent, weeklyBudget),
            lang.raw("menu.bank_budget.status.weekly"),
            listOf(
                lang.legacy("menu.bank_budget.status.spent", "spent" to weeklySpent, "budget" to weeklyBudget),
                lang.legacy("menu.bank_budget.status.used", "percent" to String.format("%.1f", (weeklySpent.toDouble() / weeklyBudget) * 100)),
                lang.legacy("menu.bank_budget.status.remaining", "amount" to weeklyBudget - weeklySpent)
            )
        )
        budgetPane.addItem(GuiItem(weeklyStatusItem), 5, 0)

        val dailyStatusItem = createMenuItem(
            getBudgetStatusMaterial(dailySpent, dailyBudget),
            lang.raw("menu.bank_budget.status.daily"),
            listOf(
                lang.legacy("menu.bank_budget.status.spent", "spent" to dailySpent, "budget" to dailyBudget),
                lang.legacy("menu.bank_budget.status.used", "percent" to String.format("%.1f", (dailySpent.toDouble() / dailyBudget) * 100)),
                lang.legacy("menu.bank_budget.status.remaining", "amount" to dailyBudget - dailySpent)
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
     * Save budget settings (REQ-011): persists all three limits per guild.
     */
    private fun saveBudgetSettings() {
        val current = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        val updated = current.copy(
            monthlyBudget = monthlyBudget,
            weeklyBudget = weeklyBudget,
            dailyBudget = dailyBudget
        )
        val saved = bankSettingsRepository.upsert(updated)
        if (saved) {
            player.sendMessage(lang.msg("menu.bank_budget.feedback.saved"))
        } else {
            player.sendMessage(lang.msg("menu.bank_budget.feedback.save_failed"))
        }
    }

    // ChatInputHandler interface methods (REQ-011)
    override fun onChatInput(player: Player, input: String) {
        // Guard FIRST: if the listener session outlived the menu interaction,
        // inputMode is null and this is ordinary chat — do not intercept it.
        val mode = inputMode ?: return

        val amount = input.trim().toIntOrNull()
        if (amount == null || amount < 0) {
            player.sendMessage(lang.msg("menu.bank_budget.feedback.invalid_amount"))
            inputMode = null
            return
        }
        when (mode) {
            "monthly" -> {
                monthlyBudget = amount
                player.sendMessage(lang.msg("menu.bank_budget.feedback.monthly_set", "amount" to amount))
            }
            "weekly" -> {
                weeklyBudget = amount
                player.sendMessage(lang.msg("menu.bank_budget.feedback.weekly_set", "amount" to amount))
            }
            "daily" -> {
                dailyBudget = amount
                player.sendMessage(lang.msg("menu.bank_budget.feedback.daily_set", "amount" to amount))
            }
            else -> return
        }
        inputMode = null
        calculateAlerts()
        updateBudgetDisplay()
        gui.update()
    }

    override fun onCancel(player: Player) {
        inputMode = null
        player.sendMessage(lang.msg("menu.bank_budget.feedback.cancelled"))
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
    private fun getLocalizedString(key: String): String {
        return lang.legacy(key)
    }
}
