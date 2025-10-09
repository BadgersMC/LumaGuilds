package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
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

class GuildWarManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>War Management - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 1: Current Wars
        addCurrentWarsSection(pane)

        // Row 2: War Declarations
        addWarDeclarationsSection(pane)

        // Row 3: Actions
        addWarActionsSection(pane)

        // Row 4-5: War History/Stats
        addWarStatsSection(pane)

        // Row 6: Navigation
        addBackButton(pane, 4, 5)

        gui.show(player)
    }

    private fun addCurrentWarsSection(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        if (activeWars.isEmpty()) {
            val noWarsItem = ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>No Active Wars")
                .addAdventureLore(player, messageService, "<gray>Your guild is not currently at war")
                .addAdventureLore(player, messageService, "<gray>Declare war to start conflicts!")
            pane.addItem(GuiItem(noWarsItem), 0, 0)
        } else {
            // Display first active war
            val war = activeWars.first()
            val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyGuildId)

            val warItem = ItemStack(Material.DIAMOND_SWORD)
                .setAdventureName(player, messageService, "<red>Active War: vs ${enemyGuild?.name ?: "Unknown"}")
                .addAdventureLore(player, messageService, "<gray>Duration: <white>${war.duration.toDays()} days")
                .addAdventureLore(player, messageService, "<gray>Remaining: <white>${war.remainingDuration?.toDays() ?: 0} days")
                .addAdventureLore(player, messageService, "<gray>Status: <dark_red>ACTIVE")

            val guiItem = GuiItem(warItem) {
                openWarDetailsMenu(war)
            }
            pane.addItem(guiItem, 0, 0)

            // Show war count if more than one
            if (activeWars.size > 1) {
                val moreWarsItem = ItemStack(Material.BOOK)
                    .setAdventureName(player, messageService, "<yellow>+${activeWars.size - 1} More Wars")
                    .addAdventureLore(player, messageService, "<gray>Click to view all active wars")
                pane.addItem(GuiItem(moreWarsItem) {
                    openWarListMenu()
                }, 1, 0)
            }
        }
    }

    private fun addWarDeclarationsSection(pane: StaticPane) {
        val incomingDeclarations = warService.getPendingDeclarationsForGuild(guild.id)
        val outgoingDeclarations = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }

        // Incoming declarations
        val incomingItem = ItemStack(if (incomingDeclarations.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .setAdventureName(player, messageService, "<green>Incoming Declarations")
            .addAdventureLore(player, messageService, "<gray>War declarations against your guild")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${incomingDeclarations.size}")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingDeclarationsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing declarations
        val outgoingItem = ItemStack(if (outgoingDeclarations.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>Outgoing Declarations")
            .addAdventureLore(player, messageService, "<gray>Your guild's war declarations")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${outgoingDeclarations.size}")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingDeclarationsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addWarActionsSection(pane: StaticPane) {
        // Declare war
        val declareWarItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<dark_red>Declare War")
            .addAdventureLore(player, messageService, "<gray>Declare war on another guild")
            .addAdventureLore(player, messageService, "<gray>Start a conflict with objectives")

        val declareWarGuiItem = GuiItem(declareWarItem) {
            openDeclareWarMenu()
        }
        pane.addItem(declareWarGuiItem, 0, 2)

        // War statistics
        val warStatsItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<gold>War Statistics")
            .addAdventureLore(player, messageService, "<gray>View your guild's war performance")
            .addAdventureLore(player, messageService, "<gray>Win/loss ratio and history")

        val warStatsGuiItem = GuiItem(warStatsItem) {
            openWarStatsMenu()
        }
        pane.addItem(warStatsGuiItem, 2, 2)

        // War history
        val warHistoryItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<yellow>War History")
            .addAdventureLore(player, messageService, "<gray>View past wars and outcomes")
            .addAdventureLore(player, messageService, "<gray>Learn from previous conflicts")

        val warHistoryGuiItem = GuiItem(warHistoryItem) {
            openWarHistoryMenu()
        }
        pane.addItem(warHistoryGuiItem, 4, 2)

        // Peace agreements
        val peaceItem = ItemStack(Material.WHITE_WOOL)
            .setAdventureName(player, messageService, "<green>☮ Peace Agreements")
            .addAdventureLore(player, messageService, "<gray>Propose peace to end wars")
            .addAdventureLore(player, messageService, "<gray>Negotiate terms and offerings")

        val peaceGuiItem = GuiItem(peaceItem) {
            openPeaceAgreementsMenu()
        }
        pane.addItem(peaceGuiItem, 6, 2)
    }

    private fun addWarStatsSection(pane: StaticPane) {
        // Quick stats display
        val winLossRatio = warService.getWinLossRatio(guild.id)
        val totalWars = warService.getWarsForGuild(guild.id).size

        val statsItem = ItemStack(Material.TOTEM_OF_UNDYING)
            .setAdventureName(player, messageService, "<aqua>Quick Stats")
            .addAdventureLore(player, messageService, "<gray>Total Wars: <white>$totalWars")
            .addAdventureLore(player, messageService, "<gray>Win/Loss Ratio: <white>${String.format("%.2f", winLossRatio)}")
            .addAdventureLore(player, messageService, "<gray>Active Wars: <white>${warService.getWarsForGuild(guild.id).count { it.isActive }}")

        val statsGuiItem = GuiItem(statsItem) {
            openDetailedStatsMenu()
        }
        pane.addItem(statsGuiItem, 0, 3)
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

    private fun openWarDetailsMenu(war: War) {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>War details menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show detailed war information, objectives, and management options.")
    }

    private fun openWarListMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>War list menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show all active wars your guild is involved in.")
    }

    private fun openIncomingDeclarationsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Incoming declarations menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show war declarations against your guild that you can accept or reject.")
    }

    private fun openOutgoingDeclarationsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Outgoing declarations menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show war declarations your guild has sent.")
    }

    private fun openDeclareWarMenu() {
        menuNavigator.openMenu(menuFactory.createGuildWarDeclarationMenu(menuNavigator, player, guild))
    }

    private fun openWarStatsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>War statistics menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show detailed war performance metrics.")
    }

    private fun openWarHistoryMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>War history menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show a history of all past wars.")
    }

    private fun openDetailedStatsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Detailed statistics menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would show comprehensive war statistics and analytics.")
    }

    private fun openPeaceAgreementsMenu() {
        menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
    }}

