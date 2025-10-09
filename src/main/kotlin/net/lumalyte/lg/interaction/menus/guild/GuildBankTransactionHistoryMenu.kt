package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.CsvExportService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.lore
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
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Guild Bank Transaction History menu with pagination, filtering, and search
 */
class GuildBankTransactionHistoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val messageService: MessageService
) : Menu, KoinComponent {
    
    private var filter: TransactionFilter = TransactionFilter()

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val csvExportService: CsvExportService by inject()
    private val fileExportManager: FileExportManager by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var transactionPane: PaginatedPane
    private lateinit var filterPane: StaticPane

    // Transaction data
    private var allTransactions: List<BankTransaction> = emptyList()
    private var filteredTransactions: List<BankTransaction> = emptyList()

    // Pagination
    private val itemsPerPage = 10
    private var currentPage = 0

    init {
        loadTransactions()
        initializeGui()
    }

    override fun open() {
        updateTransactionDisplay()
        gui.show(player)
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_TITLE, guild.name))
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Create main pane for navigation and filters
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create filter pane
        filterPane = StaticPane(0, 1, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(filterPane)

        // Create transaction display pane (bottom 4 rows)
        transactionPane = PaginatedPane(0, 2, 9, 4, Pane.Priority.NORMAL)
        gui.addPane(transactionPane)

        setupNavigation()
        setupFilters()
        setupTransactionHistory()
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

        // Statistics button
        val statsItem = createMenuItem(
            Material.BOOK,
            getLocalizedString(LocalizationKeys.MENU_BANK_STATS_TITLE),
            listOf("View detailed bank statistics and analytics")
        )
        val statsGuiItem = GuiItem(statsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(statsGuiItem, 1, 0)

        // Member Contributions button
        val contributionsItem = createMenuItem(
            Material.PLAYER_HEAD,
            "Member Contributions",
            listOf("See who contributes and who freeloads", "Net deposits vs withdrawals")
        )
        val contributionsGuiItem = GuiItem(contributionsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(contributionsGuiItem, 2, 0)

        // Export button
        val exportItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Export to CSV",
            listOf("Download transaction data", "Secure & rate-limited")
        )
        val exportGuiItem = GuiItem(exportItem) { event ->
            event.isCancelled = true
            handleExport()
        }
        mainPane.addItem(exportGuiItem, 7, 0)

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
     * Setup filter controls
     */
    private fun setupFilters() {
        // Transaction type filter
        val typeFilterItem = createMenuItem(
            Material.HOPPER,
            "Filter by Type",
            listOf("Current: ${filter.typeFilter?.name ?: "All"}", "Click to change")
        )
        val typeFilterGuiItem = GuiItem(typeFilterItem) { event ->
            event.isCancelled = true
            openTypeFilterMenu()
        }
        filterPane.addItem(typeFilterGuiItem, 0, 0)

        // Member filter
        val memberFilterItem = createMenuItem(
            Material.PLAYER_HEAD,
            "Filter by Member",
            listOf("Current: ${filter.memberFilter ?: "All"}", "Click to change")
        )
        val memberFilterGuiItem = GuiItem(memberFilterItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Member filtering coming soon!")
        }
        filterPane.addItem(memberFilterGuiItem, 1, 0)

        // Date range filter
        val dateFilterItem = createMenuItem(
            Material.CLOCK,
            getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_FILTER_DATE),
            listOf("Current: All", "Click to change")
        )
        val dateFilterGuiItem = GuiItem(dateFilterItem) { event ->
            event.isCancelled = true
            openDateFilterMenu()
        }
        filterPane.addItem(dateFilterGuiItem, 2, 0)

        // Advanced filters
        val advancedFiltersItem = createMenuItem(
            Material.ENCHANTED_BOOK,
            "Advanced Filters",
            listOf(
                "Date range, amount range, search",
                "Current filters: ${getActiveFilterCount()} active"
            )
        )
        val advancedFiltersGuiItem = GuiItem(advancedFiltersItem) { event ->
            event.isCancelled = true
            openAdvancedFiltersMenu()
        }
        filterPane.addItem(advancedFiltersGuiItem, 3, 0)

        // Clear filters
        val clearItem = createMenuItem(
            Material.WATER_BUCKET,
            "Clear Filters",
            listOf("Remove all filters")
        )
        val clearGuiItem = GuiItem(clearItem) { event ->
            event.isCancelled = true
            filter = TransactionFilter()
            loadTransactions()
            updateTransactionDisplay()
            gui.update()
        }
        filterPane.addItem(clearGuiItem, 4, 0)

        // Page navigation
        updatePageNavigation()
    }

    /**
     * Setup transaction history display
     */
    private fun setupTransactionHistory() {
        val currentItems = getCurrentPageItems()

        if (currentItems.isEmpty()) {
            // TODO: Add to pane when API is resolved
            // val noTransactionsItem = createMenuItem(
            //     Material.BARRIER,
            //     getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_NO_TRANSACTIONS),
            //     listOf("Try adjusting your filters")
            // )
            // transactionPane.addItem(GuiItem(noTransactionsItem))
        } else {
            // TODO: Add transaction items when API is resolved
            // currentItems.forEach { transaction ->
            //     val transactionItem = createTransactionItem(transaction)
            //     transactionPane.addItem(GuiItem(transactionItem))
            // }
        }

        // Display transaction items in the main pane
        displayTransactionsInMainPane(mainPane)
    }

    /**
     * Display transactions in the main pane
     */
    private fun displayTransactionsInMainPane(mainPane: StaticPane) {
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredTransactions.size)

        for (i in startIndex until endIndex) {
            val transaction = filteredTransactions[i]
            val x = (i - startIndex) % 9
            val y = (i - startIndex) / 9

            val transactionItem = createTransactionItem(transaction)
            mainPane.addItem(GuiItem(transactionItem), x, y)
        }

        // Fill remaining slots with empty items
        for (i in endIndex - startIndex until itemsPerPage) {
            val x = i % 9
            val y = i / 9

            val emptyItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Transaction")
            mainPane.addItem(GuiItem(emptyItem), x, y)
        }
    }

    /**
     * Create an item representing a bank transaction
     */

    /**
     * Update page navigation controls
     */
    private fun updatePageNavigation() {
        // Simplified navigation for now
        val totalPages = (filteredTransactions.size + itemsPerPage - 1) / itemsPerPage

        if (totalPages > 1) {
            // Page indicator
            val pageItem = createMenuItem(
                Material.PAPER,
                "Page ${currentPage + 1}/$totalPages",
                listOf("${filteredTransactions.size} total transactions")
            )
            filterPane.addItem(GuiItem(pageItem), 6, 0)
        }
    }

    /**
     * Update the transaction display
     */
    private fun updateTransactionDisplay() {
        // Clear existing items (simplified approach)
        // For now, recreate the transaction display each time

        if (filteredTransactions.isEmpty()) {
            // Add a "no transactions" message to the filter pane temporarily
            val noTransactionsItem = createMenuItem(
                Material.BARRIER,
                getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_NO_TRANSACTIONS),
                listOf("Try adjusting your filters")
            )
            // Note: We'll handle this display differently for now
        } else {
            // Display logic will be handled in setupTransactionHistory
        }
    }

    /**
     * Get transactions for the current page
     */
    private fun getCurrentPageItems(): List<BankTransaction> {
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredTransactions.size)
        return filteredTransactions.subList(startIndex, endIndex)
    }

    /**
     * Create a transaction item for display
     */
    private fun createTransactionItem(transaction: BankTransaction): ItemStack {
        val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: "Unknown"
        val timestamp = formatTimestamp(transaction.timestamp)

        val material = when (transaction.type) {
            TransactionType.DEPOSIT -> Material.LIME_WOOL
            TransactionType.WITHDRAWAL -> Material.RED_WOOL
            TransactionType.FEE -> Material.ORANGE_WOOL
            TransactionType.DEDUCTION -> Material.GRAY_WOOL
        }

        val typeDisplay = when (transaction.type) {
            TransactionType.DEPOSIT -> "Deposit"
            TransactionType.WITHDRAWAL -> "Withdrawal"
            TransactionType.FEE -> "Fee"
            TransactionType.DEDUCTION -> "Deduction"
        }

        val amountDisplay = if (transaction.type == TransactionType.WITHDRAWAL) {
            "-$${transaction.amount}"
        } else {
            "+$${transaction.amount}"
        }

        return createMenuItem(
            material,
            "$typeDisplay - $amountDisplay",
            listOf(
                "By: $actorName",
                "Time: $timestamp",
                transaction.description ?: "",
                if (transaction.fee > 0) "Fee: $${transaction.fee}" else ""
            ).filter { it.isNotEmpty() }
        )
    }

    /**
     * Load and filter transactions
     */
    private fun loadTransactions() {
        // Load all transactions for this guild
        allTransactions = bankService.getTransactionHistory(guild.id, null)

        // Apply filters
        filteredTransactions = allTransactions.filter { transaction ->
            // Type filter
            if (filter.typeFilter != null && transaction.type != filter.typeFilter) {
                return@filter false
            }

            // Member filter (search by player name)
            if (filter.memberFilter != null) {
                val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name
                if (actorName == null || !actorName.contains(filter.memberFilter!!, ignoreCase = true)) {
                    return@filter false
                }
            }

            // Date range filter
            if (filter.dateFrom != null && transaction.timestamp.isBefore(filter.dateFrom)) {
                return@filter false
            }
            if (filter.dateTo != null && transaction.timestamp.isAfter(filter.dateTo)) {
                return@filter false
            }

            // Amount range filter
            val minAmount = filter.amountMin
            val maxAmount = filter.amountMax
            if (minAmount != null && transaction.amount < minAmount) {
                return@filter false
            }
            if (maxAmount != null && transaction.amount > maxAmount) {
                return@filter false
            }

            // Search query filter (search in description or actor name)
            if (filter.searchQuery != null) {
                val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: ""
                val description = transaction.description ?: ""
                val searchText = (actorName + " " + description).lowercase()
                if (!searchText.contains(filter.searchQuery!!.lowercase())) {
                    return@filter false
                }
            }

            true
        }

        // Apply sorting
        filteredTransactions = when (filter.sortBy) {
            SortField.TIMESTAMP -> {
                if (filter.sortOrder == SortOrder.ASCENDING) {
                    filteredTransactions.sortedBy { it.timestamp }
                } else {
                    filteredTransactions.sortedByDescending { it.timestamp }
                }
            }
            SortField.AMOUNT -> {
                if (filter.sortOrder == SortOrder.ASCENDING) {
                    filteredTransactions.sortedBy { it.amount }
                } else {
                    filteredTransactions.sortedByDescending { it.amount }
                }
            }
            SortField.TYPE -> {
                if (filter.sortOrder == SortOrder.ASCENDING) {
                    filteredTransactions.sortedBy { it.type.name }
                } else {
                    filteredTransactions.sortedByDescending { it.type.name }
                }
            }
            SortField.ACTOR -> {
                if (filter.sortOrder == SortOrder.ASCENDING) {
                    filteredTransactions.sortedBy { Bukkit.getOfflinePlayer(it.actorId).name ?: "" }
                } else {
                    filteredTransactions.sortedByDescending { Bukkit.getOfflinePlayer(it.actorId).name ?: "" }
                }
            }
        }

        // Reset to first page
        currentPage = 0
    }

    /**
     * Handle export functionality
     */
    private fun handleExport() {
        // Show loading message
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🔄 Generating CSV export... This may take a moment for large datasets.")

        // Get current filtered transactions
        val transactionsToExport = if (filteredTransactions.isEmpty()) {
            // If no filtered results, export all transactions
            bankService.getTransactionHistory(guild.id, null)
        } else {
            filteredTransactions
        }

        if (transactionsToExport.isEmpty()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No transactions to export!")
            return
        }

        // Start async export
        fileExportManager.exportTransactionHistoryAsync(player, transactionsToExport, guild.name) { result ->
            when (result) {
                is FileExportManager.ExportResult.Success -> {
                    val fileSizeKB = result.fileSize / 1024.0
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Export successful!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>📄 File: ${result.fileName}")
                    player.sendMessage("<green>📏 Size: ${String.format("%.1f", fileSizeKB)} KB")
                    AdventureMenuHelper.sendMessage(player, messageService, "<yellow>💡 Use <gold>/bellclaims download ${result.fileName} <yellow>to get the file")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>📝 File will be available for 15 minutes")
                }
                is FileExportManager.ExportResult.DiscordSuccess -> {
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ CSV sent to Discord!")
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>📄 ${result.message}")
                    AdventureMenuHelper.sendMessage(player, messageService, "<yellow>💡 Check your Discord server for the file attachment")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>📝 Files are uploaded instantly to your configured channel")
                }
                is FileExportManager.ExportResult.Error -> {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Export failed: ${result.message}")
                }
                is FileExportManager.ExportResult.RateLimited -> {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>⏰ ${result.message}")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>You can export up to 5 files per hour for security.")
                }
                is FileExportManager.ExportResult.FileTooLarge -> {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>📏 ${result.message}")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Try filtering your data to reduce file size.")
                }
            }
        }
    }

    /**
     * Open transaction type filter menu
     */
    private fun openTypeFilterMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Filter by Transaction Type"))

        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        // All types option
        val allItem = createMenuItem(
            Material.CHEST,
            "All Types",
            listOf("Show all transaction types", "Currently: ${if (filter.typeFilter == null) "Selected" else "Not selected"}")
        )
        val allGuiItem = GuiItem(allItem) { event ->
            event.isCancelled = true
            filter = filter.copy(typeFilter = null); open()
        }
        pane.addItem(allGuiItem, 0, 0)

        // Deposit option
        val depositItem = createMenuItem(
            Material.GREEN_WOOL,
            "Deposits Only",
            listOf("Show only deposits", "Currently: ${if (filter.typeFilter == TransactionType.DEPOSIT) "Selected" else "Not selected"}")
        )
        val depositGuiItem = GuiItem(depositItem) { event ->
            event.isCancelled = true
            filter = filter.copy(typeFilter = TransactionType.DEPOSIT); open()
        }
        pane.addItem(depositGuiItem, 2, 0)

        // Withdrawal option
        val withdrawalItem = createMenuItem(
            Material.RED_WOOL,
            "Withdrawals Only",
            listOf("Show only withdrawals", "Currently: ${if (filter.typeFilter == TransactionType.WITHDRAWAL) "Selected" else "Not selected"}")
        )
        val withdrawalGuiItem = GuiItem(withdrawalItem) { event ->
            event.isCancelled = true
            filter = filter.copy(typeFilter = TransactionType.WITHDRAWAL); open()
        }
        pane.addItem(withdrawalGuiItem, 4, 0)

        // Deduction option
        val deductionItem = createMenuItem(
            Material.ORANGE_WOOL,
            "Deductions Only",
            listOf("Show only deductions", "Currently: ${if (filter.typeFilter == TransactionType.DEDUCTION) "Selected" else "Not selected"}")
        )
        val deductionGuiItem = GuiItem(deductionItem) { event ->
            event.isCancelled = true
            filter = filter.copy(typeFilter = TransactionType.DEDUCTION); open()
        }
        pane.addItem(deductionGuiItem, 6, 0)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to History",
            listOf("Return to transaction history")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            open()
        }
        pane.addItem(backGuiItem, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Open advanced filters menu
     */
    private fun openAdvancedFiltersMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Advanced Filters"))

        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        var currentRow = 0

        // Date range filter
        val dateRangeItem = createMenuItem(
            Material.CLOCK,
            "Date Range",
            listOf(
                "From: ${filter.dateFrom?.let { formatDate(it) } ?: "Not set"}",
                "To: ${filter.dateTo?.let { formatDate(it) } ?: "Not set"}",
                "Click to set date range"
            )
        )
        val dateRangeGuiItem = GuiItem(dateRangeItem) { event ->
            event.isCancelled = true
            // TODO: Open date range picker
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Date range picker coming soon!")
        }
        pane.addItem(dateRangeGuiItem, 0, currentRow++)
        pane.addItem(dateRangeGuiItem, 1, currentRow++)

        // Amount range filter
        val amountRangeItem = createMenuItem(
            Material.GOLD_INGOT,
            "Amount Range",
            listOf(
                "Min: ${filter.amountMin ?: "Not set"}",
                "Max: ${filter.amountMax ?: "Not set"}",
                "Click to set amount range"
            )
        )
        val amountRangeGuiItem = GuiItem(amountRangeItem) { event ->
            event.isCancelled = true
            // TODO: Open amount range picker
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Amount range picker coming soon!")
        }
        pane.addItem(amountRangeGuiItem, 2, currentRow++)
        pane.addItem(amountRangeGuiItem, 3, currentRow++)

        // Search query filter
        val searchItem = createMenuItem(
            Material.BOOK,
            "Search Query",
            listOf(
                "Query: ${filter.searchQuery ?: "Not set"}",
                "Searches in player names and descriptions",
                "Click to set search query"
            )
        )
        val searchGuiItem = GuiItem(searchItem) { event ->
            event.isCancelled = true
            // TODO: Open search query input
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Search query input coming soon!")
        }
        pane.addItem(searchGuiItem, 4, currentRow++)
        pane.addItem(searchGuiItem, 5, currentRow++)

        // Sort options
        val sortItem = createMenuItem(
            Material.HOPPER,
            "Sort Options",
            listOf(
                "Sort by: ${filter.sortBy.name}",
                "Order: ${filter.sortOrder.name}",
                "Click to change sorting"
            )
        )
        val sortGuiItem = GuiItem(sortItem) { event ->
            event.isCancelled = true
            // TODO: Open sort options menu
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Sort options menu coming soon!")
        }
        pane.addItem(sortGuiItem, 6, currentRow++)
        pane.addItem(sortGuiItem, 7, currentRow++)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to History",
            listOf("Return to transaction history")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            open()
        }
        pane.addItem(backGuiItem, 8, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Format instant to readable date string
     */
    private fun formatDate(instant: Instant): String {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    /**
     * Get count of active filters
     */
    private fun getActiveFilterCount(): Int {
        var count = 0
        if (filter.typeFilter != null) count++
        if (filter.memberFilter != null) count++
        if (filter.dateFrom != null) count++
        if (filter.dateTo != null) count++
        if (filter.amountMin != null) count++
        if (filter.amountMax != null) count++
        if (filter.searchQuery != null) count++
        return count
    }

    /**
     * Open date filter menu
     */
    private fun openDateFilterMenu() {
        // TODO: Create date range selection menu
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Date filter menu coming soon!")
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Instant): String {
        val localDateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
        return localDateTime.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))
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

/**
 * Data class for transaction filtering options
 */
data class TransactionFilter(
    val typeFilter: TransactionType? = null,
    val memberFilter: String? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val amountMin: Int? = null,
    val amountMax: Int? = null,
    val searchQuery: String? = null,
    val sortBy: SortField = SortField.TIMESTAMP,
    val sortOrder: SortOrder = SortOrder.DESCENDING
)

enum class SortField {
    TIMESTAMP, AMOUNT, TYPE, ACTOR
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

