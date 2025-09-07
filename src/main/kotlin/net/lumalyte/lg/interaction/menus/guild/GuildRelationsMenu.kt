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
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildRelationsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private var guild: Guild): Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()

    override fun open() {
        val gui = ChestGui(6, "§bDiplomatic Relations - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
            .name("§aAllies")
            .lore("§7Guilds you are allied with")
            .lore("§7Count: §f$allies")
            .lore("§7Can coordinate and support each other")

        val alliesGuiItem = GuiItem(alliesItem) {
            openAlliesListMenu()
        }
        pane.addItem(alliesGuiItem, 0, 0)

        // Enemies
        val enemiesItem = ItemStack(if (enemies > 0) Material.REDSTONE else Material.GRAY_DYE)
            .name("§cEnemies")
            .lore("§7Guilds you are at war with")
            .lore("§7Count: §f$enemies")
            .lore("§7Can engage in warfare")

        val enemiesGuiItem = GuiItem(enemiesItem) {
            openEnemiesListMenu()
        }
        pane.addItem(enemiesGuiItem, 2, 0)

        // Truces
        val trucesItem = ItemStack(if (truces > 0) Material.CLOCK else Material.GRAY_DYE)
            .name("§eTruces")
            .lore("§7Temporary ceasefires")
            .lore("§7Count: §f$truces")
            .lore("§7Peace agreements with expiration")

        val trucesGuiItem = GuiItem(trucesItem) {
            openTrucesListMenu()
        }
        pane.addItem(trucesGuiItem, 4, 0)

        // Diplomatic Status
        val statusItem = ItemStack(Material.BOOK)
            .name("§bDiplomatic Status")
            .lore("§7Your guild's diplomatic standing")
            .lore("§7Allies: §a$allies §7| Enemies: §c$enemies §7| Truces: §e$truces")

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
            .name("§aIncoming Requests")
            .lore("§7Diplomatic requests from other guilds")
            .lore("§7Count: §f${incomingRequests.size}")
            .lore("§7Alliance and truce proposals")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 1, 1)

        // Outgoing requests
        val outgoingItem = ItemStack(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name("§eOutgoing Requests")
            .lore("§7Your guild's pending requests")
            .lore("§7Count: §f${outgoingRequests.size}")
            .lore("§7Awaiting other guild responses")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 3, 1)
    }

    private fun addDiplomaticActionsSection(pane: StaticPane) {
        // Request Alliance
        val allianceItem = ItemStack(Material.GOLDEN_APPLE)
            .name("§6Request Alliance")
            .lore("§7Propose an alliance with another guild")
            .lore("§7Must be accepted by the target guild")
            .lore("§7Allows coordination and support")

        val allianceGuiItem = GuiItem(allianceItem) {
            openRequestAllianceMenu()
        }
        pane.addItem(allianceGuiItem, 0, 2)

        // Request Truce
        val truceItem = ItemStack(Material.WHITE_BANNER)
            .name("§fRequest Truce")
            .lore("§7Propose a ceasefire with an enemy")
            .lore("§7Temporary peace agreement")
            .lore("§7Must be accepted by the target guild")

        val truceGuiItem = GuiItem(truceItem) {
            openRequestTruceMenu()
        }
        pane.addItem(truceGuiItem, 2, 2)

        // Declare War
        val warItem = ItemStack(Material.IRON_SWORD)
            .name("§4Declare War")
            .lore("§7Immediately declare war on another guild")
            .lore("§7No acceptance required")
            .lore("§7Enables warfare between guilds")

        val warGuiItem = GuiItem(warItem) {
            openDeclareWarMenu()
        }
        pane.addItem(warGuiItem, 4, 2)
    }

    private fun addRelationDetailsSection(pane: StaticPane) {
        // Diplomatic History
        val historyItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§9Diplomatic History")
            .lore("§7View past relations and changes")
            .lore("§7Track diplomatic developments")
            .lore("§7Learn from past interactions")

        val historyGuiItem = GuiItem(historyItem) {
            openDiplomaticHistoryMenu()
        }
        pane.addItem(historyGuiItem, 0, 3)

        // Neutral Guilds
        val neutralItem = ItemStack(Material.BOOKSHELF)
            .name("§7Neutral Guilds")
            .lore("§7Guilds with no special relations")
            .lore("§7Browse available diplomatic partners")
            .lore("§7Potential allies or rivals")

        val neutralGuiItem = GuiItem(neutralItem) {
            openNeutralGuildsMenu()
        }
        pane.addItem(neutralGuiItem, 2, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack to Control Panel")
            .lore("§7Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openAlliesListMenu() {
        player.sendMessage("§eAllies list menu coming soon!")
        player.sendMessage("§7This would show all guilds your guild is allied with.")
    }

    private fun openEnemiesListMenu() {
        player.sendMessage("§eEnemies list menu coming soon!")
        player.sendMessage("§7This would show all guilds your guild is at war with.")
    }

    private fun openTrucesListMenu() {
        player.sendMessage("§eTruces list menu coming soon!")
        player.sendMessage("§7This would show all active truce agreements.")
    }

    private fun openDiplomaticStatusMenu() {
        player.sendMessage("§eDiplomatic status menu coming soon!")
        player.sendMessage("§7This would show comprehensive diplomatic information.")
    }

    private fun openIncomingRequestsMenu() {
        player.sendMessage("§eIncoming requests menu coming soon!")
        player.sendMessage("§7This would show diplomatic requests you can accept or reject.")
    }

    private fun openOutgoingRequestsMenu() {
        player.sendMessage("§eOutgoing requests menu coming soon!")
        player.sendMessage("§7This would show requests your guild has sent.")
    }

    private fun openRequestAllianceMenu() {
        player.sendMessage("§eRequest alliance menu coming soon!")
        player.sendMessage("§7This would allow you to request alliances with other guilds.")
    }

    private fun openRequestTruceMenu() {
        player.sendMessage("§eRequest truce menu coming soon!")
        player.sendMessage("§7This would allow you to request truces with enemy guilds.")
    }

    private fun openDeclareWarMenu() {
        player.sendMessage("§eDeclare war menu coming soon!")
        player.sendMessage("§7This would allow you to declare war on other guilds.")
    }

    private fun openDiplomaticHistoryMenu() {
        player.sendMessage("§eDiplomatic history menu coming soon!")
        player.sendMessage("§7This would show the history of diplomatic relations.")
    }

    private fun openNeutralGuildsMenu() {
        player.sendMessage("§eNeutral guilds menu coming soon!")
        player.sendMessage("§7This would show guilds with no special relations to your guild.")
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
