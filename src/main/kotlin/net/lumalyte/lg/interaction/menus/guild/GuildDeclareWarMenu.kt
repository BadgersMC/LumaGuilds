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

class GuildDeclareWarMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    private var currentPage = 0
    private val guildsPerPage = 8
    private var availableTargets: List<Guild> = emptyList()
    private var selectedGuild: Guild? = null

    override fun open() {
        availableTargets = loadAvailableTargets()
        showTargetSelectionMenu()
    }

    private fun loadAvailableTargets(): List<Guild> {
        val allGuilds = guildService.getAllGuilds()
        val currentRelations = diplomacyService.getRelations(guild.id)

        return allGuilds.filter { targetGuild ->
            targetGuild.id != guild.id && // Don't show own guild
            !currentRelations.any { relation ->
                relation.targetGuildId == targetGuild.id &&
                (relation.type.name == "ENEMY" || relation.type.name == "TRUCE") &&
                relation.isActive()
            } // Don't show guilds already at war or in truce with
        }
    }

    private fun showTargetSelectionMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>Declare War - Select Target"))
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load targets into main pane
        loadTargetsPage(mainPane)

        // Add navigation
        addTargetSelectionNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadTargetsPage(mainPane: StaticPane) {
        // Show first 36 targets (4 rows x 9 columns)
        val maxTargets = min(36, availableTargets.size)
        val pageTargets = availableTargets.take(maxTargets)

        pageTargets.forEachIndexed { index, targetGuild ->
            val item = createTargetItem(targetGuild)
            mainPane.addItem(GuiItem(item) { _ ->
                selectedGuild = targetGuild
                openWarDeclarationComposer(targetGuild)
            }, index % 9, index / 9)
        }

        // Fill empty slots with placeholder items
        for (i in pageTargets.size until 36) {
            val placeholderItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                .setAdventureName(player, messageService, "<gray>No Target")
                .lore(listOf("<dark_gray>No valid target in this slot"))

            mainPane.addItem(GuiItem(placeholderItem), i % 9, i / 9)
        }
    }

    private fun createTargetItem(targetGuild: Guild): ItemStack {
        val currentRelations = diplomacyService.getRelations(guild.id)
        val existingRelation = currentRelations.find { it.targetGuildId == targetGuild.id }

        val lore = mutableListOf(
            "<gray>Owner: <white>${targetGuild.ownerName}",
            "<gray>Members: <white>${targetGuild.level * 5}",
            "<gray>Level: <white>${targetGuild.level}"
        )

        if (existingRelation != null) {
            lore.add("<gray>Current Relation: <white>${existingRelation.type.name}")
        } else {
            lore.add("<gray>Status: <green>No existing relations")
        }

        lore.add("<gray>Power Level: <white>${calculatePowerLevel(targetGuild)}")

        return ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<red>${targetGuild.name}")
            .lore(lore)
    }

    private fun openWarDeclarationComposer(targetGuild: Guild) {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>War Declaration - ${targetGuild.name}"))
        val mainPane = StaticPane(0, 0, 9, 5)

        // Target Guild Information
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Target Guild Information")
            .lore(listOf(
                "<gray>Guild: <red>${targetGuild.name}",
                "<gray>Owner: <white>${targetGuild.ownerName}",
                "<gray>Level: <white>${targetGuild.level}",
                "<gray>Members: <white>${targetGuild.level * 5}",
                "<gray>Power Level: <white>${calculatePowerLevel(targetGuild)}"
            ))

        mainPane.addItem(GuiItem(infoItem), 2, 0)

        // War Declaration Options
        addWarOptions(mainPane, targetGuild)

        // War Consequences
        val consequencesItem = ItemStack(Material.REDSTONE)
            .setAdventureName(player, messageService, "<red>War Consequences")
            .lore(listOf(
                "<gray>Immediate declaration of war",
                "<gray>No acceptance required",
                "<gray>Cannot be cancelled once declared",
                "<gray>May affect diplomatic reputation",
                "<gray>Could lead to alliances breaking"
            ))

        mainPane.addItem(GuiItem(consequencesItem), 0, 2)

        // Custom Declaration Message
        val messageItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<yellow>Declaration Message")
            .lore(listOf(
                "<gray>Add a custom war declaration message",
                "<gray>Click to compose your declaration",
                "<gray>Leave blank for default message"
            ))

        mainPane.addItem(GuiItem(messageItem) { _ ->
            openMessageInput(targetGuild)
        }, 2, 2)

        // Declare War (with default message)
        val declareItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<dark_red><bold>DECLARE WAR")
            .lore(listOf(
                "<gray>Declare war on ${targetGuild.name}",
                "<gray>This action cannot be undone",
                "<gray>War will begin immediately",
                "<gray>Target will be notified"
            ))

        mainPane.addItem(GuiItem(declareItem) { _ ->
            declareWar(targetGuild, null)
        }, 4, 2)

        // Preview Default Message
        val previewItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<aqua>Default Declaration Preview")
            .lore(listOf(
                "<gray>Message: <white>\"We hereby declare war on ${targetGuild.name}. Let this serve as formal notice of our intentions to engage in open hostilities.\"",
                "<gray>Character limit: <white>200",
                "<gray>You can customize this message"
            ))

        mainPane.addItem(GuiItem(previewItem), 6, 2)

        // Back
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back")
            .lore(listOf("<gray>Return to target selection"))
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

    private fun addWarOptions(mainPane: StaticPane, targetGuild: Guild) {
        // War Declaration Types
        val immediateItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<dark_red>Immediate War")
            .lore(listOf(
                "<gray>Standard war declaration",
                "<gray>War begins immediately",
                "<gray>No special conditions",
                "<gray>Recommended for most cases"
            ))

        mainPane.addItem(GuiItem(immediateItem), 1, 1)

        // War with Conditions (placeholder for future features)
        val conditionalItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>Conditional War")
            .lore(listOf(
                "<gray>War with specific objectives",
                "<gray>Requires target to meet conditions",
                "<gray>More strategic approach",
                "<gray>Coming in future update"
            ))

        mainPane.addItem(GuiItem(conditionalItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Conditional war declarations coming soon!")
        }, 3, 1)

        // War Declaration with Terms
        val termsItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<aqua>War with Terms")
            .lore(listOf(
                "<gray>Propose specific war terms",
                "<gray>Define victory conditions",
                "<gray>Set war duration limits",
                "<gray>Coming in future update"
            ))

        mainPane.addItem(GuiItem(termsItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>War terms feature coming soon!")
        }, 5, 1)
    }

    private fun openMessageInput(targetGuild: Guild) {
        val anvilGui = AnvilGui("War Declaration Message")

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        // Handle name input changes
        var currentMessage = "We hereby declare war on ${targetGuild.name}. Let this serve as formal notice of our intentions to engage in open hostilities."
        anvilGui.setOnNameInputChanged { newMessage ->
            currentMessage = newMessage
        }

        // Add confirm button in the right slot
        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<dark_red><bold>DECLARE WAR")
            .lore(listOf("<gray>Click to declare war with your custom message"))

        val guiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            declareWar(targetGuild, currentMessage)
            player.closeInventory()
        }
        firstPane.addItem(guiItem, 0, 0)
        anvilGui.firstItemComponent.addPane(firstPane)

        // Prevent clicking in anvil slots
        anvilGui.setOnTopClick { event -> event.isCancelled = true }
        anvilGui.setOnBottomClick { event -> event.isCancelled = true }

        anvilGui.show(player)
    }

    private fun declareWar(targetGuild: Guild, customMessage: String?) {
        if (selectedGuild == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>No target guild selected.")
            return
        }

        val request = diplomacyService.sendRequest(
            fromGuildId = guild.id,
            toGuildId = targetGuild.id,
            type = DiplomaticRequestType.WAR_DECLARATION,
            message = customMessage
        )

        if (request != null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<dark_red><bold>WAR DECLARED!")
            AdventureMenuHelper.sendMessage(player, messageService, "<red>You have declared war on ${targetGuild.name}!")
            if (!customMessage.isNullOrBlank()) {
                player.sendMessage("<gray>Declaration: \"$customMessage\"")
            }
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>War begins immediately. Good luck.")
        } else {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to declare war. You may already be at war with this guild.")
        }
    }

    private fun calculatePowerLevel(guild: Guild): Int {
        return guild.level * 10 + (guild.level * 5) // Mock calculation
    }

    private fun addTargetSelectionNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<green>Previous Page").lore(listOf("<gray>Click to go back"))
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open()
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * guildsPerPage < availableTargets.size) {
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

        // Filter Options
        val filterItem = ItemStack(Material.COMPASS).setAdventureName(player, messageService, "<aqua>Filter Targets").lore(listOf("<gray>Filter by power level or relations"))
        navigationPane.addItem(GuiItem(filterItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Filter feature coming soon!")
        }, 6, 0)
    }
}
