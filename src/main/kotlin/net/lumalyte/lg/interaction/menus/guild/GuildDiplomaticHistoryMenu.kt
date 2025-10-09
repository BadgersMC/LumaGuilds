package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DiplomaticHistory
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildDiplomaticHistoryMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val eventsPerPage = 8
    private var allHistoryEvents: List<DiplomaticHistory> = emptyList()
    private var currentFilter: String? = null

    override fun open() {
        allHistoryEvents = loadDiplomaticHistory(guild.id)

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue><blue>Diplomatic History - ${guild.name}"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load events into main pane
        loadHistoryPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadDiplomaticHistory(guildId: UUID): List<DiplomaticHistory> {
        return diplomacyService.getDiplomaticHistory(guildId)
    }

    private fun loadHistoryPage(mainPane: StaticPane) {
        // Show first 36 events (4 rows x 9 columns)
        val maxEvents = min(36, allHistoryEvents.size)
        val pageEvents = allHistoryEvents.take(maxEvents)

        pageEvents.forEachIndexed { index, historyEvent ->
            val item = createHistoryItem(historyEvent)
            mainPane.addItem(GuiItem(item) { _ ->
                showHistoryDetails(historyEvent)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageEvents.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Event")
                .lore(listOf("<dark_gray>No diplomatic event in this slot"))

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createHistoryItem(historyEvent: DiplomaticHistory): ItemStack {
        val timestamp = historyEvent.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        val material = getEventMaterial(historyEvent.eventType)
        val color = getEventColor(historyEvent.eventType)
        val itemName = "<white>${historyEvent.description}"

        val lore = mutableListOf(
            "<gray>Event Type: ${color}${historyEvent.eventType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
            "<gray>Date: <white>$timestamp",
            "<gray>Time: <white>${historyEvent.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
        )

        historyEvent.targetGuildId?.let { targetId ->
            val targetGuild = guildService.getGuild(targetId)
            targetGuild?.let { target ->
                lore.add("<gray>Involved Guild: <white>${target.name}")
            }
        }

        lore.add("<yellow>Click for details")

        return ItemStack(material)
            .name(itemName)
            .lore(lore)
    }

    private fun showHistoryDetails(historyEvent: DiplomaticHistory) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue><blue>Diplomatic Event Details"))
        val pane = StaticPane(0, 0, 9, 4)

        // Event Information
        val infoItem = ItemStack(Material.BOOK)
        val infoMeta = infoItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        infoMeta.setDisplayName("<aqua>Event Information")
        infoMeta.lore = listOf(
            "<gray>Event Type: <white>${historyEvent.eventType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
            "<gray>Description: <white>${historyEvent.description}",
            "<gray>Timestamp: <white>${historyEvent.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"))}",
            "<gray>Event ID: <white>${historyEvent.id.toString().take(8)}..."
        )
        infoItem.itemMeta = infoMeta

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Guild Information
        val targetGuild = historyEvent.targetGuildId?.let { guildService.getGuild(it) }
        if (targetGuild != null) {
            val guildItem = ItemStack(Material.PLAYER_HEAD)
            val guildMeta = guildItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)!!
            guildMeta.setDisplayName("<yellow>Involved Guild")
            guildMeta.lore = listOf(
                "<gray>Guild: <white>${targetGuild.name}",
                "<gray>Owner: <white>${targetGuild.ownerName}",
                "<gray>Level: <white>${targetGuild.level}",
                "<gray>Members: <white>${targetGuild.level * 5}"
            )
            guildItem.itemMeta = guildMeta

            pane.addItem(GuiItem(guildItem), 0, 1)
        }

        // Event Timeline Context
        val contextItem = ItemStack(Material.CLOCK)
        val contextMeta = contextItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.CLOCK)!!
        contextMeta.setDisplayName("<gold>Timeline Context")
        contextMeta.lore = listOf(
            "<gray>This event occurred in the context of:",
            "<gray>- Guild diplomatic relations",
            "<gray>- War and peace negotiations",
            "<gray>- Alliance formations and breaks",
            "<gray>- Truce agreements and violations"
        )
        contextItem.itemMeta = contextMeta

        pane.addItem(GuiItem(contextItem), 4, 1)

        // Event Significance
        val significance = calculateEventSignificance(historyEvent)
        val significanceItem = ItemStack(getSignificanceMaterial(significance))
        val significanceMeta = significanceItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(getSignificanceMaterial(significance))!!
        significanceMeta.setDisplayName("<light_purple>Event Significance")
        significanceMeta.lore = listOf(
            "<gray>Significance Level: <white>$significance/10",
            "<gray>Impact: <white>${getImpactDescription(significance)}",
            "<gray>Historical Importance: <white>${getImportanceDescription(significance)}"
        )
        significanceItem.itemMeta = significanceMeta

        pane.addItem(GuiItem(significanceItem), 6, 1)

        // Back
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
        backMeta.setDisplayName("<yellow>Back")
        backMeta.lore = listOf("<gray>Return to diplomatic history")
        backItem.itemMeta = backMeta
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun addNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
            val prevMeta = prevItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
            prevMeta.setDisplayName("<green>Previous Page")
            prevMeta.lore = listOf("<gray>Click to go back")
            prevItem.itemMeta = prevMeta
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open()
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * eventsPerPage < allHistoryEvents.size) {
            val nextItem = ItemStack(Material.ARROW)
            val nextMeta = nextItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
            nextMeta.setDisplayName("<green>Next Page")
            nextMeta.lore = listOf("<gray>Click to go forward")
            nextItem.itemMeta = nextMeta
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open()
            }, 8, 0)
        }

        // Filter options
        val filterItem = ItemStack(Material.COMPASS)
        val filterMeta = filterItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.COMPASS)!!
        filterMeta.setDisplayName("<aqua>Filter Events")
        filterMeta.lore = listOf("<gray>Filter by event type")
        filterItem.itemMeta = filterMeta
        navigationPane.addItem(GuiItem(filterItem) { _ ->
            openFilterMenu()
        }, 2, 0)

        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back to Relations")
        backMeta.lore = listOf("<gray>Return to relations menu")
        backItem.itemMeta = backMeta
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)

        // Refresh
        val refreshItem = ItemStack(Material.LIME_DYE)
        val refreshMeta = refreshItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.LIME_DYE)!!
        refreshMeta.setDisplayName("<green>Refresh")
        refreshMeta.lore = listOf("<gray>Refresh history list")
        refreshItem.itemMeta = refreshMeta
        navigationPane.addItem(GuiItem(refreshItem) { _ ->
            open()
        }, 6, 0)
    }

    private fun openFilterMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<aqua><aqua>Filter Diplomatic Events"))
        val pane = StaticPane(0, 0, 9, 3)

        // All Events
        val allItem = ItemStack(Material.BOOK)
        val allMeta = allItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        allMeta.setDisplayName("<white>All Events")
        allMeta.lore = listOf(
            "<gray>Show all diplomatic events",
            "<gray>Total: <white>${allHistoryEvents.size}",
            if (currentFilter == null) "<green>Currently selected" else "<gray>Click to select"
        )
        allItem.itemMeta = allMeta

        pane.addItem(GuiItem(allItem) { _ ->
            currentFilter = null
            open()
        }, 1, 1)

        // Alliance Events
        val allianceCount = allHistoryEvents.count { it.eventType.contains("alliance") }
        val allianceItem = ItemStack(Material.DIAMOND)
        val allianceMeta = allianceItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.DIAMOND)!!
        allianceMeta.setDisplayName("<green>Alliance Events")
        allianceMeta.lore = listOf(
            "<gray>Formations, breaks, requests",
            "<gray>Total: <white>$allianceCount",
            if (currentFilter == "alliance") "<green>Currently selected" else "<gray>Click to select"
        )
        allianceItem.itemMeta = allianceMeta

        pane.addItem(GuiItem(allianceItem) { _ ->
            currentFilter = "alliance"
            open()
        }, 3, 1)

        // War Events
        val warCount = allHistoryEvents.count { it.eventType.contains("war") }
        val warItem = ItemStack(Material.IRON_SWORD)
        val warMeta = warItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.IRON_SWORD)!!
        warMeta.setDisplayName("<red>War Events")
        warMeta.lore = listOf(
            "<gray>Declarations, surrenders, victories",
            "<gray>Total: <white>$warCount",
            if (currentFilter == "war") "<green>Currently selected" else "<gray>Click to select"
        )
        warItem.itemMeta = warMeta

        pane.addItem(GuiItem(warItem) { _ ->
            currentFilter = "war"
            open()
        }, 5, 1)

        // Truce Events
        val truceCount = allHistoryEvents.count { it.eventType.contains("truce") }
        val truceItem = ItemStack(Material.WHITE_BANNER)
        val truceMeta = truceItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.WHITE_BANNER)!!
        truceMeta.setDisplayName("<yellow>Truce Events")
        truceMeta.lore = listOf(
            "<gray>Requests, extensions, breaks",
            "<gray>Total: <white>$truceCount",
            if (currentFilter == "truce") "<green>Currently selected" else "<gray>Click to select"
        )
        truceItem.itemMeta = truceMeta

        pane.addItem(GuiItem(truceItem) { _ ->
            currentFilter = "truce"
            open()
        }, 7, 1)

        // Back
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
        backMeta.setDisplayName("<yellow>Back")
        backMeta.lore = listOf("<gray>Return to history")
        backItem.itemMeta = backMeta
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    // Helper methods
    private fun getEventMaterial(eventType: String): Material {
        return when {
            eventType.contains("alliance") -> Material.DIAMOND
            eventType.contains("war") -> Material.IRON_SWORD
            eventType.contains("truce") -> Material.WHITE_BANNER
            eventType.contains("request") -> Material.PAPER
            else -> Material.BOOK
        }
    }

    private fun getEventColor(eventType: String): String {
        return when {
            eventType.contains("alliance") -> "<green>"
            eventType.contains("war") -> "<red>"
            eventType.contains("truce") -> "<yellow>"
            eventType.contains("request") -> "<aqua>"
            else -> "<white>"
        }
    }

    private fun calculateEventSignificance(historyEvent: DiplomaticHistory): Int {
        return when (historyEvent.eventType) {
            "war_declared" -> 10
            "alliance_formed" -> 8
            "truce_broken" -> 7
            "alliance_broken" -> 6
            "truce_formed" -> 5
            "request_accepted" -> 4
            "request_rejected" -> 3
            "request_sent" -> 2
            else -> 1
        }
    }

    private fun getSignificanceMaterial(significance: Int): Material {
        return when {
            significance >= 8 -> Material.NETHER_STAR
            significance >= 6 -> Material.DIAMOND
            significance >= 4 -> Material.GOLD_INGOT
            else -> Material.IRON_INGOT
        }
    }

    private fun getImpactDescription(significance: Int): String {
        return when {
            significance >= 8 -> "High Impact"
            significance >= 6 -> "Moderate Impact"
            significance >= 4 -> "Low Impact"
            else -> "Minimal Impact"
        }
    }

    private fun getImportanceDescription(significance: Int): String {
        return when {
            significance >= 8 -> "Major diplomatic milestone"
            significance >= 6 -> "Significant diplomatic event"
            significance >= 4 -> "Notable diplomatic action"
            else -> "Routine diplomatic activity"
        }
    }
}
