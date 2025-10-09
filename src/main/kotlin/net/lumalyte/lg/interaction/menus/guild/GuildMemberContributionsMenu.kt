package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.CsvExportService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.MemberContribution
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Guild Member Contributions menu showing net contributions for each member
 */
class GuildMemberContributionsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
, private val messageService: MessageService) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val csvExportService: CsvExportService by inject()
    private val fileExportManager: FileExportManager by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var contributionsPane: StaticPane
    private lateinit var filterPane: StaticPane

    // Data
    private var allContributions: List<MemberContribution> = emptyList()
    private var filteredContributions: List<MemberContribution> = emptyList()
    private var sortBy: SortBy = SortBy.NET_CONTRIBUTION_DESC

    // Pagination
    private val itemsPerPage = 8
    private var currentPage = 0

    init {
        loadContributions()
        initializeGui()
    }

    override fun open() {
        updateContributionsDisplay()
        gui.show(player)
    }/**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>${guild.name} - Member Contributions"))
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Create main pane for navigation
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create filter pane
        filterPane = StaticPane(0, 1, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(filterPane)

        // Create contributions display pane (bottom 4 rows)
        contributionsPane = StaticPane(0, 2, 9, 4, Pane.Priority.NORMAL)
        gui.addPane(contributionsPane)

        setupNavigation()
        setupFilters()
        setupContributionsDisplay()
    }

    /**
     * Setup navigation buttons
     */
    private fun setupNavigation() {
        // Back to transaction history button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Transaction History",
            listOf("Return to transaction history")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Export button
        val exportItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Export to CSV",
            listOf("Download member data", "Secure & rate-limited")
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
     * Setup filter buttons
     */
    private fun setupFilters() {
        // Sort by Net Contribution (default)
        val netContributionItem = createMenuItem(
            Material.GOLD_INGOT,
            "Sort by Net Contribution",
            listOf("Shows biggest contributors first", "Click to toggle ascending/descending")
        )
        val netContributionGuiItem = GuiItem(netContributionItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.NET_CONTRIBUTION_DESC)
        }
        filterPane.addItem(netContributionGuiItem, 0, 0)

        // Sort by Total Deposits
        val depositsItem = createMenuItem(
            Material.EMERALD,
            "Sort by Deposits",
            listOf("Shows biggest depositors first")
        )
        val depositsGuiItem = GuiItem(depositsItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.DEPOSITS_DESC)
        }
        filterPane.addItem(depositsGuiItem, 1, 0)

        // Sort by Total Withdrawals
        val withdrawalsItem = createMenuItem(
            Material.REDSTONE,
            "Sort by Withdrawals",
            listOf("Shows biggest withdrawers first")
        )
        val withdrawalsGuiItem = GuiItem(withdrawalsItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.WITHDRAWALS_DESC)
        }
        filterPane.addItem(withdrawalsGuiItem, 2, 0)

        // Show only freeloaders
        val freeloadersItem = createMenuItem(
            Material.RED_WOOL,
            "Show Freeloaders Only",
            listOf("Members who withdraw more than deposit")
        )
        val freeloadersGuiItem = GuiItem(freeloadersItem) { event ->
            event.isCancelled = true
            filterByStatus(MemberContribution.ContributionStatus.FREELOADER)
        }
        filterPane.addItem(freeloadersGuiItem, 3, 0)

        // Show all members
        val allMembersItem = createMenuItem(
            Material.GREEN_WOOL,
            "Show All Members",
            listOf("Clear filters, show everyone")
        )
        val allMembersGuiItem = GuiItem(allMembersItem) { event ->
            event.isCancelled = true
            showAllContributions()
        }
        filterPane.addItem(allMembersGuiItem, 4, 0)
    }

    /**
     * Setup contributions display
     */
    private fun setupContributionsDisplay() {
        val currentItems = getCurrentPageItems()

        contributionsPane.clear()

        if (currentItems.isEmpty()) {
            val noContributionsItem = createMenuItem(
                Material.BARRIER,
                "No member contributions found",
                listOf("Try clearing filters")
            )
            contributionsPane.addItem(GuiItem(noContributionsItem), 4, 1)
        } else {
            var slotIndex = 0
            currentItems.forEach { contribution ->
                val contributionItem = createContributionItem(contribution)
                val row = slotIndex / 9
                val col = slotIndex % 9
                contributionsPane.addItem(GuiItem(contributionItem), col, row)
                slotIndex++
            }
        }

        // Page navigation
        updatePageNavigation()
    }

    /**
     * Create a contribution item for display
     */
    private fun createContributionItem(contribution: MemberContribution): ItemStack {
        val playerName = contribution.playerName ?: "Unknown Player"
        val netContribution = contribution.netContribution

        val color = when (contribution.contributionStatus) {
            MemberContribution.ContributionStatus.CONTRIBUTOR -> NamedTextColor.GREEN
            MemberContribution.ContributionStatus.FREELOADER -> NamedTextColor.RED
            MemberContribution.ContributionStatus.BREAK_EVEN_CONTRIBUTOR -> NamedTextColor.YELLOW
            MemberContribution.ContributionStatus.NEUTRAL -> NamedTextColor.GRAY
        }

        val statusText = when (contribution.contributionStatus) {
            MemberContribution.ContributionStatus.CONTRIBUTOR -> "Contributor"
            MemberContribution.ContributionStatus.FREELOADER -> "Freeloader"
            MemberContribution.ContributionStatus.BREAK_EVEN_CONTRIBUTOR -> "Break-even"
            MemberContribution.ContributionStatus.NEUTRAL -> "No Activity"
        }

        val lore = mutableListOf<String>()
        lore.add("<gray>Deposits: <white>${contribution.totalDeposits}")
        lore.add("<gray>Withdrawals: <white>${contribution.totalWithdrawals}")
        lore.add("<gray>Net Contribution: §${if (netContribution >= 0) "a" else "c"}${netContribution}")
        lore.add("<gray>Transactions: <white>${contribution.transactionCount}")

        if (contribution.lastTransaction != null) {
            val lastTransactionTime = LocalDateTime.ofInstant(contribution.lastTransaction, ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            lore.add("<gray>Last Transaction: <white>${lastTransactionTime.format(formatter)}")
        } else {
            lore.add("<gray>Last Transaction: <white>Never")
        }

        lore.add("<gray>Status: §${if (color == NamedTextColor.GREEN) "a" else if (color == NamedTextColor.RED) "c" else "e"}$statusText")

        return createMenuItem(
            Material.PLAYER_HEAD,
            playerName,
            lore
        )
    }

    /**
     * Load member contributions data
     */
    private fun loadContributions() {
        allContributions = bankService.getMemberContributions(guild.id)
        filteredContributions = allContributions
        applySorting()
    }

    /**
     * Update the contributions display
     */
    private fun updateContributionsDisplay() {
        loadContributions()
        setupContributionsDisplay()
    }

    /**
     * Toggle sort order
     */
    private fun toggleSort(newSort: SortBy) {
        sortBy = when (sortBy) {
            SortBy.NET_CONTRIBUTION_DESC -> SortBy.NET_CONTRIBUTION_ASC
            SortBy.NET_CONTRIBUTION_ASC -> SortBy.NET_CONTRIBUTION_DESC
            else -> newSort
        }
        if (sortBy != newSort) {
            sortBy = newSort
        }
        applySorting()
        updateContributionsDisplay()
    }

    /**
     * Filter by contribution status
     */
    private fun filterByStatus(status: MemberContribution.ContributionStatus) {
        filteredContributions = allContributions.filter { it.contributionStatus == status }
        applySorting()
        currentPage = 0
        updateContributionsDisplay()
    }

    /**
     * Show all contributions
     */
    private fun showAllContributions() {
        filteredContributions = allContributions
        applySorting()
        currentPage = 0
        updateContributionsDisplay()
    }

    /**
     * Apply current sorting
     */
    private fun applySorting() {
        filteredContributions = when (sortBy) {
            SortBy.NET_CONTRIBUTION_DESC -> filteredContributions.sortedByDescending { it.netContribution }
            SortBy.NET_CONTRIBUTION_ASC -> filteredContributions.sortedBy { it.netContribution }
            SortBy.DEPOSITS_DESC -> filteredContributions.sortedByDescending { it.totalDeposits }
            SortBy.DEPOSITS_ASC -> filteredContributions.sortedBy { it.totalDeposits }
            SortBy.WITHDRAWALS_DESC -> filteredContributions.sortedByDescending { it.totalWithdrawals }
            SortBy.WITHDRAWALS_ASC -> filteredContributions.sortedBy { it.totalWithdrawals }
            SortBy.LAST_TRANSACTION_DESC -> filteredContributions.sortedByDescending { it.lastTransaction }
            SortBy.LAST_TRANSACTION_ASC -> filteredContributions.sortedBy { it.lastTransaction }
        }
    }

    /**
     * Get current page items
     */
    private fun getCurrentPageItems(): List<MemberContribution> {
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredContributions.size)
        return if (startIndex < filteredContributions.size) {
            filteredContributions.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    /**
     * Update page navigation controls
     */
    private fun updatePageNavigation() {
        val totalPages = (filteredContributions.size + itemsPerPage - 1) / itemsPerPage

        if (totalPages > 1) {
            // Previous page button
            if (currentPage > 0) {
                val prevItem = createMenuItem(
                    Material.ARROW,
                    "Previous Page",
                    listOf("Go to page ${currentPage}")
                )
                val prevGuiItem = GuiItem(prevItem) { event ->
                    event.isCancelled = true
                    currentPage--
                    updateContributionsDisplay()
                }
                filterPane.addItem(prevGuiItem, 7, 0)
            }

            // Next page button
            if (currentPage < totalPages - 1) {
                val nextItem = createMenuItem(
                    Material.ARROW,
                    "Next Page",
                    listOf("Go to page ${currentPage + 2}")
                )
                val nextGuiItem = GuiItem(nextItem) { event ->
                    event.isCancelled = true
                    currentPage++
                    updateContributionsDisplay()
                }
                filterPane.addItem(nextGuiItem, 8, 0)
            }

            // Page indicator
            val pageItem = createMenuItem(
                Material.PAPER,
                "Page ${currentPage + 1}/$totalPages",
                listOf("${filteredContributions.size} members shown")
            )
            filterPane.addItem(GuiItem(pageItem), 6, 0)
        }
    }

    /**
     * Handle CSV export of member contributions
     */
    private fun handleExport() {
        // Show loading message
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🔄 Generating CSV export... This may take a moment.")

        // Get current filtered contributions
        val contributionsToExport = filteredContributions

        if (contributionsToExport.isEmpty()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No member data to export!")
            return
        }

        // Start async export
        fileExportManager.exportMemberContributionsAsync(
            player,
            contributionsToExport,
            guild.name
        ) { result ->
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
     * Get localized string
     */
    private fun getLocalizedString(key: String): String {
        return localizationProvider.get(player.uniqueId, key)
    }

    /**
     * Create menu item helper
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(Component.text(name))
        meta.lore(lore.map { Component.text(it) })

        item.itemMeta = meta
        return item
    }

    /**
     * Sort options
     */
    enum class SortBy {
        NET_CONTRIBUTION_DESC,
        NET_CONTRIBUTION_ASC,
        DEPOSITS_DESC,
        DEPOSITS_ASC,
        WITHDRAWALS_DESC,
        WITHDRAWALS_ASC,
        LAST_TRANSACTION_DESC,
        LAST_TRANSACTION_ASC
    }
}

