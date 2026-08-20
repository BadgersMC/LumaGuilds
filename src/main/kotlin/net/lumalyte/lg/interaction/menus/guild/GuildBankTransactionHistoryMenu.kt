package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.common.PluginKeys
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
import java.util.UUID

/**
 * Guild Bank Transaction History menu with pagination, filtering, and search
 */
class GuildBankTransactionHistoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private var filter: TransactionFilter = TransactionFilter()
) : Menu, KoinComponent, ChatInputHandler {

    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val chatInputListener: ChatInputListener by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var transactionPane: StaticPane
    private lateinit var filterPane: StaticPane

    // Transaction data
    private var allTransactions: List<BankTransaction> = emptyList()
    private var filteredTransactions: List<BankTransaction> = emptyList()

    // Pagination
    private val itemsPerPage = 10
    private var currentPage = 0

    // Chat input mode (search)
    private var inputMode: String? = null

    // Resolved actor name cache (built once per load to avoid repeated
    // Bukkit.getOfflinePlayer calls on the main thread)
    private var actorNames: Map<UUID, String> = emptyMap()

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
        gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, getLocalizedString("menu.bank.history.title", "guild" to guild.name)))
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // Create main pane for navigation and filters
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create filter pane
        filterPane = StaticPane(0, 1, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(filterPane)

        // Create transaction display pane (bottom 4 rows)
        transactionPane = StaticPane(0, 2, 9, 4, Pane.Priority.NORMAL)
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
            getLocalizedString("menu.bank.back_to_control_panel"),
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
            getLocalizedString("menu.bank.stats.title"),
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
            lang.raw("menu.bank.navigation.contributions_name"),
            listOf(
                lang.raw("menu.bank.navigation.contributions_description"),
                lang.raw("menu.bank.navigation.contributions_detail")
            )
        )
        val contributionsGuiItem = GuiItem(contributionsItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(contributionsGuiItem, 2, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString("menu.bank.close"),
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
        // Transaction type filter (click cycles through types)
        val typeFilterItem = createMenuItem(
            Material.HOPPER,
            getLocalizedString("menu.bank.history.filter.type"),
            listOf("Current: ${typeFilterLabel(filter.typeFilter)}", "Click to cycle")
        )
        val typeFilterGuiItem = GuiItem(typeFilterItem) { event ->
            event.isCancelled = true
            cycleTypeFilter()
        }
        filterPane.addItem(typeFilterGuiItem, 0, 0)

        // Member filter
        val memberFilterItem = createMenuItem(
            Material.PLAYER_HEAD,
            getLocalizedString("menu.bank.history.filter.member"),
            listOf("Current: ${filter.memberFilter ?: "All"}", "Click to select")
        )
        val memberFilterGuiItem = GuiItem(memberFilterItem) { event ->
            event.isCancelled = true
            openMemberFilterMenu()
        }
        filterPane.addItem(memberFilterGuiItem, 1, 0)

        // Date range filter (click cycles through presets)
        val dateFilterItem = createMenuItem(
            Material.CLOCK,
            getLocalizedString("menu.bank.history.filter.date"),
            listOf("Current: ${dateRangeLabel(filter.dateRange)}", "Click to cycle")
        )
        val dateFilterGuiItem = GuiItem(dateFilterItem) { event ->
            event.isCancelled = true
            cycleDateFilter()
        }
        filterPane.addItem(dateFilterGuiItem, 2, 0)

        // Search filter
        val searchItem = createMenuItem(
            Material.COMPASS,
            getLocalizedString("menu.bank.history.filter.search"),
            listOf(
                lang.legacy("menu.bank.history.filter.current", "value" to (filter.searchQuery ?: lang.raw("menu.bank.history.value.none"))),
                lang.raw("menu.bank.history.filter.search_description")
            )
        )
        val searchGuiItem = GuiItem(searchItem) { event ->
            event.isCancelled = true
            startSearchInput()
        }
        filterPane.addItem(searchGuiItem, 3, 0)

        // Clear filters
        val clearItem = createMenuItem(
            Material.WATER_BUCKET,
            getLocalizedString("menu.bank.history.filter.clear"),
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
    }

    /**
     * Setup transaction history display
     */
    private fun setupTransactionHistory() {
        updateTransactionDisplay()
    }

    /**
     * Update page navigation controls
     */
    private fun updatePageNavigation() {
        val totalPages = (filteredTransactions.size + itemsPerPage - 1) / itemsPerPage

        // Clear stale navigation controls (slots 6-8 of filter row)
        for (slot in 6..8) {
            filterPane.removeItem(slot, 0)
        }

        if (totalPages > 1) {
            // Previous page button
            if (currentPage > 0) {
                val prevItem = createMenuItem(
                    Material.ARROW,
                    getLocalizedString("menu.bank.history.page.previous"),
                    listOf("Go to page $currentPage")
                )
                val prevGuiItem = GuiItem(prevItem) { event ->
                    event.isCancelled = true
                    currentPage--
                    updateTransactionDisplay()
                    gui.update()
                }
                filterPane.addItem(prevGuiItem, 7, 0)
            }

            // Next page button
            if (currentPage < totalPages - 1) {
                val nextItem = createMenuItem(
                    Material.ARROW,
                    getLocalizedString("menu.bank.history.page.next"),
                    listOf("Go to page ${currentPage + 2}")
                )
                val nextGuiItem = GuiItem(nextItem) { event ->
                    event.isCancelled = true
                    currentPage++
                    updateTransactionDisplay()
                    gui.update()
                }
                filterPane.addItem(nextGuiItem, 8, 0)
            }

            // Page indicator
            val pageItem = createMenuItem(
                Material.PAPER,
                lang.legacy("menu.common.page.indicator", "current" to currentPage + 1, "total" to totalPages),
                listOf(lang.legacy("menu.bank.history.page.total_transactions", "count" to filteredTransactions.size))
            )
            filterPane.addItem(GuiItem(pageItem), 6, 0)
        }
    }

    /**
     * Update the transaction display
     */
    private fun updateTransactionDisplay() {
        // Refresh filter controls so labels/lore reflect the current filter.
        // StaticPane.addItem replaces the item at the same coordinates, so this
        // does not create duplicates.
        setupFilters()

        transactionPane.clear()

        val currentItems = getCurrentPageItems()

        if (currentItems.isEmpty()) {
            val noTransactionsItem = createMenuItem(
                Material.BARRIER,
                getLocalizedString("menu.bank.history.no_transactions"),
                listOf("Try adjusting your filters")
            )
            transactionPane.addItem(GuiItem(noTransactionsItem), 4, 1)
        } else {
            var slotIndex = 0
            currentItems.forEach { transaction ->
                val transactionItem = createTransactionItem(transaction)
                val row = slotIndex / 9
                val col = slotIndex % 9
                transactionPane.addItem(GuiItem(transactionItem), col, row)
                slotIndex++
            }
        }

        updatePageNavigation()
    }

    /**
     * Get transactions for the current page
     */
    private fun getCurrentPageItems(): List<BankTransaction> {
        if (filteredTransactions.isEmpty()) return emptyList()
        val startIndex = currentPage * itemsPerPage
        if (startIndex >= filteredTransactions.size) return emptyList()
        val endIndex = minOf(startIndex + itemsPerPage, filteredTransactions.size)
        return filteredTransactions.subList(startIndex, endIndex)
    }

    /**
     * Cycle the type filter to the next transaction type
     */
    private fun cycleTypeFilter() {
        val options: List<TransactionType?> = listOf(null) + TransactionType.entries
        val currentIndex = options.indexOf(filter.typeFilter)
        filter = filter.copy(typeFilter = options[(currentIndex + 1) % options.size])
        loadTransactions()
        updateTransactionDisplay()
        gui.update()
    }

    /**
     * Cycle the date range filter to the next preset
     */
    private fun cycleDateFilter() {
        val currentIndex = DateRangePreset.entries.indexOfFirst { it.key == filter.dateRange }
        val next = DateRangePreset.entries[(currentIndex + 1) % DateRangePreset.entries.size]
        filter = filter.copy(dateRange = next.key)
        loadTransactions()
        updateTransactionDisplay()
        gui.update()
    }

    /**
     * Open a slot-click selection menu of guild members to filter by
     */
    private fun openMemberFilterMenu() {
        // Resolve each member name once before sorting/rendering.
        val members = memberService.getGuildMembers(guild.id)
        val memberNames = members.associate { member ->
            member.playerId to (Bukkit.getOfflinePlayer(member.playerId).name ?: lang.raw("menu.common.unknown_player"))
        }
        val sortedMembers = members.sortedBy { memberNames[it.playerId]?.lowercase() }

        val memberGui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, getLocalizedString("menu.bank.history.title", "guild" to guild.name)))
        memberGui.setOnGlobalClick { event -> event.isCancelled = true }

        val memberPane = PaginatedPane(0, 0, 9, 5)
        memberGui.addPane(memberPane)

        val memberItems = mutableListOf<GuiItem>()

        // "All members" option to clear the filter
        val allItem = createMenuItem(
            Material.BARRIER,
            lang.raw("menu.bank.history.member_filter.all_name"),
            listOf(lang.raw("menu.bank.history.member_filter.all_description"))
        )
        memberItems += GuiItem(allItem) { event ->
            event.isCancelled = true
            filter = filter.copy(memberFilter = null)
            // Defer inventory transitions out of the click handler to avoid
            // client desync / items stuck on the cursor.
            Bukkit.getScheduler().runTask(PluginKeys.getPlugin(), Runnable {
                player.closeInventory()
                loadTransactions()
                updateTransactionDisplay()
                gui.update()
                gui.show(player)
            })
        }

        sortedMembers.forEach { member ->
            val name = memberNames[member.playerId] ?: lang.raw("menu.common.unknown_player")
            val memberItem = createMenuItem(
                Material.PLAYER_HEAD,
                name,
                listOf(lang.legacy("menu.bank.history.member_filter.select_member", "player" to name))
            )
            memberItems += GuiItem(memberItem) { event ->
                event.isCancelled = true
                filter = filter.copy(memberFilter = name)
                // Defer inventory transitions out of the click handler.
                Bukkit.getScheduler().runTask(PluginKeys.getPlugin(), Runnable {
                    player.closeInventory()
                    loadTransactions()
                    updateTransactionDisplay()
                    gui.update()
                    gui.show(player)
                })
            }
        }

        // Populate pages with members (45 per page = 9x5 grid)
        memberPane.populateWithGuiItems(memberItems)

        // Navigation row
        val navPane = StaticPane(0, 5, 9, 1)
        memberGui.addPane(navPane)

        val backItem = createMenuItem(
            Material.ARROW,
            getLocalizedString("menu.bank.back_to_control_panel"),
            listOf("Back to transaction history")
        )
        navPane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            // Defer inventory transition out of the click handler.
            Bukkit.getScheduler().runTask(PluginKeys.getPlugin(), Runnable {
                player.closeInventory()
                gui.show(player)
            })
        }, 0, 0)

        memberGui.show(player)
    }

    /**
     * Start chat input mode for the search query
     */
    private fun startSearchInput() {
        inputMode = "search"
        chatInputListener.startInputMode(player, this)
        player.sendMessage(lang.msg("menu.bank.history.search.prompt"))
    }

    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "search" -> {
                val query = input.trim()
                if (query.isEmpty()) {
                    player.sendMessage(lang.msg("menu.bank.history.search.empty"))
                } else {
                    filter = filter.copy(searchQuery = query)
                    loadTransactions()
                    updateTransactionDisplay()
                    gui.update()
                    player.sendMessage(lang.msg("menu.bank.history.search.success", "query" to query, "count" to filteredTransactions.size))
                }
            }
            else -> return
        }
        inputMode = null
    }

    override fun onCancel(player: Player) {
        inputMode = null
        player.sendMessage(lang.msg("menu.bank.history.search.cancelled"))
    }

    /**
     * Create a transaction item for display
     */
    private fun createTransactionItem(transaction: BankTransaction): ItemStack {
        val actorName = actorNames[transaction.actorId] ?: lang.raw("menu.bank.history.value.unknown")
        val timestamp = formatTimestamp(transaction.timestamp)

        val material = when (transaction.type) {
            TransactionType.DEPOSIT -> Material.LIME_WOOL
            TransactionType.WITHDRAWAL -> Material.RED_WOOL
            TransactionType.FEE -> Material.ORANGE_WOOL
            TransactionType.DEDUCTION -> Material.GRAY_WOOL
        }

        val typeDisplay = when (transaction.type) {
            TransactionType.DEPOSIT -> lang.raw("menu.bank.history.type.deposit")
            TransactionType.WITHDRAWAL -> lang.raw("menu.bank.history.type.withdrawal")
            TransactionType.FEE -> lang.raw("menu.bank.history.type.fee")
            TransactionType.DEDUCTION -> lang.raw("menu.bank.history.type.deduction")
        }

        val amountDisplay = if (transaction.type == TransactionType.WITHDRAWAL) {
            "-$${transaction.amount}"
        } else {
            "+$${transaction.amount}"
        }

        return createMenuItem(
            material,
            lang.legacy("menu.bank.history.transaction.name", "type" to typeDisplay, "amount" to amountDisplay),
            listOf(
                lang.legacy("menu.bank.history.transaction.actor", "player" to actorName),
                lang.legacy("menu.bank.history.transaction.time", "time" to timestamp),
                transaction.description ?: "",
                if (transaction.fee > 0) lang.legacy("menu.bank.history.transaction.fee", "amount" to transaction.fee) else ""
            ).filter { it.isNotEmpty() }
        )
    }

    /**
     * Load and filter transactions
     */
    private fun loadTransactions() {
        // Load all transactions for this guild
        allTransactions = bankService.getTransactionHistory(guild.id, null)

        // Resolve each distinct actor UUID once (getOfflinePlayer can block on a
        // profile lookup and both paths run on the main thread in response to a
        // menu click — do not call it per element).
        actorNames = allTransactions.map { it.actorId }.distinct().associateWith { actorId ->
            Bukkit.getOfflinePlayer(actorId).name ?: lang.raw("menu.bank.history.value.unknown")
        }

        // Compute date cutoff once for the date range filter
        val dateCutoff = dateRangeCutoff(filter.dateRange)

        filteredTransactions = allTransactions.filter { transaction ->
            // Type filter
            if (filter.typeFilter != null && transaction.type != filter.typeFilter) {
                return@filter false
            }

            // Member filter
            if (filter.memberFilter != null) {
                val actorName = actorNames[transaction.actorId]
                if (actorName != filter.memberFilter) {
                    return@filter false
                }
            }

            // Date range filter
            if (dateCutoff != null && transaction.timestamp.isBefore(dateCutoff)) {
                return@filter false
            }

            // Search query (matches member name or description)
            if (!filter.searchQuery.isNullOrBlank()) {
                val query = filter.searchQuery!!.lowercase()
                val actorName = actorNames[transaction.actorId]?.lowercase() ?: ""
                val description = transaction.description?.lowercase() ?: ""
                if (query !in actorName && query !in description) {
                    return@filter false
                }
            }

            true
        }

        // Reset to first page
        currentPage = 0
    }

    /**
     * Convert a date range preset to an Instant cutoff (null = all time)
     */
    private fun dateRangeCutoff(range: String?): Instant? {
        return DateRangePreset.fromKey(range)?.cutoffSeconds?.let { seconds ->
            Instant.now().minusSeconds(seconds)
        }
    }

    /**
     * Human-readable label for a date range preset
     */
    private fun dateRangeLabel(range: String?): String {
        return when (DateRangePreset.fromKey(range) ?: DateRangePreset.ALL) {
            DateRangePreset.ALL -> lang.raw("menu.bank.history.date_range.all")
            DateRangePreset.LAST_24_HOURS -> lang.raw("menu.bank.history.date_range.last_24_hours")
            DateRangePreset.LAST_7_DAYS -> lang.raw("menu.bank.history.date_range.last_7_days")
            DateRangePreset.LAST_30_DAYS -> lang.raw("menu.bank.history.date_range.last_30_days")
        }
    }

    /**
     * Human-readable label for a transaction type filter
     */
    private fun typeFilterLabel(type: TransactionType?): String {
        return when (type) {
            null -> lang.raw("menu.bank.history.value.all")
            TransactionType.DEPOSIT -> lang.raw("menu.bank.history.type.deposits")
            TransactionType.WITHDRAWAL -> lang.raw("menu.bank.history.type.withdrawals")
            TransactionType.FEE -> lang.raw("menu.bank.history.type.fees")
            TransactionType.DEDUCTION -> lang.raw("menu.bank.history.type.deductions")
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Instant): String {
        val localDateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
        return localDateTime.format(DateTimeFormatter.ofPattern(lang.raw("menu.bank.history.date_format")))
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
    private fun getLocalizedString(key: String, vararg placeholders: Pair<String, Any?>): String {
        return lang.legacy(key, *placeholders)
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

/**
 * Date range presets for the transaction history filter. Single source of truth
 * for the key (persisted in [TransactionFilter.dateRange]), the cutoff, and the
 * display label — adding or renaming a preset requires one edit.
 */
enum class DateRangePreset(val key: String?, val cutoffSeconds: Long?) {
    ALL(null, null),
    LAST_24_HOURS("24h", 24 * 60 * 60),
    LAST_7_DAYS("7d", 7 * 24 * 60 * 60),
    LAST_30_DAYS("30d", 30 * 24 * 60 * 60);

    companion object {
        fun fromKey(key: String?): DateRangePreset? = entries.firstOrNull { it.key == key }
    }
}
