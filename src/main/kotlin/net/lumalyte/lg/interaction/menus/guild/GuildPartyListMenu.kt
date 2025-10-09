package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
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

class GuildPartyListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val partyService: PartyService by inject()

    private var currentPage = 0
    private val partiesPerPage = 8
    private var allParties: List<Party> = emptyList()

    override fun open() {
        allParties = loadParties()

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Party Directory - ${guild.name}"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load parties into main pane
        loadPartiesPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadParties(): List<Party> {
        return partyService.getActivePartiesForGuild(guild.id).toList()
    }

    private fun loadPartiesPage(mainPane: StaticPane) {
        // Show first 36 parties (4 rows x 9 columns)
        val maxParties = min(36, allParties.size)
        val pageParties = allParties.take(maxParties)

        pageParties.forEachIndexed { index, party ->
            val item = createPartyItem(party)
            mainPane.addItem(GuiItem(item) { _ ->
                openPartyDetails(party)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageParties.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
            val placeholderMeta = placeholderItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.GRAY_STAINED_GLASS_PANE)!!
            placeholderMeta.setDisplayName("<gray>No Party")
            placeholderMeta.lore = listOf("<dark_gray>No party in this slot")
            placeholderItem.itemMeta = placeholderMeta

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createPartyItem(party: Party): ItemStack {
        val status = getPartyStatusText(party.status)
        val leaderGuild = getPartyLeaderGuild(party)
        val memberCount = getPartyMemberCount(party)

        val lore = mutableListOf(
            "<gray>Status: <white>$status",
            "<gray>Leader: <white>${leaderGuild?.name ?: "Unknown"}",
            "<gray>Members: <white>$memberCount guilds",
            "<gray>Created: <white>${formatTimestamp(party.createdAt)}",
            "<gray>Expires: <white>${party.expiresAt?.let { formatTimestamp(it) } ?: "Never"}"
        )

        if (party.name != null) {
            lore.add(0, "<gray>Name: <white>${party.name}")
        }

        lore.add("<yellow>Click to view details")

        val material = when (party.status) {
            net.lumalyte.lg.domain.entities.PartyStatus.ACTIVE -> Material.DIAMOND_BLOCK
            net.lumalyte.lg.domain.entities.PartyStatus.DISSOLVED -> Material.REDSTONE_BLOCK
            net.lumalyte.lg.domain.entities.PartyStatus.EXPIRED -> Material.COAL_BLOCK
        }

        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)!!
        meta.setDisplayName("<white>${party.name ?: "Unnamed Party"}")
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun openPartyDetails(party: Party) {
        val detailsMenu = GuildPartyDetailsMenu(menuNavigator, player, guild, party, messageService)
        detailsMenu.open()
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
        if ((currentPage + 1) * partiesPerPage < allParties.size) {
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

        // Back to Party Management
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back to Party Management")
        backMeta.lore = listOf("<gray>Return to party management")
        backItem.itemMeta = backMeta
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)

        // Refresh
        val refreshItem = ItemStack(Material.LIME_DYE)
        val refreshMeta = refreshItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.LIME_DYE)!!
        refreshMeta.setDisplayName("<green>Refresh")
        refreshMeta.lore = listOf("<gray>Refresh party list")
        refreshItem.itemMeta = refreshMeta
        navigationPane.addItem(GuiItem(refreshItem) { _ ->
            open()
        }, 6, 0)
    }

    // Helper methods
    private fun getPartyStatusText(status: net.lumalyte.lg.domain.entities.PartyStatus): String {
        return when (status) {
            net.lumalyte.lg.domain.entities.PartyStatus.ACTIVE -> "<green>Active"
            net.lumalyte.lg.domain.entities.PartyStatus.DISSOLVED -> "<red>Dissolved"
            net.lumalyte.lg.domain.entities.PartyStatus.EXPIRED -> "<yellow>Expired"
        }
    }

    private fun getPartyLeaderGuild(party: Party): Guild? {
        return guildService.getGuild(party.leaderId)
    }

    private fun getPartyMemberCount(party: Party): Int {
        return party.guildIds.size
    }

    private fun formatTimestamp(timestamp: java.time.Instant): String {
        return timestamp.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }
}
