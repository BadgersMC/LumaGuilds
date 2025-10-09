package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DiplomaticRequest
import net.lumalyte.lg.domain.entities.DiplomaticRequestType
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

class GuildIncomingRequestsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val requestsPerPage = 8
    private var incomingRequests: List<DiplomaticRequest> = emptyList()

    override fun open() {
        incomingRequests = loadIncomingRequests(guild.id)

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<green><green>Incoming Diplomatic Requests - ${guild.name}"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load requests into main pane
        loadRequestsPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadIncomingRequests(guildId: UUID): List<DiplomaticRequest> {
        return diplomacyService.getIncomingRequests(guildId)
    }

    private fun loadRequestsPage(mainPane: StaticPane) {
        // Show first 36 requests (4 rows x 9 columns)
        val maxRequests = min(36, incomingRequests.size)
        val pageRequests = incomingRequests.take(maxRequests)

        pageRequests.forEachIndexed { index, request ->
            val fromGuild = guildService.getGuild(request.fromGuildId)
            if (fromGuild != null) {
                val item = createRequestItem(fromGuild, request)
                mainPane.addItem(GuiItem(item) { _ ->
                    handleRequestAction(fromGuild, request)
                }, index % 9, index / 9)
            }
        }

        // Fill empty slots with placeholder items
        for (i in pageRequests.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Request")
                .lore(listOf("<dark_gray>No diplomatic request in this slot"))

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createRequestItem(fromGuild: Guild, request: DiplomaticRequest): ItemStack {
        val requestedAt = request.requestedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        val expiresAt = if (request.isExpired()) {
            "<red>Expired"
        } else {
            val daysLeft = java.time.Duration.between(Instant.now(), request.expiresAt).toDays()
            "<yellow>Expires in $daysLeft days"
        }

        val requestTypeColor = when (request.type) {
            DiplomaticRequestType.ALLIANCE_REQUEST -> "<green>"
            DiplomaticRequestType.TRUCE_REQUEST -> "<yellow>"
            DiplomaticRequestType.WAR_DECLARATION -> "<red>"
        }

        val lore = mutableListOf(
            "<gray>From Guild: <white>${fromGuild.name}",
            "<gray>Owner: <white>${fromGuild.ownerName}",
            "<gray>Type: ${requestTypeColor}${request.type.name.replace("_", " ")}",
            "<gray>Requested: <white>$requestedAt",
            "<gray>Status: <white>$expiresAt"
        )

        request.message?.takeIf { it.isNotBlank() }?.let {
            lore.add("<gray>Message: <white>\"$it\"")
        }

        lore.add("<yellow>Click to accept or reject")

        val material = when (request.type) {
            DiplomaticRequestType.ALLIANCE_REQUEST -> Material.DIAMOND
            DiplomaticRequestType.TRUCE_REQUEST -> Material.WHITE_BANNER
            DiplomaticRequestType.WAR_DECLARATION -> Material.IRON_SWORD
        }

        return ItemStack(material)
            .setAdventureName(player, messageService, "<white>Request from ${fromGuild.name}")
            .lore(lore)
    }

    private fun handleRequestAction(fromGuild: Guild, request: DiplomaticRequest) {
        openRequestManagementMenu(fromGuild, request)
    }

    private fun openRequestManagementMenu(fromGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Manage Request - ${fromGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Request Information
        val baseLore = listOf(
            "<gray>From Guild: <white>${fromGuild.name}",
            "<gray>Owner: <white>${fromGuild.ownerName}",
            "<gray>Type: <white>${request.type.name.replace("_", " ")}",
            "<gray>Requested: <white>${request.requestedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
            "<gray>Expires: <white>${request.expiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
            if (request.isExpired()) "<gray>Status: <red>Expired" else "<gray>Status: <green>Active"
        )

        val loreWithMessage = request.message?.takeIf { it.isNotBlank() }?.let {
            baseLore + "<gray>Message: <white>\"$it\""
        } ?: baseLore

        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Request Information")
            .lore(loreWithMessage)

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Accept Request
        val acceptItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Accept Request")
            .lore(listOf(
                "<gray>Accept this diplomatic request",
                "<gray>This will create a diplomatic relation",
                "<gray>Requires confirmation"
            ))

        pane.addItem(GuiItem(acceptItem) { _ ->
            openAcceptConfirmation(fromGuild, request)
        }, 1, 1)

        // Reject Request
        val rejectItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>Reject Request")
            .lore(listOf(
                "<gray>Reject this diplomatic request",
                "<gray>The request will be removed",
                "<gray>Requires confirmation"
            ))

        pane.addItem(GuiItem(rejectItem) { _ ->
            openRejectConfirmation(fromGuild, request)
        }, 3, 1)

        // View Guild Info
        val viewItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<yellow>View Guild Information")
            .lore(listOf(
                "<gray>View detailed information about ${fromGuild.name}",
                "<gray>See their diplomatic status and relations"
            ))

        pane.addItem(GuiItem(viewItem) { _ ->
            openGuildInfo(fromGuild, request)
        }, 5, 1)

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to incoming requests"))
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openAcceptConfirmation(fromGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Accept Request Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Confirm Acceptance")
            .lore(listOf("<gray>Are you sure you want to accept the ${request.type.name.lowercase().replace("_", " ")} from ${fromGuild.name}?"))

        pane.addItem(GuiItem(confirmItem) { _ ->
            performAcceptRequest(fromGuild, request)
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Diplomatic request from ${fromGuild.name} has been accepted.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>Cancel")
            .lore(listOf("<gray>Go back to request management."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openRejectConfirmation(fromGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Reject Request Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>Confirm Rejection")
            .lore(listOf("<gray>Are you sure you want to reject the ${request.type.name.lowercase().replace("_", " ")} from ${fromGuild.name}?"))

        pane.addItem(GuiItem(confirmItem) { _ ->
            performRejectRequest(fromGuild, request)
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Diplomatic request from ${fromGuild.name} has been rejected.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.GRAY_WOOL)
            .setAdventureName(player, messageService, "<green>Cancel")
            .lore(listOf("<gray>Go back to request management."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun performAcceptRequest(fromGuild: Guild, request: DiplomaticRequest) {
        val success = diplomacyService.acceptRequest(request.id, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully accepted the diplomatic request from ${fromGuild.name}.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to accept the diplomatic request. Please try again.")
        }
    }

    private fun performRejectRequest(fromGuild: Guild, request: DiplomaticRequest) {
        val success = diplomacyService.rejectRequest(request.id, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Successfully rejected the diplomatic request from ${fromGuild.name}.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to reject the diplomatic request. Please try again.")
        }
    }

    private fun openGuildInfo(fromGuild: Guild, request: DiplomaticRequest) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Guild information feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show detailed information about ${fromGuild.name}")
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
        if ((currentPage + 1) * requestsPerPage < incomingRequests.size) {
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

        // Refresh
        val refreshItem = ItemStack(Material.COMPASS).setAdventureName(player, messageService, "<green>Refresh").lore(listOf("<gray>Refresh requests list"))
        navigationPane.addItem(GuiItem(refreshItem) { _ ->
            open() // Reopen to refresh data
        }, 6, 0)
    }
}
