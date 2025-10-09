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
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildRequestTruceMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val guildsPerPage = 8
    private var enemyGuilds: List<Guild> = emptyList()
    private var selectedGuild: Guild? = null
    private var selectedDuration: Duration = Duration.ofDays(7) // Default 7 days

    override fun open() {
        enemyGuilds = loadEnemyGuilds()
        showEnemySelectionMenu()
    }

    private fun loadEnemyGuilds(): List<Guild> {
        val allGuilds = guildService.getAllGuilds()
        val currentRelations = diplomacyService.getRelations(guild.id)

        return allGuilds.filter { targetGuild ->
            targetGuild.id != guild.id && // Don't show own guild
            currentRelations.any { relation ->
                relation.targetGuildId == targetGuild.id &&
                relation.type.name == "ENEMY" &&
                relation.isActive()
            } // Only show guilds that are enemies
        }
    }

    private fun showEnemySelectionMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Request Truce - Select Enemy"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load enemies into main pane
        loadEnemiesPage(mainPane)

        // Add navigation
        addEnemySelectionNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadEnemiesPage(mainPane: StaticPane) {
        // Show first 36 enemies (4 rows x 9 columns)
        val maxEnemies = min(36, enemyGuilds.size)
        val pageEnemies = enemyGuilds.take(maxEnemies)

        pageEnemies.forEachIndexed { index, enemyGuild ->
            val item = createEnemyItem(enemyGuild)
            mainPane.addItem(GuiItem(item) { _ ->
                selectedGuild = enemyGuild
                openTruceComposer(enemyGuild)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageEnemies.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Enemy")
                .lore(listOf("<dark_gray>No enemy guild in this slot"))

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createEnemyItem(enemyGuild: Guild): ItemStack {
        val lore = mutableListOf(
            "<gray>Owner: <white>${enemyGuild.ownerName}",
            "<gray>Members: <white>${enemyGuild.level * 5}",
            "<gray>Level: <white>${enemyGuild.level}",
            "<gray>Status: <red>Enemy Guild"
        )

        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<red>${enemyGuild.name}")
            .lore(lore)
    }

    private fun openTruceComposer(enemyGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Truce Request to ${enemyGuild.name}"))
        val mainPane = StaticPane(0, 0, 9, 5)

        // Enemy Guild Information
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Enemy Guild Information")
            .lore(listOf(
                "<gray>Guild: <red>${enemyGuild.name}",
                "<gray>Owner: <white>${enemyGuild.ownerName}",
                "<gray>Level: <white>${enemyGuild.level}",
                "<gray>Members: <white>${enemyGuild.level * 5}"
            ))

        mainPane.addItem(GuiItem(infoItem), 2, 0)

        // Truce Duration Options
        addDurationOptions(mainPane, enemyGuild)

        // Truce Benefits
        val benefitsItem = ItemStack(Material.WHITE_BANNER)
            .setAdventureName(player, messageService, "<green>Truce Benefits")
            .lore(listOf(
                "<gray>Temporary cessation of hostilities",
                "<gray>Time to negotiate peace",
                "<gray>Protects both guilds from attacks",
                "<gray>Can be extended or made permanent",
                "<gray>Allows diplomatic communication"
            ))

        mainPane.addItem(GuiItem(benefitsItem), 0, 2)

        // Custom Message Input
        val messageItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<yellow>Custom Message")
            .lore(listOf(
                "<gray>Add terms or conditions to your truce",
                "<gray>Click to compose your message",
                "<gray>Leave blank for default message"
            ))

        mainPane.addItem(GuiItem(messageItem) { _ ->
            openMessageInput(enemyGuild)
        }, 2, 2)

        // Send Request
        val sendItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Send Truce Request")
            .lore(listOf(
                "<gray>Send truce request to ${enemyGuild.name}",
                "<gray>Duration: <white>${formatDuration(selectedDuration)}",
                "<gray>Target can accept or reject",
                "<gray>Expires in 7 days if not responded"
            ))

        mainPane.addItem(GuiItem(sendItem) { _ ->
            sendTruceRequest(enemyGuild, null)
        }, 4, 2)

        // Preview Default Message
        val previewItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<aqua>Default Message Preview")
            .lore(listOf(
                "<gray>Message: <white>\"We propose a temporary truce for ${formatDuration(selectedDuration)}. During this time, we will cease all hostile actions and open diplomatic channels.\"",
                "<gray>Character limit: <white>300",
                "<gray>You can customize this message"
            ))

        mainPane.addItem(GuiItem(previewItem), 6, 2)

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to enemy selection"))
        mainPane.addItem(GuiItem(backItem) { _ ->
            selectedGuild = null
            open()
        }, 4, 4)

        gui.addPane(mainPane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun addDurationOptions(mainPane: StaticPane, enemyGuild: Guild) {
        val durations = listOf(
            Duration.ofDays(3) to "<yellow>3 Days",
            Duration.ofDays(7) to "<green>7 Days",
            Duration.ofDays(14) to "<aqua>2 Weeks",
            Duration.ofDays(30) to "<light_purple>1 Month"
        )

        durations.forEachIndexed { index, (duration, name) ->
            val isSelected = duration == selectedDuration
            val material = if (isSelected) Material.LIME_WOOL else Material.WHITE_WOOL
            val itemName = if (isSelected) "<green>✓ $name" else "<gray>$name"

            val item = ItemStack(material)
                .name(itemName)
                .lore(listOf(
                    "<gray>Truce Duration: <white>${formatDuration(duration)}",
                    "<gray>Click to select this duration",
                    if (isSelected) "<green>Currently selected" else "<gray>Click to select"
                ))

            mainPane.addItem(GuiItem(item) { _ ->
                selectedDuration = duration
                openTruceComposer(enemyGuild) // Refresh to show selection
            }, index + 1, 1)
        }
    }

    private fun openMessageInput(enemyGuild: Guild) {
        val anvilGui = AnvilGui("Truce Request Message")

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        // Handle name input changes
        var currentMessage = "We propose a temporary truce for ${formatDuration(selectedDuration)}. During this time, we will cease all hostile actions and open diplomatic channels."
        anvilGui.setOnNameInputChanged { newMessage ->
            currentMessage = newMessage
        }

        // Add confirm button in the right slot
        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.LIME_WOOL)
            .setAdventureName(player, messageService, "<green>Send with Custom Message")
            .lore(listOf("<gray>Click to send request with your message"))

        val guiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            sendTruceRequest(enemyGuild, currentMessage)
            player.closeInventory()
        }
        firstPane.addItem(guiItem, 0, 0)
        anvilGui.firstItemComponent.addPane(firstPane)

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        anvilGui.show(player)
    }

    private fun sendTruceRequest(enemyGuild: Guild, customMessage: String?) {
        if (selectedGuild == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>No enemy guild selected.")
            return
        }

        val request = diplomacyService.sendRequest(
            fromGuildId = guild.id,
            toGuildId = enemyGuild.id,
            type = DiplomaticRequestType.TRUCE_REQUEST,
            message = customMessage
        )

        if (request != null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Truce request sent to ${enemyGuild.name}!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Duration: ${formatDuration(selectedDuration)}")
            if (!customMessage.isNullOrBlank()) {
                player.sendMessage("<gray>Custom message: \"$customMessage\"")
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>They have 7 days to respond.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to send truce request. You may already have a pending request.")
        }
    }

    private fun formatDuration(duration: Duration): String {
        return when {
            duration.toDays() > 0 -> "${duration.toDays()} days"
            duration.toHours() > 0 -> "${duration.toHours()} hours"
            else -> "${duration.toMinutes()} minutes"
        }
    }

    private fun addEnemySelectionNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Previous Page").lore(listOf("<gray>Click to go back"))
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open()
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * guildsPerPage < enemyGuilds.size) {
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

        // Filter by War Status
        val filterItem = ItemStack(Material.COMPASS).setAdventureName(player, messageService, "<aqua>Filter Options").lore(listOf("<gray>Filter by war status or activity"))
        navigationPane.addItem(GuiItem(filterItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Filter feature coming soon!")
        }, 6, 0)
    }
}
