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

/**
 * Guild Bank Transaction History menu with pagination, filtering, and search
 */
class GuildBankTransactionHistoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private var filter: TransactionFilter = TransactionFilter()
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val csvExportService: CsvExportService by inject()
    private val fileExportManager: FileExportManager by inject()

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

    override fun passData(data: Any?) {
        // Handle filter updates
        if (data is TransactionFilter) {
            filter = data
            loadTransactions()
            updateTransactionDisplay()
            gui.update()
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_TITLE, guild.name))
        gui.setOnGlobalClick { event -> event.isCancelled = true }

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
            menuNavigator.openMenu(GuildBankMenu(menuNavigator, player, guild))
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
            menuNavigator.openMenu(GuildBankStatisticsMenu(menuNavigator, player, guild))
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
            menuNavigator.openMenu(GuildMemberContributionsMenu(menuNavigator, player, guild))
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
            openMemberFilterMenu()
        }
        filterPane.addItem(memberFilterGuiItem, 1, 0)

        // Date range filter
        val dateFilterItem = createMenuItem(
            Material.CLOCK,
            getLocalizedString(LocalizationKeys.MENU_BANK_HISTORY_FILTER_DATE),
            listOf("Current: ${filter.dateRange ?: "All"}", "Click to change")
        )
        val dateFilterGuiItem = GuiItem(dateFilterItem) { event ->
            event.isCancelled = true
            openDateFilterMenu()
        }
        filterPane.addItem(dateFilterGuiItem, 2, 0)

        // Search filter
        val searchItem = createMenuItem(
            Material.COMPASS,
            "Search Transactions",
            listOf("Click to search")
        )
        val searchGuiItem = GuiItem(searchItem) { event ->
            event.isCancelled = true
            // TODO: Open search dialog
            player.sendMessage("§eSearch functionality coming soon!")
        }
        filterPane.addItem(searchGuiItem, 3, 0)

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

        // For now, show a placeholder message
        player.sendMessage("§eTransaction history display coming soon!")
    }

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

            // Member filter
            if (filter.memberFilter != null) {
                val actorName = Bukkit.getOfflinePlayer(transaction.actorId).name
                if (actorName != filter.memberFilter) {
                    return@filter false
                }
            }

            // Date range filter
            if (filter.dateRange != null) {
                // TODO: Implement date range filtering
            }

            true
        }

        // Reset to first page
        currentPage = 0
    }

    /**
     * Handle export functionality
     */
    private fun handleExport() {
        // Show loading message
        player.sendMessage("§e🔄 Generating CSV export... This may take a moment for large datasets.")

        // Get current filtered transactions
        val transactionsToExport = if (filteredTransactions.isEmpty()) {
            // If no filtered results, export all transactions
            bankService.getTransactionHistory(guild.id, null)
        } else {
            filteredTransactions
        }

        if (transactionsToExport.isEmpty()) {
            player.sendMessage("§c❌ No transactions to export!")
            return
        }

        // Start async export
        fileExportManager.exportTransactionHistoryAsync(player, transactionsToExport, guild.name) { result ->
            when (result) {
                is FileExportManager.ExportResult.Success -> {
                    val fileSizeKB = result.fileSize / 1024.0
                    player.sendMessage("§a✅ Export successful!")
                    player.sendMessage("§a📄 File: ${result.fileName}")
                    player.sendMessage("§a📏 Size: ${String.format("%.1f", fileSizeKB)} KB")
                    player.sendMessage("§e💡 Use §6/bellclaims download ${result.fileName} §eto get the file")
                    player.sendMessage("§7📝 File will be available for 15 minutes")
                }
                is FileExportManager.ExportResult.DiscordSuccess -> {
                    player.sendMessage("§a✅ CSV sent to Discord!")
                    player.sendMessage("§a📄 ${result.message}")
                    player.sendMessage("§e💡 Check your Discord server for the file attachment")
                    player.sendMessage("§7📝 Files are uploaded instantly to your configured channel")
                }
                is FileExportManager.ExportResult.Error -> {
                    player.sendMessage("§c❌ Export failed: ${result.message}")
                }
                is FileExportManager.ExportResult.RateLimited -> {
                    player.sendMessage("§c⏰ ${result.message}")
                    player.sendMessage("§7You can export up to 5 files per hour for security.")
                }
                is FileExportManager.ExportResult.FileTooLarge -> {
                    player.sendMessage("§c📏 ${result.message}")
                    player.sendMessage("§7Try filtering your data to reduce file size.")
                }
            }
        }
    }

    /**
     * Open transaction type filter menu
     */
    private fun openTypeFilterMenu() {
        // TODO: Create filter selection menu
        player.sendMessage("§eType filter menu coming soon!")
    }

    /**
     * Open member filter menu
     */
    private fun openMemberFilterMenu() {
        // TODO: Create member selection menu
        player.sendMessage("§eMember filter menu coming soon!")
    }

    /**
     * Open date filter menu
     */
    private fun openDateFilterMenu() {
        // TODO: Create date range selection menu
        player.sendMessage("§eDate filter menu coming soon!")
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
    val dateRange: String? = null,
    val searchQuery: String? = null
)
