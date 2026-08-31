package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui

import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit

import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.TransactionType
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
import org.bukkit.event.player.PlayerQuitEvent
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

    private val vaultInventoryManager: net.lumalyte.lg.infrastructure.vault.VaultInventoryManager by inject()
    private val lang: LangService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val guildService: net.lumalyte.lg.application.services.GuildService by inject()
    private val bankService: BankService by inject() // Keep for transaction history only
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    // Current balance cache (stored as Long to match VaultInventoryManager)
    private var currentBalance = 0L
    private var previousBalance = 0L

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
            player.sendMessage(lang.msg("menu.bank.unavailable.title"))
            player.sendMessage(lang.msg("menu.bank.unavailable.economy"))
            player.sendMessage(lang.msg("menu.bank.unavailable.install"))
            player.sendMessage(lang.msg("menu.bank.unavailable.contact"))

            // Play error sound
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            return
        }

        refreshBalance()

        // Register event listener for custom input
        Bukkit.getPluginManager().registerEvents(this, net.lumalyte.lg.common.PluginKeys.getPlugin())

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
        gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, getLocalizedTitle()))
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
            "menu.bank.quick.deposit.100",
            100,
            true
        )
        mainPane.addItem(deposit100Item, 0, 1)

        val deposit1000Item = createQuickActionItem(
            Material.LIME_WOOL,
            "menu.bank.quick.deposit.1000",
            1000,
            true
        )
        mainPane.addItem(deposit1000Item, 1, 1)

        val deposit10000Item = createQuickActionItem(
            Material.LIME_WOOL,
            "menu.bank.quick.deposit.10000",
            10000,
            true
        )
        mainPane.addItem(deposit10000Item, 2, 1)

        val depositAllItem = createQuickActionItem(
            Material.LIME_WOOL,
            "menu.bank.quick.deposit.all",
            -1, // Special value for all
            true
        )
        mainPane.addItem(depositAllItem, 3, 1)

        // Withdraw buttons (second row)
        val withdraw100Item = createQuickActionItem(
            Material.RED_WOOL,
            "menu.bank.quick.withdraw.100",
            100,
            false
        )
        mainPane.addItem(withdraw100Item, 0, 2)

        val withdraw1000Item = createQuickActionItem(
            Material.RED_WOOL,
            "menu.bank.quick.withdraw.1000",
            1000,
            false
        )
        mainPane.addItem(withdraw1000Item, 1, 2)

        val withdraw10000Item = createQuickActionItem(
            Material.RED_WOOL,
            "menu.bank.quick.withdraw.10000",
            10000,
            false
        )
        mainPane.addItem(withdraw10000Item, 2, 2)

        val withdrawAllItem = createQuickActionItem(
            Material.RED_WOOL,
            "menu.bank.quick.withdraw.all",
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
            getLocalizedString("menu.bank.custom.deposit"),
            listOf(lang.gui("menu.bank.custom.deposit_description"))
        )
        val depositGuiItem = GuiItem(customDepositItem) { event ->
            event.isCancelled = true
            openCustomAmountDialog(TransactionType.DEPOSIT)
        }
        mainPane.addItem(depositGuiItem, 6, 1)

        // Custom withdraw button
        val customWithdrawItem = createMenuItem(
            Material.RED_WOOL,
            getLocalizedString("menu.bank.custom.withdraw"),
            listOf(lang.gui("menu.bank.custom.withdraw_description"))
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
                getLocalizedString("menu.bank.history.title", "guild" to guild.name),
                listOf(getLocalizedString("menu.bank.history.no_transactions"))
            )
        } else {
            createMenuItem(
                Material.BOOK,
                getLocalizedString("menu.bank.history.title", "guild" to guild.name),
                listOf(
                    lang.gui("menu.bank.history.recent", "count" to transactions.size),
                    lang.gui("menu.bank.history.open_action")
                )
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
            getLocalizedString("menu.bank.stats.title"),
            listOf(lang.gui("menu.bank.navigation.statistics_description"))
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 7, 3)

        // Automation & Rewards button
        val automationItem = createMenuItem(
            Material.COMPARATOR,
            lang.gui("menu.bank.navigation.automation_name"),
            listOf(lang.gui("menu.bank.navigation.automation_description"), lang.gui("menu.bank.navigation.automation_features"))
        )
        val automationGuiItem = GuiItem(automationItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankAutomationMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(automationGuiItem, 5, 3)

        // Member Contributions button
        val contributionsItem = createMenuItem(
            Material.PLAYER_HEAD,
            lang.gui("menu.bank.navigation.contributions_name"),
            listOf(lang.gui("menu.bank.navigation.contributions_description"), lang.gui("menu.bank.navigation.contributions_detail"))
        )
        val contributionsGuiItem = GuiItem(contributionsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(contributionsGuiItem, 6, 3)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            getLocalizedString("menu.bank.back_to_control_panel"),
            listOf(lang.gui("menu.bank.navigation.back_description"))
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 8, 3)
    }

    /**
     * Create a quick action item for deposits/withdrawals
     */
    private fun createQuickActionItem(material: Material, localizationKey: String, amount: Int, isDeposit: Boolean): GuiItem {
        val displayName = getLocalizedString(localizationKey)
        val lore = if (amount == -1) {
            listOf(
                if (isDeposit) lang.gui("menu.bank.quick.description.deposit_all")
                else lang.gui("menu.bank.quick.description.withdraw_all")
            )
        } else {
            listOf(
                if (isDeposit) lang.gui("menu.bank.quick.description.deposit", "amount" to amount)
                else lang.gui("menu.bank.quick.description.withdraw", "amount" to amount)
            )
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
            showErrorFeedback(lang.gui("menu.bank.feedback.vault_unavailable"))
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

        val actualAmount: Int = if (amount == -1) {
            if (isDeposit) {
                // Get player's current balance from Vault economy
                bankService.getPlayerBalance(player.uniqueId).toInt()
            } else {
                // For withdraw all, use CURRENT real balance, not stale cache
                vaultInventoryManager.getGoldBalance(guild.id).toInt()
            }
        } else {
            amount
        }

        // Show loading state
        showLoadingState(
            if (isDeposit) lang.gui("menu.bank.processing.depositing")
            else lang.gui("menu.bank.processing.withdrawing")
        )

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
                            animateBalanceChange(currentBalance, vaultInventoryManager.getGoldBalance(guild.id))

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
                }.runTask(net.lumalyte.lg.common.PluginKeys.getPlugin())
            }
        }.runTaskAsynchronously(net.lumalyte.lg.common.PluginKeys.getPlugin())
    }

    /**
     * Handle deposit operation with physical gold items
     */
    private fun handleDeposit(amount: Int): Boolean {
        // Check DEPOSIT_TO_BANK permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DEPOSIT_TO_BANK)) {
            val message = lang.gui("menu.bank.feedback.deposit_permission_denied")
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_permission_denied"))
            showErrorFeedback(message)
            return false
        }

        return try {
            // Count available gold in player inventory
            var totalNuggets = 0L
            val goldItems = mutableListOf<Pair<Int, Int>>() // slot, value pairs

            for (i in 0 until player.inventory.size) {
                val item = player.inventory.getItem(i) ?: continue
                val value = net.lumalyte.lg.application.utilities.GoldBalanceButton.calculateGoldValue(item)
                if (value > 0) {
                    totalNuggets += value
                    goldItems.add(Pair(i, value.toInt()))
                }
            }

            if (totalNuggets < amount) {
                val message = lang.gui("menu.bank.feedback.insufficient_gold", "balance" to totalNuggets, "amount" to amount)
                player.sendMessage(lang.msg("menu.bank.feedback.insufficient_gold", "balance" to totalNuggets, "amount" to amount))
                showErrorFeedback(message)
                return false
            }

            // Remove gold items from player inventory
            var remaining = amount
            for ((slot, value) in goldItems) {
                if (remaining <= 0) break

                val toRemove = minOf(remaining, value)
                val item = player.inventory.getItem(slot) ?: continue

                if (toRemove >= value) {
                    // Remove entire stack
                    player.inventory.setItem(slot, null)
                    remaining -= value
                } else {
                    // Partially remove - need to break down the item
                    player.inventory.setItem(slot, null)
                    remaining -= value

                    // Give back change if needed
                    if (value > toRemove) {
                        val change = value - toRemove
                        val changeItems = net.lumalyte.lg.application.utilities.GoldBalanceButton.convertToItems(change.toLong())
                        for (changeItem in changeItems) {
                            player.inventory.addItem(changeItem)
                        }
                    }
                }
            }

            // Deposit to guild vault (adds to virtual gold balance)
            val vaultInventoryManager: net.lumalyte.lg.infrastructure.vault.VaultInventoryManager by inject()
            val newBalance = vaultInventoryManager.depositGold(guild.id, player.uniqueId, amount.toLong())
            vaultInventoryManager.forceFlush(guild.id)

            // Reload guild to get updated state
            guild = guildService.getGuild(guild.id) ?: guild

            val message = lang.msg(
                "menu.bank.feedback.deposit_success",
                "amount" to amount,
            ).color(NamedTextColor.GREEN)
            player.sendMessage(message)
            showSuccessFeedback(lang.gui("menu.bank.feedback.deposit_overlay"), amount.toLong())
            true
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            val errorMessage = lang.gui("menu.bank.feedback.deposit_error", "reason" to e.message)
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_error", "reason" to e.message))
            showErrorFeedback(errorMessage)
            false
        }
    }

    /**
     * Handle withdrawal operation with physical gold items
     */
    private fun handleWithdrawal(amount: Int): Boolean {
        // Check WITHDRAW_FROM_BANK permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
            val message = lang.gui("menu.bank.feedback.withdraw_permission_denied")
            player.sendMessage(lang.msg("menu.bank.feedback.withdraw_permission_denied"))
            showErrorFeedback(message)
            return false
        }

        // Guard against zero/negative amount (avoids voiding gold via stale cached balance)
        if (amount <= 0) {
            val message = lang.gui("menu.bank.feedback.withdraw_no_amount")
            player.sendMessage(lang.msg("menu.bank.feedback.withdraw_no_amount"))
            showErrorFeedback(message)
            return false
        }

        return try {
            // Get vault inventory manager
            val vaultInventoryManager: net.lumalyte.lg.infrastructure.vault.VaultInventoryManager by inject()

            // Check if guild has sufficient gold
            val currentVaultBalance = vaultInventoryManager.getGoldBalance(guild.id)
            if (currentVaultBalance < amount) {
                val message = lang.gui("menu.bank.feedback.insufficient_vault_gold", "balance" to currentVaultBalance, "amount" to amount)
                player.sendMessage(lang.msg("menu.bank.feedback.insufficient_vault_gold", "balance" to currentVaultBalance, "amount" to amount))
                showErrorFeedback(message)
                return false
            }

            // Withdraw from guild vault (deducts from virtual gold balance)
            val newBalance = vaultInventoryManager.withdrawGold(guild.id, player.uniqueId, amount.toLong())
            if (newBalance == -1L) {
                val message = lang.gui("menu.bank.feedback.vault_withdraw_failed")
                player.sendMessage(lang.msg("menu.bank.feedback.vault_withdraw_failed"))
                showErrorFeedback(message)
                return false
            }

            vaultInventoryManager.forceFlush(guild.id)

            // Give physical gold items to player
            val goldItems = net.lumalyte.lg.application.utilities.GoldBalanceButton.convertToItems(amount.toLong())
            val leftoverItems = mutableListOf<ItemStack>()

            for (goldItem in goldItems) {
                val leftover = player.inventory.addItem(goldItem)
                leftoverItems.addAll(leftover.values)
            }

            if (leftoverItems.isNotEmpty()) {
                // Inventory full - drop items at player's feet
                leftoverItems.forEach { item ->
                    player.world.dropItemNaturally(player.location, item)
                }
                player.sendMessage(lang.msg("menu.bank.feedback.inventory_full"))
            }

            // Reload guild to get updated state
            guild = guildService.getGuild(guild.id) ?: guild

            val message = lang.msg(
                "menu.bank.feedback.withdraw_success",
                "amount" to amount,
            ).color(NamedTextColor.GREEN)
            player.sendMessage(message)
            showSuccessFeedback(lang.gui("menu.bank.feedback.withdraw_overlay"), -amount.toLong())
            true
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            val errorMessage = lang.gui("menu.bank.feedback.withdraw_error", "reason" to e.message)
            player.sendMessage(lang.msg("menu.bank.feedback.withdraw_error", "reason" to e.message))
            showErrorFeedback(errorMessage)
            false
        }
    }

    /**
     * Create a transaction history item
     */
    private fun createTransactionItem(transaction: BankTransaction): ItemStack {
        val transactionType = when (transaction.type) {
            TransactionType.DEPOSIT -> getLocalizedString("menu.bank.transaction.deposit")
            TransactionType.WITHDRAWAL -> getLocalizedString("menu.bank.transaction.withdrawal")
            TransactionType.FEE -> getLocalizedString("menu.bank.transaction.fee")
            TransactionType.DEDUCTION -> getLocalizedString("menu.bank.transaction.deduction")
        }

        val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: lang.gui("menu.bank.transaction.unknown_actor")
        val timestamp = transaction.timestamp.toString().substring(0, 16) // Simple date formatting

        val material = when (transaction.type) {
            TransactionType.DEPOSIT -> Material.LIME_WOOL
            TransactionType.WITHDRAWAL -> Material.RED_WOOL
            TransactionType.FEE -> Material.ORANGE_WOOL
            TransactionType.DEDUCTION -> Material.GRAY_WOOL
        }

        return createMenuItem(
            material,
            lang.gui("menu.bank.transaction.name", "type" to transactionType, "amount" to transaction.amount),
            buildList {
                add(lang.gui("menu.bank.transaction.actor", "actor" to actorName))
                add(lang.gui("menu.bank.transaction.time", "time" to timestamp))
                if (transaction.fee > 0) {
                    add(lang.gui("menu.bank.transaction.fee_amount", "fee" to transaction.fee))
                }
            }
        )
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: Component, lore: List<Component>): ItemStack {
        val item = ItemStack.of(material)
        val meta = item.itemMeta

        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        if (lore.isNotEmpty()) {
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Get localized string with optional parameters
     */
    private fun getLocalizedString(key: String, vararg placeholders: Pair<String, Any?>): Component {
        return lang.gui(key, *placeholders)
    }

    /**
     * Get localized title for the GUI
     */
    private fun getLocalizedTitle(): String {
        return lang.guiTitle("menu.bank.title", "guild" to guild.name)
    }

    /**
     * Load current balance from vault gold balance
     */
    private fun loadBalance() {
        currentBalance = vaultInventoryManager.getGoldBalance(guild.id)
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
    private fun showLoadingState(message: Component) {
        isLoading = true

        // Replace action buttons with loading indicators
        val loadingSlots = listOf(
            Pair(0, 1), Pair(1, 1), Pair(2, 1), Pair(3, 1), // Deposit buttons
            Pair(0, 2), Pair(1, 2), Pair(2, 2), Pair(3, 2)  // Withdraw buttons
        )

        loadingSlots.forEach { (x, y) ->
            val loadingItem = createMenuItem(
                Material.YELLOW_WOOL,
                lang.gui("menu.bank.processing.name"),
                listOf(message, lang.gui("menu.bank.processing.wait"))
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
                        lang.gui("menu.bank.processing.animated", "frame" to currentFrame),
                        listOf(lang.gui("menu.bank.processing.description"), lang.gui("menu.bank.processing.wait"))
                    )
                    mainPane.addItem(GuiItem(loadingItem), x, y)
                }

                gui.update()
                frameIndex++
            }
        }.runTaskTimer(net.lumalyte.lg.common.PluginKeys.getPlugin(), 0L, LOADING_SPINNER_INTERVAL).also {
            animationTasks.add(it)
        }
    }

    /**
     * Animate balance change with smooth transitions
     */
    private fun animateBalanceChange(oldBalance: Long, newBalance: Long) {
        if (oldBalance == newBalance) return

        val difference = newBalance - oldBalance
        val isIncrease = difference > 0
        val steps = max(1, min(10, Math.abs(difference.toInt()) / 50)) // More steps for larger changes
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
        }.runTaskTimer(net.lumalyte.lg.common.PluginKeys.getPlugin(), 0L, BALANCE_UPDATE_INTERVAL).also {
            animationTasks.add(it)
        }
    }

    /**
     * Create animated balance display item
     */
    private fun createAnimatedBalanceItem(balance: Long, color: NamedTextColor, isIncrease: Boolean): ItemStack {
        val arrow = if (isIncrease) "⬆" else "⬇"
        val displayName = lang.gui("menu.bank.balance.animated", "balance" to balance, "arrow" to arrow)
            .color(color)
            .decoration(TextDecoration.ITALIC, false)

        val item = ItemStack.of(Material.EMERALD)
        val meta = item.itemMeta

        meta.displayName(displayName)
        meta.lore(listOf(
            lang.gui("menu.bank.balance.title").decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta
        return item
    }

    /**
     * Enhanced balance display with trend indicators
     */
    private fun updateBalanceDisplayEnhanced() {
        val trend = when {
            currentBalance > previousBalance -> lang.gui("menu.bank.balance.trend.increasing")
            currentBalance < previousBalance -> lang.gui("menu.bank.balance.trend.decreasing")
            else -> lang.gui("menu.bank.balance.trend.stable")
        }

        val balanceItem = createMenuItem(
            Material.EMERALD,
            getLocalizedString("menu.bank.balance.current", "balance" to currentBalance),
            listOf(
                getLocalizedString("menu.bank.balance.title"),
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
            lang.gui("menu.bank.balance.player", "balance" to playerBalance),
            listOf(lang.gui("menu.bank.balance.player_description"))
        )
        mainPane.addItem(GuiItem(playerBalanceItem), 0, 0)
    }

    /**
     * Enhanced button click feedback with sound and visual effects
     */
    private fun createEnhancedButton(material: Material, name: Component, lore: List<Component>,
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
                lang.gui("menu.bank.processing.button", "name" to name),
                listOf(lang.gui("menu.bank.processing.short"))
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
            }.runTaskLater(net.lumalyte.lg.common.PluginKeys.getPlugin(), 2L)
        }
    }

    /**
     * Enhanced error handling with visual feedback
     */
    private fun showErrorFeedback(message: Component, sound: Sound = ERROR_SOUND) {
        player.playSound(player.location, sound, 1.0f, 0.8f)

        // Flash red overlay on the balance display
        val errorItem = createMenuItem(
            Material.RED_WOOL,
            lang.gui("menu.bank.overlay.error.name"),
            listOf(message, lang.gui("menu.bank.overlay.error.retry"))
        )

        mainPane.addItem(GuiItem(errorItem), 1, 0)

        // Auto-restore after 3 seconds
        object : BukkitRunnable() {
            override fun run() {
                updateBalanceDisplay()
                gui.update()
            }
        }.runTaskLater(net.lumalyte.lg.common.PluginKeys.getPlugin(), 60L) // 3 seconds
    }

    /**
     * Enhanced success feedback with celebration effects
     */
    private fun showSuccessFeedback(message: Component, amount: Long) {
        player.playSound(player.location, SUCCESS_SOUND, 1.0f, 1.5f)

        // Create celebration particles (visual effect)
        val location = player.location
        player.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, location, 20, 0.5, 0.5, 0.5, 0.1)

        // Show success message with animation
        val successItem = createMenuItem(
            Material.LIME_WOOL,
            lang.gui("menu.bank.overlay.success.name"),
            listOf(message, lang.gui("menu.bank.overlay.success.amount", "amount" to amount))
        )

        mainPane.addItem(GuiItem(successItem), 1, 0)

        // Auto-restore after 2 seconds
        object : BukkitRunnable() {
            override fun run() {
                updateBalanceDisplay()
                gui.update()
            }
        }.runTaskLater(net.lumalyte.lg.common.PluginKeys.getPlugin(), 40L) // 2 seconds
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
        val anvilTitle = if (type == TransactionType.DEPOSIT) {
            lang.msg("menu.bank.anvil.deposit_title")
        } else {
            lang.msg("menu.bank.anvil.withdraw_title")
        }
        val anvilGui = Bukkit.createInventory(null, InventoryType.ANVIL, anvilTitle)

        // Set up the anvil with a paper item for input
        val paper = ItemStack.of(Material.PAPER)
        val meta = paper.itemMeta
        meta?.displayName(Component.text("0"))
        paper.itemMeta = meta

        anvilGui.setItem(0, paper)

        // Open the anvil GUI
        player.openInventory(anvilGui)

        if (type == TransactionType.DEPOSIT) {
            player.sendMessage(lang.msg("menu.bank.anvil.deposit_prompt"))
        } else {
            player.sendMessage(lang.msg("menu.bank.anvil.withdraw_prompt"))
        }
        player.sendMessage(lang.msg("menu.bank.anvil.numbers_only"))
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
                    player.sendMessage(lang.msg("menu.bank.feedback.manual_fee_denied"))
                    return
                }
                TransactionType.DEDUCTION -> {
                    // Deductions are not manually entered by players
                    player.sendMessage(lang.msg("menu.bank.feedback.manual_deduction_denied"))
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
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.bank.feedback.processing_error", "reason" to e.message))
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
                        val amountText = displayName.toString().replace('\u00A7'.toString(), "").trim()
                        val amount = amountText.toIntOrNull()

                        if (amount != null && amount > 0) {
                            // Close anvil GUI
                            player.closeInventory()

                            // Process the amount after a short delay to allow GUI to close
                            object : BukkitRunnable() {
                                override fun run() {
                                    processCustomAmount(amount)
                                }
                            }.runTaskLater(net.lumalyte.lg.common.PluginKeys.getPlugin(), 1L)
                        } else {
                            player.sendMessage(lang.msg("menu.bank.feedback.positive_amount_required"))
                            player.playSound(player.location, ERROR_SOUND, 1.0f, 0.8f)
                        }
                    } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                        player.sendMessage(lang.msg("menu.bank.feedback.amount_read_error"))
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
            }.runTaskLater(net.lumalyte.lg.common.PluginKeys.getPlugin(), 1L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player.uniqueId != player.uniqueId) return

        // Player disconnected - cleanup
        cleanup()
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
            PlayerQuitEvent.getHandlerList().unregister(this)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Ignore if already unregistered
        }
    }
}

