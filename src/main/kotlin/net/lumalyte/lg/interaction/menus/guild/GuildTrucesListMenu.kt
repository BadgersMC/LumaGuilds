package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DiplomaticRelation
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
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

class GuildTrucesListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                          private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val trucesPerPage = 8
    private var allTruces: List<DiplomaticRelation> = emptyList()

    override fun open() {
        // Load active truces for this guild
        allTruces = loadTruces(guild.id)

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Truces List - ${guild.name}"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load truces into main pane
        loadTrucesPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadTruces(guildId: UUID): List<DiplomaticRelation> {
        // Get relations and filter for active truces
        val relations = diplomacyService.getRelations(guildId)
        return relations.filter { it.type == DiplomaticRelationType.TRUCE && it.isActive() }
    }

    private fun loadTrucesPage(mainPane: StaticPane) {
        // Show first 36 truces (4 rows x 9 columns)
        val maxTruces = min(36, allTruces.size)
        val pageTruces = allTruces.take(maxTruces)

        pageTruces.forEachIndexed { index, relation ->
            val targetGuild = guildService.getGuild(relation.targetGuildId)
            if (targetGuild != null) {
                val item = createTruceItem(targetGuild, relation)
                mainPane.addItem(GuiItem(item) { _ ->
                    manageTruce(targetGuild, relation)
                }, index % 9, index / 9)
            }
        }
    }

    private fun createTruceItem(targetGuild: Guild, relation: DiplomaticRelation): ItemStack {
        val establishedAt = relation.establishedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        val expiresAt = relation.expiresAt?.let { expires ->
            if (expires.isAfter(Instant.now())) {
                val daysLeft = java.time.Duration.between(Instant.now(), expires).toDays()
                "<red>Expires in $daysLeft days"
            } else {
                "<red>Expired"
            }
        } ?: "<red>No expiration"

        val lore = listOf(
            "<gray>Owner: <white>${targetGuild.ownerName}",
            "<gray>Established: <white>$establishedAt",
            "<gray>Status: $expiresAt",
            "<gray>Members: <white>${targetGuild.level * 5}", // Mock member count
            "<yellow>Click to extend or break truce"
        )

        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<yellow>${targetGuild.name}")
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
        if ((currentPage + 1) * trucesPerPage < allTruces.size) {
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

    private fun manageTruce(targetGuild: Guild, relation: DiplomaticRelation) {
        openTruceManagementMenu(targetGuild, relation)
    }

    private fun openTruceManagementMenu(targetGuild: Guild, relation: DiplomaticRelation) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Truce Management - ${targetGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Truce Information
        val infoItem = ItemStack(Material.BOOK).setAdventureName(player, messageService, "<aqua>Truce Information")
            .lore(listOf(
                "<gray>Target Guild: <white>${targetGuild.name}",
                "<gray>Established: <white>${relation.establishedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
                "<gray>Expires: <white>${relation.expiresAt?.let { expires ->
                    if (expires.isAfter(Instant.now())) {
                        "In ${java.time.Duration.between(Instant.now(), expires).toDays()} days"
                    } else {
                        "Expired"
                    }
                } ?: "Never"}",
                "<gray>Status: <white>${if (relation.isActive()) "Active" else "Expired"}"
            ))
        pane.addItem(GuiItem(infoItem), 2, 0)

        // Extend Truce
        val extendItem = ItemStack(Material.LIME_WOOL).setAdventureName(player, messageService, "<green>Extend Truce")
            .lore(listOf(
                "<gray>Extend this truce by 7 days",
                "<gray>Maintain peaceful relations",
                "<gray>Requires confirmation"
            ))
        pane.addItem(GuiItem(extendItem) { _ ->
            openExtendTruceConfirmation(targetGuild, relation)
        }, 1, 1)

        // Break Truce
        val breakItem = ItemStack(Material.RED_WOOL).setAdventureName(player, messageService, "<red>Break Truce")
            .lore(listOf(
                "<gray>Immediately end this truce",
                "<gray>May lead to war declaration",
                "<gray>Requires confirmation"
            ))
        pane.addItem(GuiItem(breakItem) { _ ->
            openBreakTruceConfirmation(targetGuild, relation)
        }, 3, 1)

        // View History
        val historyItem = ItemStack(Material.PAPER).setAdventureName(player, messageService, "<yellow>View History")
            .lore(listOf(
                "<gray>View diplomatic history",
                "<gray>See past interactions",
                "<gray>Track relationship changes"
            ))
        pane.addItem(GuiItem(historyItem) { _ ->
            openDiplomaticHistory(targetGuild)
        }, 5, 1)

        // Back
        val backItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to truces list"))
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openExtendTruceConfirmation(targetGuild: Guild, relation: DiplomaticRelation) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Extend Truce Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.LIME_WOOL).setAdventureName(player, messageService, "<green>Confirm Extension")
            .lore(listOf("<gray>Extend truce with ${targetGuild.name} by 7 days?"))
        pane.addItem(GuiItem(confirmItem) { _ ->
            performExtendTruce(targetGuild, relation)
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Truce with ${targetGuild.name} has been extended by 7 days.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.RED_WOOL).setAdventureName(player, messageService, "<red>Cancel")
            .lore(listOf("<gray>Go back to truce management."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openBreakTruceConfirmation(targetGuild: Guild, relation: DiplomaticRelation) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Break Truce Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.RED_WOOL).setAdventureName(player, messageService, "<red>Confirm Break Truce")
            .lore(listOf("<gray>Are you sure you want to break the truce with ${targetGuild.name}?"))
            .lore(listOf("<gray>This will immediately end the truce agreement."))
        pane.addItem(GuiItem(confirmItem) { _ ->
            performBreakTruce(targetGuild, relation)
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Truce with ${targetGuild.name} has been broken.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.GRAY_WOOL).setAdventureName(player, messageService, "<green>Cancel")
            .lore(listOf("<gray>Go back to truce management."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun performExtendTruce(targetGuild: Guild, relation: DiplomaticRelation) {
        // Extend truce by 7 days (604800000 milliseconds)
        val newExpiresAt = (relation.expiresAt ?: relation.establishedAt).plusSeconds(604800)
        val updates = mapOf("expires_at" to newExpiresAt)

        val success = diplomacyService.updateRelation(relation.id, updates)
        if (success) {
            // Log the diplomatic event
            diplomacyService.logDiplomaticEvent(
                guild.id,
                targetGuild.id,
                "truce_extended",
                "Truce extended by 7 days"
            )
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to extend truce. Please try again.")
        }
    }

    private fun performBreakTruce(targetGuild: Guild, relation: DiplomaticRelation) {
        // For truces, we need to break the relation and potentially create an enemy relation
        val success = diplomacyService.breakRelation(guild.id, targetGuild.id)
        if (success) {
            // Log the diplomatic event
            diplomacyService.logDiplomaticEvent(
                guild.id,
                targetGuild.id,
                "truce_broken",
                "Truce agreement broken"
            )
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to break truce. Please try again.")
        }
    }

    private fun openDiplomaticHistory(targetGuild: Guild) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Diplomatic history feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show the diplomatic history with ${targetGuild.name}")
    }
}
