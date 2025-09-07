package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui

import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit

import net.lumalyte.lg.domain.entities.BankTransaction
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
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.max
import kotlin.math.min

/**
 * Guild Bank menu allowing players to deposit, withdraw, and view transaction history
 */
class GuildBankMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent, Listener {

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    // Current balance cache
    private var currentBalance = 0
    private var previousBalance = 0

    // UI/UX Enhancement variables
    private var isLoading = false
    private var lastTransactionTime = 0L
    private var animationTasks = mutableListOf<org.bukkit.scheduler.BukkitTask>()
    private var loadingSlots = mutableListOf<Pair<Int, Int>>()

    // Sound and animation constants
    private val BUTTON_CLICK_SOUND = Sound.UI_BUTTON_CLICK
    private val SUCCESS_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP
    private val ERROR_SOUND = Sound.ENTITY_VILLAGER_NO
    private val COIN_SOUND = Sound.ENTITY_ITEM_PICKUP

    // Animation constants
    private val ANIMATION_DURATION = 20L // ticks
    private val BALANCE_UPDATE_INTERVAL = 2L
    private val LOADING_SPINNER_INTERVAL = 4L

    // Custom input state
    private var awaitingCustomAmount = false
    private var customAmountType: TransactionType? = null

    init {
        initializeGui()
        loadBalance()
    }

    override fun open() {
        // Check Vault availability on menu open
        if (!isEconomyAvailable()) {
            // Show error message and don't open the menu
            player.sendMessage("§c⚠ Guild Bank Unavailable")
            player.sendMessage("§cVault economy system not found!")
            player.sendMessage("§cPlease install Vault and an economy plugin (Essentials, iConomy, etc.)")
            player.sendMessage("§cContact your server administrator for assistance.")

            // Play error sound
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            return
        }

        refreshBalance()

        // Register event listener for custom input
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("LumaGuilds")!!)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Cleanup any running animations before changing data
        cleanup()

        guild = data as? Guild ?: return
        refreshBalance()
        gui.update()
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, getLocalizedTitle())
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // Create main pane for balance and quick actions
        mainPane = StaticPane(0, 0, 9, 4, Priority.NORMAL)
        gui.addPane(mainPane)

        setupBalanceDisplay()
        setupQuickActions()
        setupCustomActions()
        setupTransactionHistory()
        setupNavigation()
    }

    /**
     * Setup the balance display section
     */
    private fun setupBalanceDisplay() {
        // Player balance display (moved to first slot)
        updatePlayerBalanceDisplay()

        // Current balance display (will be updated dynamically)
        updateBalanceDisplay()
    }

    /**
     * Update the balance display (original method - now calls enhanced version)
     */
    private fun updateBalanceDisplay() {
        updateBalanceDisplayEnhanced()
    }

    /**
     * Setup quick action buttons for common amounts
     */
    private fun setupQuickActions() {
        // Deposit buttons (top row)
        val deposit100Item = createQuickActionItem(
            Material.LIME_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_DEPOSIT_100,
            100,
            true
        )
        mainPane.addItem(deposit100Item, 0, 1)

        val deposit1000Item = createQuickActionItem(
            Material.LIME_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_DEPOSIT_1000,
            1000,
            true
        )
        mainPane.addItem(deposit1000Item, 1, 1)

        val deposit10000Item = createQuickActionItem(
            Material.LIME_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_DEPOSIT_10000,
            10000,
            true
        )
        mainPane.addItem(deposit10000Item, 2, 1)

        val depositAllItem = createQuickActionItem(
            Material.LIME_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_DEPOSIT_ALL,
            -1, // Special value for all
            true
        )
        mainPane.addItem(depositAllItem, 3, 1)

        // Withdraw buttons (second row)
        val withdraw100Item = createQuickActionItem(
            Material.RED_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_WITHDRAW_100,
            100,
            false
        )
        mainPane.addItem(withdraw100Item, 0, 2)

        val withdraw1000Item = createQuickActionItem(
            Material.RED_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_WITHDRAW_1000,
            1000,
            false
        )
        mainPane.addItem(withdraw1000Item, 1, 2)

        val withdraw10000Item = createQuickActionItem(
            Material.RED_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_WITHDRAW_10000,
            10000,
            false
        )
        mainPane.addItem(withdraw10000Item, 2, 2)

        val withdrawAllItem = createQuickActionItem(
            Material.RED_WOOL,
            LocalizationKeys.MENU_BANK_QUICK_WITHDRAW_ALL,
            -1, // Special value for all
            false
        )
        mainPane.addItem(withdrawAllItem, 3, 2)
    }

    /**
     * Setup custom amount action buttons
     */
    private fun setupCustomActions() {
        // Custom deposit button
        val customDepositItem = createMenuItem(
            Material.GREEN_WOOL,
            getLocalizedString(LocalizationKeys.MENU_BANK_CUSTOM_DEPOSIT),
            listOf("Enter custom deposit amount")
        )
        val depositGuiItem = GuiItem(customDepositItem) { event ->
            event.isCancelled = true
            openCustomAmountDialog(TransactionType.DEPOSIT)
        }
        mainPane.addItem(depositGuiItem, 6, 1)

        // Custom withdraw button
        val customWithdrawItem = createMenuItem(
            Material.RED_WOOL,
            getLocalizedString(LocalizationKeys.MENU_BANK_CUSTOM_WITHDRAW),
            listOf("Enter custom withdrawal amount")
        )
        val withdrawGuiItem = GuiItem(customWithdrawItem) { event ->
            event.isCancelled = true
            openCustomAmountDialog(TransactionType.WITHDRAWAL)
        }
        mainPane.addItem(withdrawGuiItem, 6, 2)
    }

    /**
     * Setup transaction history display
     */
    private fun setupTransactionHistory() {
        val transactions = bankService.getTransactionHistory(guild.id, 8)

        // Create transaction history button
        val historyItem = if (transactions.isEmpty()) {
            createMenuItem(
                Material.BARRIER,
                getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_TITLE),
                listOf(getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_NO_TRANSACTIONS))
            )
        } else {
            createMenuItem(
                Material.BOOK,
                getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_TITLE),
                listOf("${transactions.size} recent transactions", "Click to view full history")
            )
        }

        // Make it clickable to open detailed history menu
        val historyGuiItem = GuiItem(historyItem) { event ->
            event.isCancelled = true
            openTransactionHistory()
        }
        mainPane.addItem(historyGuiItem, 7, 0)
    }

    /**
     * Open the detailed transaction history menu
     */
    private fun openTransactionHistory() {
        val historyMenu = GuildBankTransactionHistoryMenu(menuNavigator, player, guild)
        menuNavigator.openMenu(historyMenu)
    }

    /**
     * Setup navigation buttons
     */
    private fun setupNavigation() {
        // Statistics button
        val statsItem = createMenuItem(
            Material.BOOK,
            getLocalizedString(LocalizationKeys.MENU_BANK_STATS_TITLE),
            listOf("View detailed bank statistics and analytics")
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 7, 3)

        // Member Contributions button
        val contributionsItem = createMenuItem(
            Material.PLAYER_HEAD,
            "Member Contributions",
            listOf("See who contributes and who freeloads", "Net deposits vs withdrawals")
        )
        val contributionsGuiItem = GuiItem(contributionsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildMemberContributionsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(contributionsGuiItem, 6, 3)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            getLocalizedString(LocalizationKeys.MENU_BANK_BACK_TO_CONTROL_PANEL),
            listOf("Return to guild control panel")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 8, 3)
    }

    /**
     * Create a quick action item for deposits/withdrawals
     */
    private fun createQuickActionItem(material: Material, localizationKey: String, amount: Int, isDeposit: Boolean): GuiItem {
        val displayName = getLocalizedString(localizationKey)
        val lore = if (amount == -1) {
            listOf("Click to ${if (isDeposit) "deposit" else "withdraw"} all available")
        } else {
            listOf("Click to ${if (isDeposit) "deposit" else "withdraw"} ${amount} coins")
        }

        val itemStack = createMenuItem(material, displayName, lore)
        return GuiItem(itemStack) { event ->
            event.isCancelled = true
            handleQuickAction(amount, isDeposit)
        }
    }

    /**
     * Handle quick deposit/withdraw actions with enhanced UI feedback
     */
    private fun handleQuickAction(amount: Int, isDeposit: Boolean) {
        // Check if Vault economy is available
        if (!isEconomyAvailable()) {
            showErrorFeedback("Vault economy not available! Guild Bank requires Vault and an economy plugin (Essentials, iConomy, etc.)")
            return
        }

        // Prevent spam clicking
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTransactionTime < 1000) { // 1 second cooldown
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }
        lastTransactionTime = currentTime

        // Play button click sound
        player.playSound(player.location, BUTTON_CLICK_SOUND, 1.0f, 1.0f)

        val actualAmount = if (amount == -1) {
            if (isDeposit) {
                // Get player's current balance from Vault economy
                bankService.getPlayerBalance(player.uniqueId)
            } else {
                // For withdraw all, use guild balance
                currentBalance
            }
        } else {
            amount
        }

        // Show loading state
        showLoadingState(if (isDeposit) "Depositing..." else "Withdrawing...")

        // Use async task for transaction processing
        object : BukkitRunnable() {
            override fun run() {
                val success = if (isDeposit) {
                    handleDeposit(actualAmount)
                } else {
                    handleWithdrawal(actualAmount)
                }

                // Schedule UI update on main thread
                object : BukkitRunnable() {
                    override fun run() {
                        hideLoadingState()

                        if (success) {
                            // Animate balance change
                            animateBalanceChange(currentBalance, bankService.getBalance(guild.id))

                            // Play success sound and visual feedback
                            player.playSound(player.location, SUCCESS_SOUND, 1.0f, 1.5f)
                            player.playSound(player.location, COIN_SOUND, 1.0f, 1.2f)

                            // Update UI
                            refreshBalance()
                            gui.update()
                        } else {
                            // Play error sound
                            player.playSound(player.location, ERROR_SOUND, 1.0f, 0.8f)
                        }
                    }
                }.runTask(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!)
            }
        }.runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!)
    }

    /**
     * Handle deposit operation with enhanced feedback
     */
    private fun handleDeposit(amount: Int): Boolean {
        return try {
            val transaction = bankService.deposit(guild.id, player.uniqueId, amount)
            if (transaction != null) {
                val message = getLocalizedString(
                    LocalizationKeys.MENU_BANK_FEEDBACK_DEPOSIT_SUCCESS,
                    amount.toString()
                )
                player.sendMessage(Component.text(message).color(NamedTextColor.GREEN))
                // Show enhanced success feedback
                showSuccessFeedback("Deposit successful!", amount)
                true
            } else {
                val message = getLocalizedString(LocalizationKeys.MENU_BANK_FEEDBACK_NO_PERMISSION)
                player.sendMessage(Component.text(message).color(NamedTextColor.RED))
                showErrorFeedback(message)
                false
            }
        } catch (e: Exception) {
            val errorMessage = "An error occurred during deposit."
            player.sendMessage(Component.text("§c$errorMessage"))
            showErrorFeedback(errorMessage)
            false
        }
    }

    /**
     * Handle withdrawal operation with enhanced feedback
     */
    private fun handleWithdrawal(amount: Int): Boolean {
        return try {
            val transaction = bankService.withdraw(guild.id, player.uniqueId, amount)
            if (transaction != null) {
                val message = getLocalizedString(
                    LocalizationKeys.MENU_BANK_FEEDBACK_WITHDRAW_SUCCESS,
                    amount.toString()
                )
                player.sendMessage(Component.text(message).color(NamedTextColor.GREEN))
                // Show enhanced success feedback
                showSuccessFeedback("Withdrawal successful!", -amount) // Negative for withdrawal
                true
            } else {
                val message = getLocalizedString(LocalizationKeys.MENU_BANK_FEEDBACK_INSUFFICIENT_GUILD_FUNDS)
                player.sendMessage(Component.text(message).color(NamedTextColor.RED))
                showErrorFeedback(message)
                false
            }
        } catch (e: Exception) {
            val errorMessage = "An error occurred during withdrawal."
            player.sendMessage(Component.text("§c$errorMessage"))
            showErrorFeedback(errorMessage)
            false
        }
    }

    /**
     * Create a transaction history item
     */
    private fun createTransactionItem(transaction: BankTransaction): ItemStack {
        val transactionType = when (transaction.type) {
            TransactionType.DEPOSIT -> getLocalizedString(LocalizationKeys.MENU_BANK_TRANSACTION_DEPOSIT)
            TransactionType.WITHDRAWAL -> getLocalizedString(LocalizationKeys.MENU_BANK_TRANSACTION_WITHDRAWAL)
            TransactionType.FEE -> getLocalizedString(LocalizationKeys.MENU_BANK_TRANSACTION_FEE)
            TransactionType.DEDUCTION -> "Deduction"
        }

        val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: "Unknown"
        val timestamp = transaction.timestamp.toString().substring(0, 16) // Simple date formatting

        val material = when (transaction.type) {
            TransactionType.DEPOSIT -> Material.LIME_WOOL
            TransactionType.WITHDRAWAL -> Material.RED_WOOL
            TransactionType.FEE -> Material.ORANGE_WOOL
            TransactionType.DEDUCTION -> Material.GRAY_WOOL
        }

        return createMenuItem(
            material,
            "${transactionType}: ${transaction.amount}",
            listOf(
                "By: $actorName",
                "Time: $timestamp",
                if (transaction.fee > 0) "Fee: ${transaction.fee}" else ""
            ).filter { it.isNotEmpty() }
        )
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
     * Get localized title for the GUI
     */
    private fun getLocalizedTitle(): String {
        return getLocalizedString(LocalizationKeys.MENU_BANK_TITLE, guild.name)
    }

    /**
     * Load current balance
     */
    private fun loadBalance() {
        currentBalance = bankService.getBalance(guild.id)
    }

    /**
     * Refresh balance from service
     */
    private fun refreshBalance() {
        loadBalance()
        updateBalanceDisplay()
        updatePlayerBalanceDisplay()
    }

    /**
     * Show loading state on buttons during transaction processing
     */
    private fun showLoadingState(message: String) {
        isLoading = true

        // Replace action buttons with loading indicators
        val loadingSlots = listOf(
            Pair(0, 1), Pair(1, 1), Pair(2, 1), Pair(3, 1), // Deposit buttons
            Pair(0, 2), Pair(1, 2), Pair(2, 2), Pair(3, 2)  // Withdraw buttons
        )

        loadingSlots.forEach { (x, y) ->
            val loadingItem = createMenuItem(
                Material.YELLOW_WOOL,
                "⏳ Processing...",
                listOf(message, "Please wait...")
            )
            mainPane.addItem(GuiItem(loadingItem), x, y)
        }

        // Start loading animation
        startLoadingAnimation(loadingSlots)
        gui.update()
    }

    /**
     * Hide loading state and restore normal buttons
     */
    private fun hideLoadingState() {
        isLoading = false

        // Cancel loading animations
        animationTasks.forEach { it.cancel() }
        animationTasks.clear()

        // Restore original buttons
        setupQuickActions()
        gui.update()
    }

    /**
     * Start loading animation on specified slots
     */
    private fun startLoadingAnimation(slots: List<Pair<Int, Int>>) {
        val frames = listOf("⏳", "⌛", "⏳", "⌛")

        object : BukkitRunnable() {
            private var frameIndex = 0

            override fun run() {
                if (!isLoading) {
                    cancel()
                    return
                }

                val currentFrame = frames[frameIndex % frames.size]
                slots.forEach { (x, y) ->
                    val loadingItem = createMenuItem(
                        Material.YELLOW_WOOL,
                        "$currentFrame Processing...",
                        listOf("Transaction in progress", "Please wait...")
                    )
                    mainPane.addItem(GuiItem(loadingItem), x, y)
                }

                gui.update()
                frameIndex++
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 0L, LOADING_SPINNER_INTERVAL).also {
            animationTasks.add(it)
        }
    }

    /**
     * Animate balance change with smooth transitions
     */
    private fun animateBalanceChange(oldBalance: Int, newBalance: Int) {
        if (oldBalance == newBalance) return

        val difference = newBalance - oldBalance
        val isIncrease = difference > 0
        val steps = max(1, min(10, Math.abs(difference) / 50)) // More steps for larger changes
        val stepSize = difference / steps

        object : BukkitRunnable() {
            private var currentStep = 0
            private var animatedBalance = oldBalance

            override fun run() {
                if (currentStep >= steps) {
                    // Final update
                    currentBalance = newBalance
                    updateBalanceDisplay()
                    gui.update()
                    cancel()
                    return
                }

                animatedBalance += stepSize
                currentBalance = animatedBalance

                // Color coding for the balance display
                val color = when {
                    isIncrease -> NamedTextColor.GREEN
                    animatedBalance < oldBalance -> NamedTextColor.RED
                    else -> NamedTextColor.YELLOW
                }

                val balanceItem = createAnimatedBalanceItem(currentBalance, color, isIncrease)
                mainPane.addItem(GuiItem(balanceItem), 1, 0)

                gui.update()
                currentStep++
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 0L, BALANCE_UPDATE_INTERVAL).also {
            animationTasks.add(it)
        }
    }

    /**
     * Create animated balance display item
     */
    private fun createAnimatedBalanceItem(balance: Int, color: NamedTextColor, isIncrease: Boolean): ItemStack {
        val arrow = if (isIncrease) "⬆" else "⬇"
        val displayName = Component.text("$balance coins $arrow")
            .color(color)
            .decoration(TextDecoration.ITALIC, false)

        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta

        meta.displayName(displayName)
        meta.lore(listOf(
            Component.text("Current Balance")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }

    /**
     * Enhanced balance display with trend indicators
     */
    private fun updateBalanceDisplayEnhanced() {
        val trend = when {
            currentBalance > previousBalance -> "↗ Increasing"
            currentBalance < previousBalance -> "↘ Decreasing"
            else -> "→ Stable"
        }

        val balanceItem = createMenuItem(
            Material.EMERALD,
            getLocalizedString(LocalizationKeys.MENU_BANK_BALANCE_CURRENT, currentBalance.toString()),
            listOf(
                getLocalizedString(LocalizationKeys.MENU_BANK_BALANCE_TITLE),
                trend
            )
        )

        mainPane.addItem(GuiItem(balanceItem), 1, 0)
        previousBalance = currentBalance
    }

    /**
     * Update the player balance display
     */
    private fun updatePlayerBalanceDisplay() {
        val playerBalance = bankService.getPlayerBalance(player.uniqueId)
        val playerBalanceItem = createMenuItem(
            Material.GOLD_NUGGET,
            "Your Balance: $playerBalance",
            listOf("Available for deposit")
        )
        mainPane.addItem(GuiItem(playerBalanceItem), 0, 0)
    }

    /**
     * Enhanced button click feedback with sound and visual effects
     */
    private fun createEnhancedButton(material: Material, name: String, lore: List<String>,
                                   clickSound: Sound = BUTTON_CLICK_SOUND,
                                   clickAction: () -> Unit): GuiItem {
        val item = createMenuItem(material, name, lore)

        return GuiItem(item) { event ->
            event.isCancelled = true

            // Play click sound
            player.playSound(player.location, clickSound, 1.0f, 1.0f)

            // Visual feedback - briefly change item appearance
            val feedbackItem = createMenuItem(
                Material.LIGHT_BLUE_WOOL,
                "✓ $name",
                listOf("Processing...")
            )

            // Store original item for restoration
            val originalX = -1
            val originalY = -1

            // Find the slot of this item (simplified approach)
            mainPane.addItem(GuiItem(feedbackItem), originalX, originalY)
            gui.update()

            // Execute action after brief delay
            object : BukkitRunnable() {
                override fun run() {
                    clickAction()
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 2L)
        }
    }

    /**
     * Enhanced error handling with visual feedback
     */
    private fun showErrorFeedback(message: String, sound: Sound = ERROR_SOUND) {
        player.playSound(player.location, sound, 1.0f, 0.8f)

        // Flash red overlay on the balance display
        val errorItem = createMenuItem(
            Material.RED_WOOL,
            "⚠ Error",
            listOf(message, "Please try again")
        )

        mainPane.addItem(GuiItem(errorItem), 1, 0)

        // Auto-restore after 3 seconds
        object : BukkitRunnable() {
            override fun run() {
                updateBalanceDisplay()
                gui.update()
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 60L) // 3 seconds
    }

    /**
     * Enhanced success feedback with celebration effects
     */
    private fun showSuccessFeedback(message: String, amount: Int) {
        player.playSound(player.location, SUCCESS_SOUND, 1.0f, 1.5f)

        // Create celebration particles (visual effect)
        val location = player.location
        player.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, location, 20, 0.5, 0.5, 0.5, 0.1)

        // Show success message with animation
        val successItem = createMenuItem(
            Material.LIME_WOOL,
            "✓ Success!",
            listOf(message, "+$amount coins")
        )

        mainPane.addItem(GuiItem(successItem), 1, 0)

        // Auto-restore after 2 seconds
        object : BukkitRunnable() {
            override fun run() {
                updateBalanceDisplay()
                gui.update()
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 40L) // 2 seconds
    }

    /**
     * Check if Vault economy is available
     */
    private fun isEconomyAvailable(): Boolean {
        val bukkitService = bankService as? BankServiceBukkit
        return bukkitService?.isEconomyAvailable() ?: false
    }

    /**
     * Open custom amount input dialog using anvil GUI
     */
    private fun openCustomAmountDialog(type: TransactionType) {
        customAmountType = type
        awaitingCustomAmount = true

        // Close current inventory
        player.closeInventory()

        // Create anvil GUI for input
        val anvilGui = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text(
            if (type == TransactionType.DEPOSIT) "Enter Deposit Amount" else "Enter Withdrawal Amount"
        ))

        // Set up the anvil with a paper item for input
        val paper = ItemStack(Material.PAPER)
        val meta = paper.itemMeta
        meta?.displayName(Component.text("0"))
        paper.itemMeta = meta

        anvilGui.setItem(0, paper)

        // Open the anvil GUI
        player.openInventory(anvilGui)

        player.sendMessage(Component.text("§aEnter the amount you want to ${if (type == TransactionType.DEPOSIT) "deposit" else "withdraw"} in the anvil."))
        player.sendMessage(Component.text("§7Use numbers only (e.g., 5000, 10000)"))
    }

    /**
     * Process custom amount input
     */
    private fun processCustomAmount(amount: Int) {
        if (customAmountType == null) return

        try {
            when (customAmountType) {
                TransactionType.DEPOSIT -> {
                    handleDeposit(amount)
                }
                TransactionType.WITHDRAWAL -> {
                    handleWithdrawal(amount)
                }
                TransactionType.FEE -> {
                    // Fees are not manually entered by players
                    player.sendMessage(Component.text("§cCannot manually enter fees."))
                    return
                }
                TransactionType.DEDUCTION -> {
                    // Deductions are not manually entered by players
                    player.sendMessage(Component.text("§cCannot manually enter deductions."))
                    return
                }
                null -> {
                    // Should not happen due to null check above
                    return
                }
            }

            // Play success sound
            player.playSound(player.location, SUCCESS_SOUND, 1.0f, 1.0f)
        } catch (e: Exception) {
            player.sendMessage(Component.text("§cError processing transaction: ${e.message}"))
            player.playSound(player.location, ERROR_SOUND, 1.0f, 0.8f)
        } finally {
            customAmountType = null
            awaitingCustomAmount = false
        }
    }

    /**
     * Handle inventory click events for anvil GUI
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked != player || !awaitingCustomAmount) return

        if (event.inventory.type == InventoryType.ANVIL && event.slot == 2) {
            event.isCancelled = true

            // Get the result item (slot 2 is the result slot)
            val resultItem = event.inventory.getItem(2)
            if (resultItem != null && resultItem.type == Material.PAPER) {
                val displayName = resultItem.itemMeta?.displayName()
                if (displayName != null) {
                    try {
                        val amountText = displayName.toString().replace("§", "").trim()
                        val amount = amountText.toIntOrNull()

                        if (amount != null && amount > 0) {
                            // Close anvil GUI
                            player.closeInventory()

                            // Process the amount after a short delay to allow GUI to close
                            object : BukkitRunnable() {
                                override fun run() {
                                    processCustomAmount(amount)
                                }
                            }.runTaskLater(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 1L)
                        } else {
                            player.sendMessage(Component.text("§cInvalid amount. Please enter a positive number."))
                            player.playSound(player.location, ERROR_SOUND, 1.0f, 0.8f)
                        }
                    } catch (e: Exception) {
                        player.sendMessage(Component.text("§cError reading amount. Please try again."))
                        player.playSound(player.location, ERROR_SOUND, 1.0f, 0.8f)
                    }
                }
            }
        }
    }

    /**
     * Handle inventory close events
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player == player && awaitingCustomAmount) {
            awaitingCustomAmount = false
            customAmountType = null

            // Reopen the bank menu after a short delay
            object : BukkitRunnable() {
                override fun run() {
                    open()
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("LumaGuilds")!!, 1L)
        }
    }

    /**
     * Cleanup animation tasks when menu is closed
     */
    private fun cleanup() {
        animationTasks.forEach { it.cancel() }
        animationTasks.clear()

        // Unregister event listener
        try {
            InventoryClickEvent.getHandlerList().unregister(this)
            InventoryCloseEvent.getHandlerList().unregister(this)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }
}
