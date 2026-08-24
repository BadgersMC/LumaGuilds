package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.MemberContribution
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Guild Member Contributions menu showing net contributions for each member
 */
class GuildMemberContributionsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val lang: LangService by inject()
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
    }

    override fun passData(data: Any?) {
        // Handle filter updates if needed
        updateContributionsDisplay()
        gui.update()
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.bank.contributions.title", "guild" to guild.name)))
        gui.setOnGlobalClick { event -> event.isCancelled = true }

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
            lang.gui("menu.bank.contributions.navigation.back_name"),
            listOf(lang.gui("menu.bank.contributions.navigation.back_description"))
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString("menu.bank.close"),
            listOf(lang.gui("menu.common.close_description"))
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
            lang.gui("menu.bank.contributions.filter.net.name"),
            listOf(
                lang.gui("menu.bank.contributions.filter.net.description"),
                lang.gui("menu.bank.contributions.filter.net.toggle")
            )
        )
        val netContributionGuiItem = GuiItem(netContributionItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.NET_CONTRIBUTION_DESC)
        }
        filterPane.addItem(netContributionGuiItem, 0, 0)

        // Sort by Total Deposits
        val depositsItem = createMenuItem(
            Material.EMERALD,
            lang.gui("menu.bank.contributions.filter.deposits.name"),
            listOf(lang.gui("menu.bank.contributions.filter.deposits.description"))
        )
        val depositsGuiItem = GuiItem(depositsItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.DEPOSITS_DESC)
        }
        filterPane.addItem(depositsGuiItem, 1, 0)

        // Sort by Total Withdrawals
        val withdrawalsItem = createMenuItem(
            Material.REDSTONE,
            lang.gui("menu.bank.contributions.filter.withdrawals.name"),
            listOf(lang.gui("menu.bank.contributions.filter.withdrawals.description"))
        )
        val withdrawalsGuiItem = GuiItem(withdrawalsItem) { event ->
            event.isCancelled = true
            toggleSort(SortBy.WITHDRAWALS_DESC)
        }
        filterPane.addItem(withdrawalsGuiItem, 2, 0)

        // Show only freeloaders
        val freeloadersItem = createMenuItem(
            Material.RED_WOOL,
            lang.gui("menu.bank.contributions.filter.freeloaders.name"),
            listOf(lang.gui("menu.bank.contributions.filter.freeloaders.description"))
        )
        val freeloadersGuiItem = GuiItem(freeloadersItem) { event ->
            event.isCancelled = true
            filterByStatus(MemberContribution.ContributionStatus.FREELOADER)
        }
        filterPane.addItem(freeloadersGuiItem, 3, 0)

        // Show all members
        val allMembersItem = createMenuItem(
            Material.GREEN_WOOL,
            lang.gui("menu.bank.contributions.filter.all.name"),
            listOf(lang.gui("menu.bank.contributions.filter.all.description"))
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
                lang.gui("menu.bank.contributions.empty.name"),
                listOf(lang.gui("menu.bank.contributions.empty.description"))
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
        val playerName = contribution.playerName?.let(Component::text)
            ?: lang.gui("menu.common.unknown_player")
        val netContribution = contribution.netContribution

        val statusText = when (contribution.contributionStatus) {
            MemberContribution.ContributionStatus.CONTRIBUTOR -> lang.gui("menu.bank.contributions.status.contributor")
            MemberContribution.ContributionStatus.FREELOADER -> lang.gui("menu.bank.contributions.status.freeloader")
            MemberContribution.ContributionStatus.BREAK_EVEN_CONTRIBUTOR -> lang.gui("menu.bank.contributions.status.break_even")
            MemberContribution.ContributionStatus.NEUTRAL -> lang.gui("menu.bank.contributions.status.neutral")
        }

        val lore = mutableListOf<Component>()
        lore.add(lang.gui("menu.bank.contributions.item.deposits", "amount" to contribution.totalDeposits))
        lore.add(lang.gui("menu.bank.contributions.item.withdrawals", "amount" to contribution.totalWithdrawals))
        lore.add(if (netContribution >= 0) {
            lang.gui("menu.bank.contributions.item.net_positive", "amount" to netContribution)
        } else {
            lang.gui("menu.bank.contributions.item.net_negative", "amount" to netContribution)
        })
        lore.add(lang.gui("menu.bank.contributions.item.transactions", "count" to contribution.transactionCount))

        if (contribution.lastTransaction != null) {
            val lastTransactionTime = LocalDateTime.ofInstant(contribution.lastTransaction, ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern(lang.raw("menu.bank.contributions.date_format"))
            lore.add(lang.gui("menu.bank.contributions.item.last_transaction", "time" to lastTransactionTime.format(formatter)))
        } else {
            lore.add(lang.gui("menu.bank.contributions.item.last_transaction_never"))
        }

        lore.add(when (contribution.contributionStatus) {
            MemberContribution.ContributionStatus.CONTRIBUTOR -> lang.gui("menu.bank.contributions.item.status_contributor", "status" to statusText)
            MemberContribution.ContributionStatus.FREELOADER -> lang.gui("menu.bank.contributions.item.status_freeloader", "status" to statusText)
            MemberContribution.ContributionStatus.BREAK_EVEN_CONTRIBUTOR -> lang.gui("menu.bank.contributions.item.status_break_even", "status" to statusText)
            MemberContribution.ContributionStatus.NEUTRAL -> lang.gui("menu.bank.contributions.item.status_neutral", "status" to statusText)
        })

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
                    lang.gui("menu.bank.history.page.previous"),
                    listOf(lang.gui("menu.common.page.go_to", "page" to currentPage))
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
                    lang.gui("menu.bank.history.page.next"),
                    listOf(lang.gui("menu.common.page.go_to", "page" to currentPage + 2))
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
                lang.gui("menu.common.page.indicator", "current" to currentPage + 1, "total" to totalPages),
                listOf(lang.gui("menu.bank.contributions.page.members_shown", "count" to filteredContributions.size))
            )
            filterPane.addItem(GuiItem(pageItem), 6, 0)
        }
    }

    /**
     * Get localized string
     */
    private fun getLocalizedString(key: String): Component {
        return lang.gui(key)
    }

    /**
     * Create menu item helper
     */
    private fun createMenuItem(material: Material, name: Component, lore: List<*>): ItemStack {
        val item = ItemStack.of(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(name)
        meta.lore(lore.map { if (it is Component) it else Component.text(it.toString()) })

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

