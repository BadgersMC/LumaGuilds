package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildRelationsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<aqua><aqua>Diplomatic Relations - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 1: Current Relations Overview
        addRelationsOverviewSection(pane)

        // Row 2: Relation Requests
        addRelationRequestsSection(pane)

        // Row 3: Diplomatic Actions
        addDiplomaticActionsSection(pane)

        // Row 4-5: Relation Details/History
        addRelationDetailsSection(pane)

        // Row 6: Navigation
        addBackButton(pane, 4, 5)

        gui.show(player)
    }

    private fun addRelationsOverviewSection(pane: StaticPane) {
        val relations = relationService.getGuildRelations(guild.id)

        // Count relations by type
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }

        // Allies
        val alliesItem = ItemStack(if (allies > 0) Material.DIAMOND else Material.GRAY_DYE)
            .setAdventureName(player, messageService, "<green>Allies")
            .addAdventureLore(player, messageService, "<gray>Guilds you are allied with")
            .addAdventureLore(player, messageService, "<gray>Count: <white>$allies")
            .addAdventureLore(player, messageService, "<gray>Can coordinate and support each other")

        val alliesGuiItem = GuiItem(alliesItem) {
            openAlliesListMenu()
        }
        pane.addItem(alliesGuiItem, 0, 0)

        // Enemies
        val enemiesItem = ItemStack(if (enemies > 0) Material.REDSTONE else Material.GRAY_DYE)
            .setAdventureName(player, messageService, "<red>Enemies")
            .addAdventureLore(player, messageService, "<gray>Guilds you are at war with")
            .addAdventureLore(player, messageService, "<gray>Count: <white>$enemies")
            .addAdventureLore(player, messageService, "<gray>Can engage in warfare")

        val enemiesGuiItem = GuiItem(enemiesItem) {
            openEnemiesListMenu()
        }
        pane.addItem(enemiesGuiItem, 2, 0)

        // Truces
        val trucesItem = ItemStack(if (truces > 0) Material.CLOCK else Material.GRAY_DYE)
            .setAdventureName(player, messageService, "<yellow>Truces")
            .addAdventureLore(player, messageService, "<gray>Temporary ceasefires")
            .addAdventureLore(player, messageService, "<gray>Count: <white>$truces")
            .addAdventureLore(player, messageService, "<gray>Peace agreements with expiration")

        val trucesGuiItem = GuiItem(trucesItem) {
            openTrucesListMenu()
        }
        pane.addItem(trucesGuiItem, 4, 0)

        // Diplomatic Status
        val statusItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<aqua>Diplomatic Status")
            .addAdventureLore(player, messageService, "<gray>Your guild's diplomatic standing")
            .addAdventureLore(player, messageService, "<gray>Allies: <green>$allies <gray>| Enemies: <red>$enemies <gray>| Truces: <yellow>$truces")

        val statusGuiItem = GuiItem(statusItem) {
            openDiplomaticStatusMenu()
        }
        pane.addItem(statusGuiItem, 6, 0)
    }

    private fun addRelationRequestsSection(pane: StaticPane) {
        val incomingRequests = relationService.getIncomingRequests(guild.id)
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)

        // Incoming requests
        val incomingItem = ItemStack(if (incomingRequests.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .setAdventureName(player, messageService, "<green>Incoming Requests")
            .addAdventureLore(player, messageService, "<gray>Diplomatic requests from other guilds")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${incomingRequests.size}")
            .addAdventureLore(player, messageService, "<gray>Alliance and truce proposals")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 1, 1)

        // Outgoing requests
        val outgoingItem = ItemStack(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>Outgoing Requests")
            .addAdventureLore(player, messageService, "<gray>Your guild's pending requests")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${outgoingRequests.size}")
            .addAdventureLore(player, messageService, "<gray>Awaiting other guild responses")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 3, 1)
    }

    private fun addDiplomaticActionsSection(pane: StaticPane) {
        // Request Alliance
        val allianceItem = ItemStack(Material.GOLDEN_APPLE)
            .setAdventureName(player, messageService, "<gold>Request Alliance")
            .addAdventureLore(player, messageService, "<gray>Propose an alliance with another guild")
            .addAdventureLore(player, messageService, "<gray>Must be accepted by the target guild")
            .addAdventureLore(player, messageService, "<gray>Allows coordination and support")

        val allianceGuiItem = GuiItem(allianceItem) {
            openRequestAllianceMenu()
        }
        pane.addItem(allianceGuiItem, 0, 2)

        // Request Truce
        val truceItem = ItemStack(Material.WHITE_BANNER)
            .setAdventureName(player, messageService, "<white>Request Truce")
            .addAdventureLore(player, messageService, "<gray>Propose a ceasefire with an enemy")
            .addAdventureLore(player, messageService, "<gray>Temporary peace agreement")
            .addAdventureLore(player, messageService, "<gray>Must be accepted by the target guild")

        val truceGuiItem = GuiItem(truceItem) {
            openRequestTruceMenu()
        }
        pane.addItem(truceGuiItem, 2, 2)

        // Declare War
        val warItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<dark_red>Declare War")
            .addAdventureLore(player, messageService, "<gray>Immediately declare war on another guild")
            .addAdventureLore(player, messageService, "<gray>No acceptance required")
            .addAdventureLore(player, messageService, "<gray>Enables warfare between guilds")

        val warGuiItem = GuiItem(warItem) {
            openDeclareWarMenu()
        }
        pane.addItem(warGuiItem, 4, 2)
    }

    private fun addRelationDetailsSection(pane: StaticPane) {
        // Diplomatic History
        val historyItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<blue>Diplomatic History")
            .addAdventureLore(player, messageService, "<gray>View past relations and changes")
            .addAdventureLore(player, messageService, "<gray>Track diplomatic developments")
            .addAdventureLore(player, messageService, "<gray>Learn from past interactions")

        val historyGuiItem = GuiItem(historyItem) {
            openDiplomaticHistoryMenu()
        }
        pane.addItem(historyGuiItem, 0, 3)

        // Neutral Guilds
        val neutralItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gray>Neutral Guilds")
            .addAdventureLore(player, messageService, "<gray>Guilds with no special relations")
            .addAdventureLore(player, messageService, "<gray>Browse available diplomatic partners")
            .addAdventureLore(player, messageService, "<gray>Potential allies or rivals")

        val neutralGuiItem = GuiItem(neutralItem) {
            openNeutralGuildsMenu()
        }
        pane.addItem(neutralGuiItem, 2, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openAlliesListMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Allies list menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show all guilds your guild is allied with.")
    }

    private fun openEnemiesListMenu() {
        val enemiesMenu = GuildEnemiesListMenu(menuNavigator, player, guild)
        enemiesMenu.open()
    }

    private fun openTrucesListMenu() {
        val trucesMenu = GuildTrucesListMenu(menuNavigator, player, guild, messageService)
        trucesMenu.open()
    }

    private fun openDiplomaticStatusMenu() {
        val statusMenu = GuildDiplomaticStatusMenu(menuNavigator, player, guild, messageService)
        statusMenu.open()
    }

    private fun openIncomingRequestsMenu() {
        val incomingMenu = GuildIncomingRequestsMenu(menuNavigator, player, guild, messageService)
        incomingMenu.open()
    }

    private fun openOutgoingRequestsMenu() {
        val outgoingMenu = GuildOutgoingRequestsMenu(menuNavigator, player, guild, messageService)
        outgoingMenu.open()
    }

    private fun openRequestAllianceMenu() {
        val allianceMenu = GuildRequestAllianceMenu(menuNavigator, player, guild, messageService)
        allianceMenu.open()
    }

    private fun openRequestTruceMenu() {
        val truceMenu = GuildRequestTruceMenu(menuNavigator, player, guild, messageService)
        truceMenu.open()
    }

    private fun openDeclareWarMenu() {
        val warMenu = GuildDeclareWarMenu(menuNavigator, player, guild, messageService)
        warMenu.open()
    }

    private fun openDiplomaticHistoryMenu() {
        val historyMenu = net.lumalyte.lg.interaction.menus.guild.GuildDiplomaticHistoryMenu(menuNavigator, player, guild, messageService)
        historyMenu.open()
    }

    private fun openNeutralGuildsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Neutral guilds menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show guilds with no special relations to your guild.")
    }}

