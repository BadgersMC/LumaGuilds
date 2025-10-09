package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ActivityLevel
import net.lumalyte.lg.application.services.MemberSearchFilter
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
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
import java.time.LocalDate
import java.time.ZoneId
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Advanced member search menu with comprehensive filtering options.
 */
class GuildAdvancedMemberSearchMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val messageService: MessageService
) : Menu, KoinComponent {

    private var searchFilter: MemberSearchFilter = MemberSearchFilter()
    private val memberService: MemberService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    init {
        initializeGui()
    }

    override fun open() {
        updateFilterDisplay()
        gui.show(player)
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Advanced Member Search - ${guild.name}"))
        AntiDupeUtil.protect(gui)

        mainPane = StaticPane(0, 0, 9, 5)
        gui.addPane(mainPane)

        setupFilterOptions()
        setupSearchButton()
        setupBackButton()
    }

    /**
     * Setup filter option buttons
     */
    private fun setupFilterOptions() {
        var row = 0

        // Name search filter
        val nameFilterItem = createMenuItem(
            Material.NAME_TAG,
            "Search by Name",
            listOf(
                "Current: ${searchFilter.nameQuery ?: "Not set"}",
                "Enter player name to search",
                "Supports partial matches"
            )
        )
        val nameFilterGuiItem = GuiItem(nameFilterItem) { event ->
            event.isCancelled = true
            // TODO: Open text input for name search
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Name search input coming soon!")
        }
        mainPane.addItem(nameFilterGuiItem, 0, row++)

        // Rank filter
        val rankFilterItem = createMenuItem(
            Material.BOOK,
            "Filter by Rank",
            listOf(
                "Current: ${getRankFilterDescription()}",
                "Select specific ranks to include",
                "Click to modify rank filter"
            )
        )
        val rankFilterGuiItem = GuiItem(rankFilterItem) { event ->
            event.isCancelled = true
            openRankFilterMenu()
        }
        mainPane.addItem(rankFilterGuiItem, 1, row++)

        // Online status filter
        val onlineFilterItem = createMenuItem(
            if (searchFilter.onlineOnly) Material.GREEN_WOOL else Material.RED_WOOL,
            "Online Only",
            listOf(
                "Status: ${if (searchFilter.onlineOnly) "Enabled" else "Disabled"}",
                "Show only online members",
                "Click to toggle"
            )
        )
        val onlineFilterGuiItem = GuiItem(onlineFilterItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(onlineOnly = !searchFilter.onlineOnly)
            updateFilterDisplay()
            gui.update()
        }
        mainPane.addItem(onlineFilterGuiItem, 2, row++)

        // Join date filters
        val joinDateFilterItem = createMenuItem(
            Material.CLOCK,
            "Join Date Range",
            listOf(
                "From: ${searchFilter.joinDateAfter?.let { formatDate(it) } ?: "Not set"}",
                "To: ${searchFilter.joinDateBefore?.let { formatDate(it) } ?: "Not set"}",
                "Click to set date range"
            )
        )
        val joinDateFilterGuiItem = GuiItem(joinDateFilterItem) { event ->
            event.isCancelled = true
            openDateFilterMenu()
        }
        mainPane.addItem(joinDateFilterGuiItem, 3, row++)

        // Activity level filter
        val activityFilterItem = createMenuItem(
            Material.EXPERIENCE_BOTTLE,
            "Activity Level",
            listOf(
                "Current: ${searchFilter.activityLevel?.name ?: "All levels"}",
                "Filter by member activity",
                "Click to change"
            )
        )
        val activityFilterGuiItem = GuiItem(activityFilterItem) { event ->
            event.isCancelled = true
            openActivityLevelMenu()
        }
        mainPane.addItem(activityFilterGuiItem, 4, row++)

        // Contribution range filter
        val contributionFilterItem = createMenuItem(
            Material.GOLD_INGOT,
            "Contribution Range",
            listOf(
                "Min: ${searchFilter.minContributions ?: "Not set"}",
                "Max: ${searchFilter.maxContributions ?: "Not set"}",
                "Filter by total contributions"
            )
        )
        val contributionFilterGuiItem = GuiItem(contributionFilterItem) { event ->
            event.isCancelled = true
            openContributionRangeMenu()
        }
        mainPane.addItem(contributionFilterGuiItem, 5, row++)
    }

    /**
     * Setup search execution button
     */
    private fun setupSearchButton() {
        val searchItem = createMenuItem(
            Material.DIAMOND_SWORD,
            "Execute Search",
            listOf(
                "Search with current filters",
                "Found: ${getSearchResultCount()} members",
                "Click to search"
            )
        )
        val searchGuiItem = GuiItem(searchItem) { event ->
            event.isCancelled = true
            executeSearch()
        }
        mainPane.addItem(searchGuiItem, 7, 4)
    }

    /**
     * Setup back button
     */
    private fun setupBackButton() {
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Members",
            listOf("Return to member list")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildMemberListMenu(menuNavigator, player, guild, messageService))
        }
        mainPane.addItem(backGuiItem, 8, 4)
    }

    /**
     * Open rank filter selection menu
     */
    private fun openRankFilterMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Filter by Ranks"))
        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        // Get all ranks for this guild
        val rankService = net.lumalyte.lg.application.services.RankService::class.java
        // TODO: Inject RankService properly
        val allRanks = listOf<Rank>() // Placeholder

        var currentRow = 0
        var currentCol = 0

        // All ranks option
        val allRanksItem = createMenuItem(
            Material.CHEST,
            "All Ranks",
            listOf("Include all ranks in search")
        )
        val allRanksGuiItem = GuiItem(allRanksItem) { event ->
            event.isCancelled = true
            // Clear rank filter and refresh
            searchFilter = searchFilter.copy(rankFilter = null)
            open()
        }
        pane.addItem(allRanksGuiItem, currentCol, currentRow)

        // Individual rank options would go here
        // For now, simplified implementation

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Search",
            listOf("Return to search menu")
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
     * Open activity level filter menu
     */
    private fun openActivityLevelMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Filter by Activity Level"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        var currentRow = 0

        // All activity levels
        val allActivityItem = createMenuItem(
            Material.CHEST,
            "All Activity Levels",
            listOf("Include all activity levels")
        )
        val allActivityGuiItem = GuiItem(allActivityItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(activityLevel = null)
            open()
        }
        pane.addItem(allActivityGuiItem, 0, currentRow)

        // High activity
        val highActivityItem = createMenuItem(
            Material.EMERALD,
            "High Activity",
            listOf("Very active members only")
        )
        val highActivityGuiItem = GuiItem(highActivityItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(activityLevel = ActivityLevel.HIGH)
            open()
        }
        pane.addItem(highActivityGuiItem, 2, currentRow)

        currentRow++

        // Medium activity
        val mediumActivityItem = createMenuItem(
            Material.GOLD_INGOT,
            "Medium Activity",
            listOf("Moderately active members")
        )
        val mediumActivityGuiItem = GuiItem(mediumActivityItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(activityLevel = ActivityLevel.MEDIUM)
            open()
        }
        pane.addItem(mediumActivityGuiItem, 4, currentRow)

        currentRow++

        // Low/Inactive
        val lowActivityItem = createMenuItem(
            Material.REDSTONE,
            "Low/Inactive",
            listOf("Less active or inactive members")
        )
        val lowActivityGuiItem = GuiItem(lowActivityItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(activityLevel = ActivityLevel.LOW)
            open()
        }
        pane.addItem(lowActivityGuiItem, 6, currentRow)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Search",
            listOf("Return to search menu")
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
     * Open date filter menu
     */
    private fun openDateFilterMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Filter by Join Date"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        // Quick date options
        val lastWeekItem = createMenuItem(
            Material.CLOCK,
            "Last 7 Days",
            listOf("Members who joined in the last week")
        )
        val lastWeekGuiItem = GuiItem(lastWeekItem) { event ->
            event.isCancelled = true
            val weekAgo = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)
            searchFilter = searchFilter.copy(joinDateAfter = weekAgo)
            open()
        }
        pane.addItem(lastWeekGuiItem, 0, 0)

        val lastMonthItem = createMenuItem(
            Material.CLOCK,
            "Last 30 Days",
            listOf("Members who joined in the last month")
        )
        val lastMonthGuiItem = GuiItem(lastMonthItem) { event ->
            event.isCancelled = true
            val monthAgo = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS)
            searchFilter = searchFilter.copy(joinDateAfter = monthAgo)
            open()
        }
        pane.addItem(lastMonthGuiItem, 2, 0)

        val customDateItem = createMenuItem(
            Material.BOOK,
            "Custom Date Range",
            listOf("Set specific date range", "Coming soon!")
        )
        val customDateGuiItem = GuiItem(customDateItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Custom date range picker coming soon!")
        }
        pane.addItem(customDateGuiItem, 4, 0)

        // Clear date filter
        val clearDateItem = createMenuItem(
            Material.WATER_BUCKET,
            "Clear Date Filter",
            listOf("Remove date restrictions")
        )
        val clearDateGuiItem = GuiItem(clearDateItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(joinDateAfter = null, joinDateBefore = null)
            open()
        }
        pane.addItem(clearDateGuiItem, 6, 0)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Search",
            listOf("Return to search menu")
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
     * Open contribution range menu
     */
    private fun openContributionRangeMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Filter by Contributions"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        // Preset ranges
        val highContributionItem = createMenuItem(
            Material.DIAMOND,
            "High Contributors (1000+)",
            listOf("Members with 1000+ total contributions")
        )
        val highContributionGuiItem = GuiItem(highContributionItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(minContributions = 1000)
            open()
        }
        pane.addItem(highContributionGuiItem, 0, 0)

        val mediumContributionItem = createMenuItem(
            Material.GOLD_INGOT,
            "Medium Contributors (100-999)",
            listOf("Members with 100-999 total contributions")
        )
        val mediumContributionGuiItem = GuiItem(mediumContributionItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(minContributions = 100, maxContributions = 999)
            open()
        }
        pane.addItem(mediumContributionGuiItem, 2, 0)

        val lowContributionItem = createMenuItem(
            Material.IRON_INGOT,
            "Low Contributors (< 100)",
            listOf("Members with less than 100 contributions")
        )
        val lowContributionGuiItem = GuiItem(lowContributionItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(maxContributions = 99)
            open()
        }
        pane.addItem(lowContributionGuiItem, 4, 0)

        val customRangeItem = createMenuItem(
            Material.BOOK,
            "Custom Range",
            listOf("Set custom contribution range", "Coming soon!")
        )
        val customRangeGuiItem = GuiItem(customRangeItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Custom contribution range picker coming soon!")
        }
        pane.addItem(customRangeGuiItem, 6, 0)

        // Clear contribution filter
        val clearContributionItem = createMenuItem(
            Material.WATER_BUCKET,
            "Clear Contribution Filter",
            listOf("Remove contribution restrictions")
        )
        val clearContributionGuiItem = GuiItem(clearContributionItem) { event ->
            event.isCancelled = true
            searchFilter = searchFilter.copy(minContributions = null, maxContributions = null)
            open()
        }
        pane.addItem(clearContributionGuiItem, 0, 1)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Search",
            listOf("Return to search menu")
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
     * Execute the search with current filters
     */
    private fun executeSearch() {
        try {
            val results = memberService.searchMembers(guild.id, searchFilter)

            if (results.isEmpty()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>No members found matching the search criteria.")
                return
            }

            AdventureMenuHelper.sendMessage(player, messageService, "<green>Found ${results.size} members matching criteria:")
            results.take(5).forEach { member ->
                val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown"
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>- $playerName")
            }

            if (results.size > 5) {
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>... and ${results.size - 5} more")
            }

            // Open results menu
            menuNavigator.openMenu(GuildMemberSearchResultsMenu(
                menuNavigator, player, guild, results, searchFilter, messageService
            ))
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error executing search: ${e.message}")
        }
    }

    /**
     * Update filter display to show current settings
     */
    private fun updateFilterDisplay() {
        // Update is handled by individual setup methods
    }

    /**
     * Get count of search results with current filters
     */
    private fun getSearchResultCount(): Int {
        return try {
            memberService.searchMembers(guild.id, searchFilter).size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get rank filter description
     */
    private fun getRankFilterDescription(): String {
        return if (searchFilter.rankFilter == null) {
            "All ranks"
        } else {
            val rankFilter = searchFilter.rankFilter
            "${rankFilter?.size ?: 0} ranks selected"
        }
    }

    /**
     * Format instant to readable date string
     */
    private fun formatDate(instant: Instant): String {
        return LocalDate.ofInstant(instant, ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
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
}
