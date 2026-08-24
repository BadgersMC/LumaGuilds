package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

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
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.guild_war_management.title", "guild" to guild.name)))
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
            val noWarsItem = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.guild_war_management.current.none.name"))
                .lore(lang.gui("menu.guild_war_management.current.none.description"))
                .lore(lang.gui("menu.guild_war_management.current.none.hint"))
            pane.addItem(GuiItem(noWarsItem), 0, 0)
        } else {
            // Display first active war
            val war = activeWars.first()
            val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyGuildId)

            val warItem = ItemStack.of(Material.DIAMOND_SWORD)
                .name(lang.gui("menu.guild_war_management.current.active.name", "enemy" to (enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown"))))
                .lore(lang.gui("menu.guild_war_management.current.active.duration", "days" to war.duration.toDays()))
                .lore(lang.gui("menu.guild_war_management.current.active.remaining", "days" to (war.remainingDuration?.toDays() ?: 0)))
                .lore(lang.gui("menu.guild_war_management.current.active.status"))

            val guiItem = GuiItem(warItem) {
                openWarDetailsMenu(war)
            }
            pane.addItem(guiItem, 0, 0)

            // Show war count if more than one
            if (activeWars.size > 1) {
                val moreWarsItem = ItemStack.of(Material.BOOK)
                    .name(lang.gui("menu.guild_war_management.current.more.name", "count" to activeWars.size - 1))
                    .lore(lang.gui("menu.guild_war_management.current.more.description"))
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
        val incomingItem = ItemStack.of(if (incomingDeclarations.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .name(lang.gui("menu.guild_war_management.declarations.incoming.name"))
            .lore(lang.gui("menu.guild_war_management.declarations.incoming.description"))
            .lore(lang.gui("menu.guild_war_management.declarations.count", "count" to incomingDeclarations.size))

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingDeclarationsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing declarations
        val outgoingItem = ItemStack.of(if (outgoingDeclarations.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name(lang.gui("menu.guild_war_management.declarations.outgoing.name"))
            .lore(lang.gui("menu.guild_war_management.declarations.outgoing.description"))
            .lore(lang.gui("menu.guild_war_management.declarations.count", "count" to outgoingDeclarations.size))

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingDeclarationsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addWarActionsSection(pane: StaticPane) {
        // Declare war
        val declareWarItem = ItemStack.of(Material.IRON_SWORD)
            .name(lang.gui("menu.guild_war_management.actions.declare.name"))
            .lore(lang.gui("menu.guild_war_management.actions.declare.description"))
            .lore(lang.gui("menu.guild_war_management.actions.declare.hint"))

        val declareWarGuiItem = GuiItem(declareWarItem) {
            openDeclareWarMenu()
        }
        pane.addItem(declareWarGuiItem, 0, 2)

        // War statistics
        val warStatsItem = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.gui("menu.guild_war_management.actions.statistics.name"))
            .lore(lang.gui("menu.guild_war_management.actions.statistics.description"))
            .lore(lang.gui("menu.guild_war_management.actions.statistics.hint"))

        val warStatsGuiItem = GuiItem(warStatsItem) {
            openWarStatsMenu()
        }
        pane.addItem(warStatsGuiItem, 2, 2)

        // War history
        val warHistoryItem = ItemStack.of(Material.BOOKSHELF)
            .name(lang.gui("menu.guild_war_management.actions.history.name"))
            .lore(lang.gui("menu.guild_war_management.actions.history.description"))
            .lore(lang.gui("menu.guild_war_management.actions.history.hint"))

        val warHistoryGuiItem = GuiItem(warHistoryItem) {
            openWarHistoryMenu()
        }
        pane.addItem(warHistoryGuiItem, 4, 2)

        // Peace agreements
        val peaceItem = ItemStack.of(Material.WHITE_WOOL)
            .name(lang.gui("menu.guild_war_management.actions.peace.name"))
            .lore(lang.gui("menu.guild_war_management.actions.peace.description"))
            .lore(lang.gui("menu.guild_war_management.actions.peace.hint"))

        val peaceGuiItem = GuiItem(peaceItem) {
            openPeaceAgreementsMenu()
        }
        pane.addItem(peaceGuiItem, 6, 2)
    }

    private fun addWarStatsSection(pane: StaticPane) {
        // Quick stats display
        val winLossRatio = warService.getWinLossRatio(guild.id)
        val totalWars = warService.getWarsForGuild(guild.id).size

        val statsItem = ItemStack.of(Material.TOTEM_OF_UNDYING)
            .name(lang.gui("menu.guild_war_management.quick_stats.name"))
            .lore(lang.gui("menu.guild_war_management.quick_stats.total", "count" to totalWars))
            .lore(lang.gui("menu.guild_war_management.quick_stats.ratio", "ratio" to String.format("%.2f", winLossRatio)))
            .lore(lang.gui("menu.guild_war_management.quick_stats.active", "count" to warService.getWarsForGuild(guild.id).count { it.isActive }))

        val statsGuiItem = GuiItem(statsItem) {
            openDetailedStatsMenu()
        }
        pane.addItem(statsGuiItem, 0, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.back.name"))
            .lore(lang.gui("menu.guild_war_management.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openWarDetailsMenu(war: War) {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.details.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.details.description"))
    }

    private fun openWarListMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.list.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.list.description"))
    }

    private fun openIncomingDeclarationsMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.incoming.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.incoming.description"))
    }

    private fun openOutgoingDeclarationsMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.outgoing.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.outgoing.description"))
    }

    private fun openDeclareWarMenu() {
        menuNavigator.openMenu(menuFactory.createGuildWarDeclarationMenu(menuNavigator, player, guild))
    }

    private fun openWarStatsMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.statistics.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.statistics.description"))
    }

    private fun openWarHistoryMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.history.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.history.description"))
    }

    private fun openDetailedStatsMenu() {
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.detailed_statistics.title"))
        player.sendMessage(lang.msg("menu.guild_war_management.coming_soon.detailed_statistics.description"))
    }

    private fun openPeaceAgreementsMenu() {
        menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

