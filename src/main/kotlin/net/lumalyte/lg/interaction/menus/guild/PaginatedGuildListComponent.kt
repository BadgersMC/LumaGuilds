package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * A reusable component for displaying a paginated list of guilds with filters.
 * Used for diplomacy/war menus.
 */
class PaginatedGuildListComponent(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val title: String,
    private val onGuildSelect: (Guild, messageService: MessageService) -> Unit,
    private val filter: (Guild) -> Boolean = { true }
) : KoinComponent {

    private val guildService: GuildService by inject()

    private var currentPage = 0
    private val guildsPerPage = 8
    private var allGuilds: List<Guild> = emptyList()

    fun open(messageService: MessageService) {
        allGuilds = loadGuilds().filter(filter)

        val gui = ChestGui(5, AdventureMenuHelper.createMenuTitle(player, messageService, title))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load guilds into main pane
        loadGuildsPage(mainPane, messageService)

        // Add navigation
        addNavigation(navigationPane, messageService)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadGuilds(): List<Guild> {
        // TODO: Implement actual guild list loading from GuildService - Task 2.2
        // For now, return mock data or all guilds
        return listOf(
            // Mock guilds; replace with guildService.getAllGuilds()
        )
    }

    private fun loadGuildsPage(mainPane: StaticPane, messageService: MessageService) {
        val startIndex = currentPage * guildsPerPage
        val endIndex = minOf(startIndex + guildsPerPage, allGuilds.size)
        val pageGuilds = allGuilds.subList(startIndex, endIndex)

        pageGuilds.forEachIndexed { index, guild ->
            val item = createGuildItem(guild, messageService)
            mainPane.addItem(GuiItem(item) { _ ->
                onGuildSelect(guild, messageService)
            }, index % 9, index / 9)
        }
    }

    private fun createGuildItem(guild: Guild, messageService: MessageService): ItemStack {
        val lore = listOf(
            "<gray>Owner: <white>${guild.ownerName}",
            "<gray>Members: <white>${guild.level * 5}", // Mock member count
            "<yellow>Click to select"
        )
        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<green>${guild.name}")
            .addAdventureLore(player, messageService, *lore.toTypedArray())
    }

    private fun addNavigation(navigationPane: StaticPane, messageService: MessageService) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Previous Page").addAdventureLore(player, messageService, "<gray>Click to go back")
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open(messageService)
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * guildsPerPage < allGuilds.size) {
            val nextItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Next Page").addAdventureLore(player, messageService, "<gray>Click to go forward")
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open(messageService)
            }, 8, 0)
        }

        // Back
        val backItem = ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<red>Back").addAdventureLore(player, messageService, "<gray>Return to previous menu")
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)
    }
}
