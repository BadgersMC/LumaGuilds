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
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildWarManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild): Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()

    override fun open() {
        val gui = ChestGui(6, "§4War Management - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
                .name("§cNo Active Wars")
                .lore("§7Your guild is not currently at war")
                .lore("§7Declare war to start conflicts!")
            pane.addItem(GuiItem(noWarsItem), 0, 0)
        } else {
            // Display first active war
            val war = activeWars.first()
            val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyGuildId)

            val warItem = ItemStack(Material.DIAMOND_SWORD)
                .name("§cActive War: vs ${enemyGuild?.name ?: "Unknown"}")
                .lore("§7Duration: §f${war.duration.toDays()} days")
                .lore("§7Remaining: §f${war.remainingDuration?.toDays() ?: 0} days")
                .lore("§7Status: §4ACTIVE")

            val guiItem = GuiItem(warItem) {
                openWarDetailsMenu(war)
            }
            pane.addItem(guiItem, 0, 0)

            // Show war count if more than one
            if (activeWars.size > 1) {
                val moreWarsItem = ItemStack(Material.BOOK)
                    .name("§e+${activeWars.size - 1} More Wars")
                    .lore("§7Click to view all active wars")
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
            .name("§aIncoming Declarations")
            .lore("§7War declarations against your guild")
            .lore("§7Count: §f${incomingDeclarations.size}")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingDeclarationsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing declarations
        val outgoingItem = ItemStack(if (outgoingDeclarations.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name("§eOutgoing Declarations")
            .lore("§7Your guild's war declarations")
            .lore("§7Count: §f${outgoingDeclarations.size}")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingDeclarationsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addWarActionsSection(pane: StaticPane) {
        // Declare war
        val declareWarItem = ItemStack(Material.IRON_SWORD)
            .name("§4Declare War")
            .lore("§7Declare war on another guild")
            .lore("§7Start a conflict with objectives")

        val declareWarGuiItem = GuiItem(declareWarItem) {
            openDeclareWarMenu()
        }
        pane.addItem(declareWarGuiItem, 0, 2)

        // War statistics
        val warStatsItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§6War Statistics")
            .lore("§7View your guild's war performance")
            .lore("§7Win/loss ratio and history")

        val warStatsGuiItem = GuiItem(warStatsItem) {
            openWarStatsMenu()
        }
        pane.addItem(warStatsGuiItem, 2, 2)

        // War history
        val warHistoryItem = ItemStack(Material.BOOKSHELF)
            .name("§eWar History")
            .lore("§7View past wars and outcomes")
            .lore("§7Learn from previous conflicts")

        val warHistoryGuiItem = GuiItem(warHistoryItem) {
            openWarHistoryMenu()
        }
        pane.addItem(warHistoryGuiItem, 4, 2)

        // Peace agreements
        val peaceItem = ItemStack(Material.WHITE_WOOL)
            .name("§a☮ Peace Agreements")
            .lore("§7Propose peace to end wars")
            .lore("§7Negotiate terms and offerings")

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
            .name("§bQuick Stats")
            .lore("§7Total Wars: §f$totalWars")
            .lore("§7Win/Loss Ratio: §f${String.format("%.2f", winLossRatio)}")
            .lore("§7Active Wars: §f${warService.getWarsForGuild(guild.id).count { it.isActive }}")

        val statsGuiItem = GuiItem(statsItem) {
            openDetailedStatsMenu()
        }
        pane.addItem(statsGuiItem, 0, 3)
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

    private fun openWarDetailsMenu(war: War) {
        player.sendMessage("§eWar details menu coming soon!")
        player.sendMessage("§7This would show detailed war information, objectives, and management options.")
    }

    private fun openWarListMenu() {
        player.sendMessage("§eWar list menu coming soon!")
        player.sendMessage("§7This would show all active wars your guild is involved in.")
    }

    private fun openIncomingDeclarationsMenu() {
        player.sendMessage("§eIncoming declarations menu coming soon!")
        player.sendMessage("§7This would show war declarations against your guild that you can accept or reject.")
    }

    private fun openOutgoingDeclarationsMenu() {
        player.sendMessage("§eOutgoing declarations menu coming soon!")
        player.sendMessage("§7This would show war declarations your guild has sent.")
    }

    private fun openDeclareWarMenu() {
        menuNavigator.openMenu(GuildWarDeclarationMenu(menuNavigator, player, guild))
    }

    private fun openWarStatsMenu() {
        player.sendMessage("§eWar statistics menu coming soon!")
        player.sendMessage("§7This would show detailed war performance metrics.")
    }

    private fun openWarHistoryMenu() {
        player.sendMessage("§eWar history menu coming soon!")
        player.sendMessage("§7This would show a history of all past wars.")
    }

    private fun openDetailedStatsMenu() {
        player.sendMessage("§eDetailed statistics menu coming soon!")
        player.sendMessage("§7This would show comprehensive war statistics and analytics.")
    }

    private fun openPeaceAgreementsMenu() {
        menuNavigator.openMenu(PeaceAgreementMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
