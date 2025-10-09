package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
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
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildRequestAllianceMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                              private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val guildsPerPage = 8
    private var availableGuilds: List<Guild> = emptyList()
    private var selectedGuild: Guild? = null

    override fun open() {
        availableGuilds = loadAvailableGuilds()
        showGuildSelectionMenu()
    }

    private fun loadAvailableGuilds(): List<Guild> {
        val allGuilds = guildService.getAllGuilds()
        val currentRelations = diplomacyService.getRelations(guild.id)

        return allGuilds.filter { targetGuild ->
            targetGuild.id != guild.id && // Don't show own guild
            !currentRelations.any { relation ->
                relation.targetGuildId == targetGuild.id &&
                relation.type.name == "ALLIANCE" &&
                relation.isActive()
            } // Don't show guilds already allied with
        }
    }

    private fun showGuildSelectionMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Request Alliance - Select Guild"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load guilds into main pane
        loadGuildsPage(mainPane)

        // Add navigation
        addGuildSelectionNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadGuildsPage(mainPane: StaticPane) {
        // Show first 36 guilds (4 rows x 9 columns)
        val maxGuilds = min(36, availableGuilds.size)
        val pageGuilds = availableGuilds.take(maxGuilds)

        pageGuilds.forEachIndexed { index, targetGuild ->
            val item = createGuildItem(targetGuild)
            mainPane.addItem(GuiItem(item) { _ ->
                selectedGuild = targetGuild
                openAllianceRequestComposer(targetGuild)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageGuilds.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Guild")
                .lore(listOf("<dark_gray>No guild available in this slot"))

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createGuildItem(targetGuild: Guild): ItemStack {
        val lore = mutableListOf(
            "<gray>Owner: <white>${targetGuild.ownerName}",
            "<gray>Members: <white>${targetGuild.level * 5}",
            "<gray>Level: <white>${targetGuild.level}",
            "<gray>Status: <green>Available for Alliance"
        )

        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<white>${targetGuild.name}")
            .lore(lore)
    }

    private fun openAllianceRequestComposer(targetGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Alliance Request to ${targetGuild.name}"))
        val pane = StaticPane(0, 0, 9, 4)

        // Guild Information
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Target Guild Information")
            .lore(listOf(
                "<gray>Guild: <white>${targetGuild.name}",
                "<gray>Owner: <white>${targetGuild.ownerName}",
                "<gray>Level: <white>${targetGuild.level}",
                "<gray>Members: <white>${targetGuild.level * 5}"
            ))

        pane.addItem(GuiItem(infoItem), 2, 0)

        // Alliance Benefits
        val benefitsItem = ItemStack(Material.DIAMOND)
            .setAdventureName(player, messageService, "<green>Alliance Benefits")
            .lore(listOf(
                "<gray>Mutual protection and support",
                "<gray>Shared resources and assistance",
                "<gray>Joint operations capability",
                "<gray>Diplomatic coordination",
                "<gray>Trade and economic benefits"
            ))

        pane.addItem(GuiItem(benefitsItem), 0, 1)

        // Custom Message Input
        val messageItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<yellow>Custom Message")
            .lore(listOf(
                "<gray>Add a personal message to your request",
                "<gray>Click to compose your message",
                "<gray>Leave blank for default message"
            ))

        pane.addItem(GuiItem(messageItem) { _ ->
            openMessageInput(targetGuild)
        }, 2, 1)

        // Send Request (with default message)
        val sendItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Send Alliance Request")
            .lore(listOf(
                "<gray>Send alliance request with default message",
                "<gray>Target: <white>${targetGuild.name}",
                "<gray>Expires in 7 days",
                "<gray>They can accept or reject"
            ))

        pane.addItem(GuiItem(sendItem) { _ ->
            sendAllianceRequest(targetGuild, null)
        }, 4, 1)

        // Preview Default Message
        val previewItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<aqua>Default Message Preview")
            .lore(listOf(
                "<gray>Message: <white>\"We would like to propose an alliance between our guilds. Together we can achieve greater success and mutual protection.\"",
                "<gray>Character limit: <white>200",
                "<gray>You can customize this message"
            ))

        pane.addItem(GuiItem(previewItem), 6, 1)

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to guild selection"))
        pane.addItem(GuiItem(backItem) { _ ->
            selectedGuild = null
            open()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openMessageInput(targetGuild: Guild) {
        val anvilGui = AnvilGui("Alliance Request Message")

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        // Handle name input changes
        var currentMessage = "We would like to propose an alliance between our guilds. Together we can achieve greater success and mutual protection."
        anvilGui.setOnNameInputChanged { newMessage ->
            currentMessage = newMessage
        }

        // Add confirm button in the right slot
        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Send with Custom Message")
            .lore(listOf("<gray>Click to send request with your message"))

        val guiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            sendAllianceRequest(targetGuild, currentMessage)
            player.closeInventory()
        }
        firstPane.addItem(guiItem, 0, 0)
        anvilGui.firstItemComponent.addPane(firstPane)

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        anvilGui.show(player)
    }

    private fun sendAllianceRequest(targetGuild: Guild, customMessage: String?) {
        if (selectedGuild == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>No guild selected.")
            return
        }

        val request = diplomacyService.sendRequest(
            fromGuildId = guild.id,
            toGuildId = targetGuild.id,
            type = DiplomaticRequestType.ALLIANCE_REQUEST,
            message = customMessage
        )

        if (request != null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Alliance request sent to ${targetGuild.name}!")
            if (!customMessage.isNullOrBlank()) {
                player.sendMessage("<gray>Custom message: \"$customMessage\"")
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>They have 7 days to respond.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to send alliance request. You may already have a pending request.")
        }
    }

    private fun addGuildSelectionNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Previous Page").lore(listOf("<gray>Click to go back"))
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open()
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * guildsPerPage < availableGuilds.size) {
            val nextItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Next Page").lore(listOf("<gray>Click to go forward"))
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open()
            }, 8, 0)
        }

        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<red>Back to Relations").lore(listOf("<gray>Return to relations menu"))
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)

        // Search/Filter
        val searchItem = ItemStack(Material.COMPASS).setAdventureName(player, messageService, "<aqua>Search Guilds").lore(listOf("<gray>Search for specific guilds"))
        navigationPane.addItem(GuiItem(searchItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Search feature coming soon!")
        }, 6, 0)
    }
}
