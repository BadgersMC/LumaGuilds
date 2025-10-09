package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
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
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildNeutralGuildsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                            private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val guildsPerPage = 8
    private var neutralGuilds: List<Guild> = emptyList()

    override fun open() {
        neutralGuilds = loadNeutralGuilds()

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gray><gray>Neutral Guilds - ${guild.name}"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load guilds into main pane
        loadGuildsPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadNeutralGuilds(): List<Guild> {
        val allGuilds = guildService.getAllGuilds()
        val currentRelations = diplomacyService.getRelations(guild.id)

        return allGuilds.filter { targetGuild ->
            targetGuild.id != guild.id && // Don't show own guild
            !currentRelations.any { relation ->
                relation.targetGuildId == targetGuild.id &&
                relation.isActive()
            } // Only show guilds with no active relations
        }
    }

    private fun loadGuildsPage(mainPane: StaticPane) {
        // Show first 36 guilds (4 rows x 9 columns)
        val maxGuilds = min(36, neutralGuilds.size)
        val pageGuilds = neutralGuilds.take(maxGuilds)

        pageGuilds.forEachIndexed { index, neutralGuild ->
            val item = createGuildItem(neutralGuild)
            mainPane.addItem(GuiItem(item) { _ ->
                openGuildActionsMenu(neutralGuild)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageGuilds.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
            val placeholderMeta = placeholderItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.GRAY_STAINED_GLASS_PANE)!!
            placeholderMeta.setDisplayName("<gray>No Guild")
            placeholderMeta.lore = listOf("<dark_gray>No neutral guild in this slot")
            placeholderItem.itemMeta = placeholderMeta

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createGuildItem(neutralGuild: Guild): ItemStack {
        val lore = mutableListOf(
            "<gray>Owner: <white>${neutralGuild.ownerName}",
            "<gray>Members: <white>${neutralGuild.level * 5}",
            "<gray>Level: <white>${neutralGuild.level}",
            "<gray>Status: <gray>Neutral - No relations",
            "<gray>Power Level: <white>${calculatePowerLevel(neutralGuild)}",
            "<yellow>Click to view diplomatic options"
        )

        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)!!
        meta.setDisplayName("<white>${neutralGuild.name}")
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun openGuildActionsMenu(neutralGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gray><gray>Diplomatic Options - ${neutralGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Guild Information
        val infoItem = ItemStack(Material.BOOK)
        val infoMeta = infoItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        infoMeta.setDisplayName("<aqua>Guild Information")
        infoMeta.lore = listOf(
            "<gray>Guild: <white>${neutralGuild.name}",
            "<gray>Owner: <white>${neutralGuild.ownerName}",
            "<gray>Level: <white>${neutralGuild.level}",
            "<gray>Members: <white>${neutralGuild.level * 5}",
            "<gray>Power Level: <white>${calculatePowerLevel(neutralGuild)}"
        )
        infoItem.itemMeta = infoMeta

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Diplomatic Outreach Options
        addDiplomaticOptions(pane, neutralGuild)

        // Guild Comparison
        val comparisonItem = ItemStack(Material.COMPASS)
        val comparisonMeta = comparisonItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.COMPASS)!!
        comparisonMeta.setDisplayName("<gold>Guild Comparison")
        comparisonMeta.lore = listOf(
            "<gray>Compare your guild with ${neutralGuild.name}",
            "<gray>Power: <white>${calculatePowerLevel(guild)} vs ${calculatePowerLevel(neutralGuild)}",
            "<gray>Members: <white>${guild.level * 5} vs ${neutralGuild.level * 5}",
            "<gray>Level: <white>${guild.level} vs ${neutralGuild.level}"
        )
        comparisonItem.itemMeta = comparisonMeta

        pane.addItem(GuiItem(comparisonItem), 0, 2)

        // Diplomatic History (if any)
        val historyCount = getDiplomaticHistoryCount(neutralGuild)
        val historyItem = ItemStack(Material.PAPER)
        val historyMeta = historyItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PAPER)!!
        historyMeta.setDisplayName("<yellow>Diplomatic History")
        historyMeta.lore = listOf(
            "<gray>Past interactions: <white>$historyCount events",
            "<gray>Check for previous relations",
            "<gray>View diplomatic patterns"
        )
        historyItem.itemMeta = historyMeta

        pane.addItem(GuiItem(historyItem) { _ ->
            openDiplomaticHistory(neutralGuild)
        }, 4, 2)

        // Back
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
        backMeta.setDisplayName("<yellow>Back")
        backMeta.lore = listOf("<gray>Return to neutral guilds")
        backItem.itemMeta = backMeta
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 3)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun addDiplomaticOptions(pane: StaticPane, neutralGuild: Guild) {
        // Request Alliance
        val allianceItem = ItemStack(Material.DIAMOND)
        val allianceMeta = allianceItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.DIAMOND)!!
        allianceMeta.setDisplayName("<green>Request Alliance")
        allianceMeta.lore = listOf(
            "<gray>Propose an alliance with ${neutralGuild.name}",
            "<gray>Benefits: Mutual protection and support",
            "<gray>Requires their acceptance"
        )
        allianceItem.itemMeta = allianceMeta

        pane.addItem(GuiItem(allianceItem) { _ ->
            openAllianceRequest(neutralGuild)
        }, 1, 1)

        // Send Message
        val messageItem = ItemStack(Material.PAPER)
        val messageMeta = messageItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PAPER)!!
        messageMeta.setDisplayName("<aqua>Send Diplomatic Message")
        messageMeta.lore = listOf(
            "<gray>Send a diplomatic message",
            "<gray>Express interest in cooperation",
            "<gray>Open diplomatic channels"
        )
        messageItem.itemMeta = messageMeta

        pane.addItem(GuiItem(messageItem) { _ ->
            openMessageComposer(neutralGuild)
        }, 3, 1)

        // Observe Guild
        val observeItem = ItemStack(Material.SPYGLASS)
        val observeMeta = observeItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.SPYGLASS)!!
        observeMeta.setDisplayName("<yellow>Observe Guild")
        observeMeta.lore = listOf(
            "<gray>Monitor ${neutralGuild.name}'s activities",
            "<gray>Track their diplomatic moves",
            "<gray>Gather intelligence"
        )
        observeItem.itemMeta = observeMeta

        pane.addItem(GuiItem(observeItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Observation feature coming soon!")
        }, 5, 1)

        // Block Diplomatic Relations
        val blockItem = ItemStack(Material.BARRIER)
        val blockMeta = blockItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        blockMeta.setDisplayName("<red>Block Diplomatic Relations")
        blockMeta.lore = listOf(
            "<gray>Prevent diplomatic contact with ${neutralGuild.name}",
            "<gray>No alliance or truce requests",
            "<gray>Can be reversed later"
        )
        blockItem.itemMeta = blockMeta

        pane.addItem(GuiItem(blockItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Blocking feature coming soon!")
        }, 7, 1)
    }

    private fun openAllianceRequest(neutralGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<green><green>Alliance Request to ${neutralGuild.name}"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.LIME_WOOL)
        val confirmMeta = confirmItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.LIME_WOOL)!!
        confirmMeta.setDisplayName("<green>Send Alliance Request")
        confirmMeta.lore = listOf("<gray>Send an alliance request to ${neutralGuild.name}?")
        confirmItem.itemMeta = confirmMeta

        pane.addItem(GuiItem(confirmItem) { _ ->
            sendAllianceRequest(neutralGuild)
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Alliance request sent to ${neutralGuild.name}!")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.RED_WOOL)
        val cancelMeta = cancelItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.RED_WOOL)!!
        cancelMeta.setDisplayName("<red>Cancel")
        cancelMeta.lore = listOf("<gray>Go back to guild options.")
        cancelItem.itemMeta = cancelMeta
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun sendAllianceRequest(neutralGuild: Guild) {
        val request = diplomacyService.sendRequest(
            fromGuildId = guild.id,
            toGuildId = neutralGuild.id,
            type = net.lumalyte.lg.domain.entities.DiplomaticRequestType.ALLIANCE_REQUEST,
            message = "We would like to propose an alliance between our guilds. Together we can achieve greater success and mutual protection."
        )

        if (request == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to send alliance request. You may already have a pending request.")
        }
    }

    private fun openMessageComposer(neutralGuild: Guild) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Message composer feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would allow you to send custom diplomatic messages to ${neutralGuild.name}")
    }

    private fun openDiplomaticHistory(neutralGuild: Guild) {
        val history = diplomacyService.getDiplomaticHistoryBetweenGuilds(guild.id, neutralGuild.id)
        if (history.isEmpty()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>No diplomatic history with ${neutralGuild.name}")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Found ${history.size} diplomatic events with ${neutralGuild.name}")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Use the diplomatic history menu to view details.")
        }
    }

    private fun calculatePowerLevel(guild: Guild): Int {
        return guild.level * 10 + (guild.level * 5) // Mock calculation
    }

    private fun getDiplomaticHistoryCount(neutralGuild: Guild): Int {
        return diplomacyService.getDiplomaticHistoryBetweenGuilds(guild.id, neutralGuild.id).size
    }

    private fun addNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
            val prevMeta = prevItem.itemMeta ?: org.bukkit.Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
            prevMeta.setDisplayName("<green>Previous Page")
            prevMeta.lore = listOf("<gray>Click to go back")
            prevItem.itemMeta = prevMeta
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open()
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * guildsPerPage < neutralGuilds.size) {
            val nextItem = ItemStack(Material.ARROW)
            val nextMeta = nextItem.itemMeta ?: org.bukkit.Bukkit.getItemFactory().getItemMeta(Material.ARROW)!!
            nextMeta.setDisplayName("<green>Next Page")
            nextMeta.lore = listOf("<gray>Click to go forward")
            nextItem.itemMeta = nextMeta
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open()
            }, 8, 0)
        }

        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: org.bukkit.Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back to Relations")
        backMeta.lore = listOf("<gray>Return to relations menu")
        backItem.itemMeta = backMeta
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)

        // Filter Options
        val filterItem = ItemStack(Material.COMPASS)
        val filterMeta = filterItem.itemMeta ?: org.bukkit.Bukkit.getItemFactory().getItemMeta(Material.COMPASS)!!
        filterMeta.setDisplayName("<aqua>Filter Guilds")
        filterMeta.lore = listOf("<gray>Filter by power level or activity")
        filterItem.itemMeta = filterMeta
        navigationPane.addItem(GuiItem(filterItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Filter feature coming soon!")
        }, 6, 0)
    }
}
