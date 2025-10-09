package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.domain.entities.DiplomaticRelation
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildAlliesListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                          private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val alliesPerPage = 8
    private var allAllies: List<DiplomaticRelation> = emptyList()

    override fun open() {
        // For now, load mock allies; later integrate with DiplomacyService
        allAllies = loadAllies(guild.id)

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Allies List"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load allies into main pane
        loadAlliesPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadAllies(guildId: UUID): List<DiplomaticRelation> {
        // Mock data for now; replace with DiplomacyService.getRelations(guildId).filter { it.type == DiplomaticRelationType.ALLIANCE }
        return listOf(
            DiplomaticRelation(UUID.randomUUID(), guildId, UUID.randomUUID(), DiplomaticRelationType.ALLIANCE, Instant.now().minusSeconds(86400)),
            DiplomaticRelation(UUID.randomUUID(), guildId, UUID.randomUUID(), DiplomaticRelationType.ALLIANCE, Instant.now().minusSeconds(172800))
        )
    }

    private fun loadAlliesPage(mainPane: StaticPane) {
        // For now, show first 36 allies (4 rows x 9 columns)
        val maxAllies = min(36, allAllies.size)
        val pageAllies = allAllies.take(maxAllies)

        pageAllies.forEachIndexed { index, relation ->
            val allyGuild = guildService.getGuild(relation.targetGuildId) // Assuming GuildService has this method
            if (allyGuild != null) {
                val item = createAllyItem(allyGuild, relation)
                mainPane.addItem(GuiItem(item), index % 9, index / 9)
            }
        }
    }

    private fun createAllyItem(allyGuild: Guild, relation: DiplomaticRelation): ItemStack {
        val establishedAt = relation.establishedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        val lore = listOf(
            "<gray>Owner: <white>${allyGuild.ownerName}",
            "<gray>Established: <white>$establishedAt",
            "<gray>Members: <white>${allyGuild.level * 5}", // Mock member count
            "<yellow>Click to view details or break alliance"
        )
        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<green>${allyGuild.name}")
            .lore(lore)
    }

    private fun addNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Previous Page").lore(listOf("<gray>Click to go back"))
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open() // Reopen with new page
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * alliesPerPage < allAllies.size) {
            val nextItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Next Page").lore(listOf("<gray>Click to go forward"))
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open() // Reopen with new page
            }, 8, 0)
        }

        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<red>Back to Relations").lore(listOf("<gray>Return to relations menu"))
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)
    }

    private fun breakAlliance(allyGuild: Guild) {
        openBreakAllianceConfirmation(allyGuild)
    }

    private fun openBreakAllianceConfirmation(allyGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Break Alliance Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.LIME_WOOL).setAdventureName(player, messageService, "<green>Confirm Break Alliance")
            .lore(listOf("<gray>Are you sure you want to break the alliance with ${allyGuild.name}?"))
        pane.addItem(GuiItem(confirmItem) { _ ->
            // Perform the break alliance action
            performBreakAlliance(allyGuild)
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Alliance with ${allyGuild.name} has been broken.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.RED_WOOL).setAdventureName(player, messageService, "<red>Cancel")
            .lore(listOf("<gray>Go back to the allies list."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun performBreakAlliance(allyGuild: Guild) {
        val success = diplomacyService.breakAlliance(guild.id, allyGuild.id)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Alliance with ${allyGuild.name} has been broken.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to break alliance. Please try again.")
        }
    }
}
