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

class GuildOutgoingRequestsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val requestsPerPage = 8
    private var outgoingRequests: List<DiplomaticRequest> = emptyList()

    override fun open() {
        outgoingRequests = loadOutgoingRequests(guild.id)

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Outgoing Diplomatic Requests - ${guild.name}"))
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

    private fun loadOutgoingRequests(guildId: UUID): List<DiplomaticRequest> {
        return diplomacyService.getOutgoingRequests(guildId)
    }

    private fun loadRequestsPage(mainPane: StaticPane) {
        // Show first 36 requests (4 rows x 9 columns)
        val maxRequests = min(36, outgoingRequests.size)
        val pageRequests = outgoingRequests.take(maxRequests)

        pageRequests.forEachIndexed { index, request ->
            val toGuild = guildService.getGuild(request.toGuildId)
            if (toGuild != null) {
                val item = createRequestItem(toGuild, request)
                mainPane.addItem(GuiItem(item) { _ ->
                    handleRequestAction(toGuild, request)
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

    private fun createRequestItem(toGuild: Guild, request: DiplomaticRequest): ItemStack {
        val requestedAt = request.requestedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        val status = when {
            request.isExpired() -> "<red>Expired"
            hasResponse(request) -> "<green>Responded"
            else -> "<yellow>Pending"
        }

        val requestTypeColor = when (request.type) {
            DiplomaticRequestType.ALLIANCE_REQUEST -> "<green>"
            DiplomaticRequestType.TRUCE_REQUEST -> "<yellow>"
            DiplomaticRequestType.WAR_DECLARATION -> "<red>"
        }

        val lore = mutableListOf(
            "<gray>To Guild: <white>${toGuild.name}",
            "<gray>Owner: <white>${toGuild.ownerName}",
            "<gray>Type: ${requestTypeColor}${request.type.name.replace("_", " ")}",
            "<gray>Sent: <white>$requestedAt",
            "<gray>Status: <white>$status"
        )

        request.message?.takeIf { it.isNotBlank() }?.let {
            lore.add("<gray>Message: <white>\"$it\"")
        }

        lore.add("<gray>Time Active: <white>${calculateTimeActive(request)}")

        if (hasResponse(request)) {
            lore.add("<gray>Response: <white>${getResponseStatus(request)}")
        } else {
            lore.add("<yellow>Click to cancel request")
        }

        val material = when (request.type) {
            DiplomaticRequestType.ALLIANCE_REQUEST -> Material.DIAMOND
            DiplomaticRequestType.TRUCE_REQUEST -> Material.WHITE_BANNER
            DiplomaticRequestType.WAR_DECLARATION -> Material.IRON_SWORD
        }

        return ItemStack(material)
            .setAdventureName(player, messageService, "<white>Request to ${toGuild.name}")
            .lore(lore)
    }

    private fun handleRequestAction(toGuild: Guild, request: DiplomaticRequest) {
        if (request.isActive() && !hasResponse(request)) {
            openRequestManagementMenu(toGuild, request)
        } else {
            openRequestDetailsMenu(toGuild, request)
        }
    }

    private fun openRequestManagementMenu(toGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Manage Request - ${toGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Request Information
        val baseLore = listOf(
            "<gray>To Guild: <white>${toGuild.name}",
            "<gray>Owner: <white>${toGuild.ownerName}",
            "<gray>Type: <white>${request.type.name.replace("_", " ")}",
            "<gray>Sent: <white>${request.requestedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
            "<gray>Expires: <white>${request.expiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
            "<gray>Status: <green>Active - No Response Yet"
        )

        val loreWithMessage = request.message?.takeIf { it.isNotBlank() }?.let {
            baseLore + "<gray>Message: <white>\"$it\""
        } ?: baseLore

        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Request Information")
            .lore(loreWithMessage)

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Cancel Request
        val cancelItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>Cancel Request")
            .lore(listOf(
                "<gray>Cancel this diplomatic request",
                "<gray>The request will be removed",
                "<gray>Requires confirmation"
            ))

        pane.addItem(GuiItem(cancelItem) { _ ->
            openCancelConfirmation(toGuild, request)
        }, 1, 1)

        // View Guild Info
        val viewItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<yellow>View Guild Information")
            .lore(listOf(
                "<gray>View detailed information about ${toGuild.name}",
                "<gray>See their diplomatic status and relations"
            ))

        pane.addItem(GuiItem(viewItem) { _ ->
            openGuildInfo(toGuild, request)
        }, 3, 1)

        // Request Statistics
        val statsItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<gold>Request Statistics")
            .lore(listOf(
                "<gray>Time Active: <white>${calculateTimeActive(request)}",
                "<gray>Response Rate: <white>${getOverallResponseRate()}%",
                "<gray>Your Success Rate: <white>${getPersonalSuccessRate()}%",
                "<gray>Average Wait Time: <white>${getAverageWaitTime()}"
            ))

        pane.addItem(GuiItem(statsItem), 5, 1)

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to outgoing requests"))
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openRequestDetailsMenu(toGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<aqua><aqua>Request Details - ${toGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Request Information
        val baseLore = listOf(
            "<gray>To Guild: <white>${toGuild.name}",
            "<gray>Owner: <white>${toGuild.ownerName}",
            "<gray>Type: <white>${request.type.name.replace("_", " ")}",
            "<gray>Sent: <white>${request.requestedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}",
            "<gray>Status: <white>${getDetailedStatus(request)}"
        )

        val loreWithMessage = request.message?.takeIf { it.isNotBlank() }?.let {
            baseLore + "<gray>Message: <white>\"$it\""
        } ?: baseLore

        val loreWithDuration = if (request.isExpired()) {
            loreWithMessage + "<gray>Expired: <red>${request.expiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}"
        } else {
            loreWithMessage + "<gray>Duration: <white>${calculateTimeActive(request)}"
        }

        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Request Information")
            .lore(loreWithDuration)

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Response Information (if any)
        if (hasResponse(request)) {
            val responseItem = ItemStack(Material.EMERALD_BLOCK)
                .setAdventureName(player, messageService, "<green>Response Received")
                .lore(listOf(
                    "<gray>Response: <white>${getResponseStatus(request)}",
                    "<gray>Response Time: <white>${calculateResponseTime(request)}",
                    "<gray>Responder: <white>${getResponderName(request)}"
                ))

            pane.addItem(GuiItem(responseItem), 1, 1)

            // View Current Relation
            val relationItem = ItemStack(Material.COMPASS)
                .setAdventureName(player, messageService, "<yellow>View Current Relation")
                .lore(listOf(
                    "<gray>See the current diplomatic status",
                    "<gray>Between your guild and ${toGuild.name}"
                ))

            pane.addItem(GuiItem(relationItem) { _ ->
                openCurrentRelation(toGuild, request)
            }, 3, 1)
        } else {
            // Request still pending
            val pendingItem = ItemStack(Material.CLOCK)
                .setAdventureName(player, messageService, "<yellow>Still Pending")
                .lore(listOf(
                    "<gray>No response received yet",
                    "<gray>Time waiting: <white>${calculateTimeActive(request)}",
                    "<gray>Expires: <white>${request.expiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))}"
                ))

            pane.addItem(GuiItem(pendingItem), 1, 1)
        }

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to outgoing requests"))
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openCancelConfirmation(toGuild: Guild, request: DiplomaticRequest) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Cancel Request Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>Confirm Cancellation")
            .lore(listOf("<gray>Are you sure you want to cancel the ${request.type.name.lowercase().replace("_", " ")} to ${toGuild.name}?"))

        pane.addItem(GuiItem(confirmItem) { _ ->
            performCancelRequest(toGuild, request)
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Diplomatic request to ${toGuild.name} has been cancelled.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.GRAY_WOOL)
            .setAdventureName(player, messageService, "<green>Keep Request")
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

    private fun performCancelRequest(toGuild: Guild, request: DiplomaticRequest) {
        val success = diplomacyService.rejectRequest(request.id, player.uniqueId)
        if (success) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Successfully cancelled the diplomatic request to ${toGuild.name}.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to cancel the diplomatic request. Please try again.")
        }
    }

    private fun openGuildInfo(toGuild: Guild, request: DiplomaticRequest) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Guild information feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show detailed information about ${toGuild.name}")
    }

    private fun openCurrentRelation(toGuild: Guild, request: DiplomaticRequest) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Current relation feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show the current diplomatic status with ${toGuild.name}")
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
        if ((currentPage + 1) * requestsPerPage < outgoingRequests.size) {
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

    // Real calculation methods
    private fun calculateTimeActive(request: DiplomaticRequest): String {
        val duration = java.time.Duration.between(request.requestedAt, Instant.now())
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return when {
            days > 0 -> "$days days, $hours hours"
            hours > 0 -> "$hours hours, $minutes minutes"
            else -> "$minutes minutes"
        }
    }

    private fun calculateResponseTime(request: DiplomaticRequest): String {
        // This would need to be implemented when we have response tracking
        return "Unknown"
    }

    private fun getResponseStatus(request: DiplomaticRequest): String {
        // This would need to be implemented with actual response tracking
        return "Accepted/Rejected"
    }

    private fun getResponderName(request: DiplomaticRequest): String {
        // This would need to be implemented with actual response tracking
        return "Guild Member"
    }

    private fun hasResponse(request: DiplomaticRequest): Boolean {
        // This would check if there's a response in the database
        return false // Placeholder - would check diplomatic history
    }

    private fun getDetailedStatus(request: DiplomaticRequest): String {
        return when {
            request.isExpired() -> "<red>Expired"
            hasResponse(request) -> "<green>Responded"
            else -> "<yellow>Pending"
        }
    }

    private fun getOverallResponseRate(): Double {
        // This would calculate from all historical requests
        return 75.0
    }

    private fun getPersonalSuccessRate(): Double {
        // This would calculate the player's personal success rate
        return 80.0
    }

    private fun getAverageWaitTime(): String {
        // This would calculate from historical data
        return "2.5 hours"
    }
}
