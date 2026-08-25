package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarStats
import net.lumalyte.lg.domain.entities.WarStatus
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GuildWarManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private var guild: Guild): Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

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

    // ========================================================================
    // 2a. openWarDetailsMenu — 3-4 row ChestGui with info, objectives, stats, actions
    // ========================================================================

    private fun openWarDetailsMenu(war: War) {
        val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
        val enemyGuild = guildService.getGuild(enemyGuildId)
        val enemyName = enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")
        val stats = warService.getWarStats(war.id)
        val isDeclaring = war.declaringGuildId == guild.id

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4,
            lang.guiTitle("menu.guild_war_management.war_details.title", "enemy" to enemyName)))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val pane = StaticPane(0, 0, 9, 4)
        gui.addPane(pane)

        // Row 0: Info item
        val infoItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.gui("menu.guild_war_management.war_details.info.enemy", "enemy" to enemyName))

        infoItem.lore(lang.gui("menu.guild_war_management.war_details.info.duration", "days" to war.duration.toDays()))
        if (war.isActive) {
            infoItem.lore(lang.gui("menu.guild_war_management.war_details.info.remaining", "days" to (war.remainingDuration?.toDays() ?: 0)))
        }
        infoItem.lore(lang.gui(
                    when (war.status) {
                        WarStatus.ACTIVE -> "menu.guild_war_management.war_details.info.status_active"
                        WarStatus.ENDED -> "menu.guild_war_management.war_details.info.status_ended"
                        WarStatus.DECLARED -> "menu.guild_war_management.war_details.info.status_declared"
                        WarStatus.CANCELLED -> "menu.guild_war_management.war_details.info.status_ended"
                    }
                ))
        pane.addItem(GuiItem(infoItem), 0, 0)

        // Row 0-1: Objective progress bars
        if (war.objectives.isNotEmpty()) {
            val objTitleItem = ItemStack.of(Material.STRUCTURE_BLOCK)
                .name(lang.gui("menu.guild_war_management.war_details.objectives.title"))
            pane.addItem(GuiItem(objTitleItem), 4, 0)
            // Add objectives to slots 4-7, row 0-1
            var objIndex = 0
            for (obj in war.objectives) {
                val x = 4 + (objIndex % 4).coerceAtMost(3)
                val y = objIndex / 4
                if (y > 1) break

                val mat = if (obj.isCompleted) Material.LIME_DYE else Material.RED_DYE

                val objItem = ItemStack.of(mat)
                    .name(lang.gui(if (obj.isCompleted) "menu.guild_war_management.war_details.objectives.completed"
                    else "menu.guild_war_management.war_details.objectives.entry", "type" to obj.type.name, "current" to obj.currentValue, "target" to obj.targetValue))
                pane.addItem(GuiItem(objItem), x, y)
                objIndex++
            }
        }

        // Row 2: Stats for both sides
        // Your guild's stats
        val yourKills = if (isDeclaring) stats.declaringGuildKills else stats.defendingGuildKills
        val yourDeaths = if (isDeclaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths
        val enemyKills = if (isDeclaring) stats.defendingGuildKills else stats.declaringGuildKills
        val enemyDeaths = if (isDeclaring) stats.defendingGuildDeaths else stats.declaringGuildDeaths
        val yourKdr = if (isDeclaring) stats.declaringKillRatio else stats.defendingKillRatio
        val enemyKdr = if (isDeclaring) stats.defendingKillRatio else stats.declaringKillRatio

        val yourStatsItem = ItemStack.of(Material.DIAMOND_CHESTPLATE)
            .name(lang.gui("menu.guild_war_management.war_details.stats.your_kills", "kills" to yourKills))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.your_deaths", "deaths" to yourDeaths))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.your_kdr", "ratio" to String.format("%.2f", yourKdr)))
        pane.addItem(GuiItem(yourStatsItem), 0, 2)

        val enemyStatsItem = ItemStack.of(Material.IRON_CHESTPLATE)
            .name(lang.gui("menu.guild_war_management.war_details.stats.enemy_kills", "enemy" to enemyName, "kills" to enemyKills))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.enemy_deaths", "enemy" to enemyName, "deaths" to enemyDeaths))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.enemy_kdr", "enemy" to enemyName, "ratio" to String.format("%.2f", enemyKdr)))
        pane.addItem(GuiItem(enemyStatsItem), 3, 2)

        // Row 2-3: Actions
        if (war.isActive) {
            // Surrender button
            val surrenderItem = ItemStack.of(Material.RED_WOOL)
                .name(lang.gui("menu.guild_war_management.war_details.actions.surrender.name"))
                .lore(lang.gui("menu.guild_war_management.war_details.actions.surrender.lore"))
            pane.addItem(GuiItem(surrenderItem) {
                val result = warService.endWar(war.id, enemyGuildId, null, player.uniqueId)
                if (result) {
                    player.sendMessage(lang.msg("menu.guild_war_management.feedback.surrendered"))
                } else {
                    player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                }
                player.closeInventory()
            }, 6, 1)

            // Propose Peace
            val peaceItem = ItemStack.of(Material.WHITE_WOOL)
                .name(lang.gui("menu.guild_war_management.war_details.actions.propose_peace.name"))
                .lore(lang.gui("menu.guild_war_management.war_details.actions.propose_peace.lore"))
            pane.addItem(GuiItem(peaceItem) {
                menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
            }, 7, 1)
        }

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_details.actions.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_details.actions.back.lore"))
        pane.addItem(GuiItem(backItem) { open() }, 4, 3)

        gui.show(player)
    }

    // ========================================================================
    // 2b. openWarListMenu — PaginatedPane (6 rows) of all active wars
    // ========================================================================

    private var warListCurrentPage = 0
    private val warListItemsPerPage = 28

    private fun openWarListMenu() {
        warListCurrentPage = 0
        buildWarListMenu()
    }

    private fun buildWarListMenu() {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6,
            lang.guiTitle("menu.guild_war_management.war_list.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val warPane = PaginatedPane(1, 0, 7, 4)
        val staticPane = StaticPane(0, 0, 9, 6)

        val totalPages = (activeWars.size + warListItemsPerPage - 1) / warListItemsPerPage
        if (warListCurrentPage >= totalPages && totalPages > 0) warListCurrentPage = totalPages - 1

        val startIndex = warListCurrentPage * warListItemsPerPage
        val endIndex = minOf(startIndex + warListItemsPerPage, activeWars.size)
        val pageWars = activeWars.toList().subList(startIndex, endIndex)

        warPane.clear()
        val newPage = StaticPane(0, 0, 7, 4)

        if (pageWars.isEmpty()) {
            val emptyItem = ItemStack.of(Material.GREEN_BANNER)
                .name(lang.gui("menu.guild_war_management.war_list.empty.name"))
                .lore(lang.gui("menu.guild_war_management.war_list.empty.description"))
            newPage.addItem(GuiItem(emptyItem) { }, 3, 1)
        } else {
            for ((index, war) in pageWars.withIndex()) {
                val x = index % 7
                val y = index / 7
                val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyGuildId)
                val enemyName = enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")
                val stats = warService.getWarStats(war.id)
                val isDeclaring = war.declaringGuildId == guild.id
                val kills = if (isDeclaring) stats.declaringGuildKills else stats.defendingGuildKills
                val deaths = if (isDeclaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths

                val item = ItemStack.of(Material.DIAMOND_SWORD)
                    .name(lang.gui("menu.guild_war_management.war_list.item.name", "enemy" to enemyName))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.duration", "days" to war.duration.toDays()))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.kills", "kills" to kills))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.deaths", "deaths" to deaths))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.click"))
                newPage.addItem(GuiItem(item) { openWarDetailsMenu(war) }, x, y)
            }
        }

        warPane.addPage(newPage)
        warPane.page = 0

        // Navigation buttons
        if (warListCurrentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_list.navigation.previous.name"))
                .lore(lang.gui("menu.guild_war_management.war_list.navigation.previous.description"))
            staticPane.addItem(GuiItem(prevItem) {
                warListCurrentPage--
                buildWarListMenu()
            }, 0, 4)
        }

        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_war_management.war_list.navigation.page", "page" to warListCurrentPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
        staticPane.addItem(GuiItem(pageItem) { }, 4, 4)

        if (warListCurrentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_list.navigation.next.name"))
                .lore(lang.gui("menu.guild_war_management.war_list.navigation.next.description"))
            staticPane.addItem(GuiItem(nextItem) {
                warListCurrentPage++
                buildWarListMenu()
            }, 8, 4)
        }

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_list.navigation.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_list.navigation.back.description"))
        staticPane.addItem(GuiItem(backItem) { open() }, 4, 5)

        gui.addPane(warPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    // ========================================================================
    // 2c. openIncomingDeclarationsMenu — PaginatedPane, accept/reject
    // ========================================================================

    private var incomingPage = 0
    private val incomingItemsPerPage = 28

    private fun openIncomingDeclarationsMenu() {
        incomingPage = 0
        buildIncomingDeclarationsMenu()
    }

    private fun buildIncomingDeclarationsMenu() {
        val declarations = warService.getPendingDeclarationsForGuild(guild.id)

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6,
            lang.guiTitle("menu.guild_war_management.incoming_declarations.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val declPane = PaginatedPane(1, 0, 7, 4)
        val staticPane = StaticPane(0, 0, 9, 6)

        val totalPages = (declarations.size + incomingItemsPerPage - 1) / incomingItemsPerPage
        if (incomingPage >= totalPages && totalPages > 0) incomingPage = totalPages - 1

        val startIndex = incomingPage * incomingItemsPerPage
        val endIndex = minOf(startIndex + incomingItemsPerPage, declarations.size)
        val pageDecls = declarations.toList().subList(startIndex, endIndex)

        declPane.clear()
        val newPage = StaticPane(0, 0, 7, 4)

        if (pageDecls.isEmpty()) {
            val emptyItem = ItemStack.of(Material.GREEN_DYE)
                .name(lang.gui("menu.guild_war_management.incoming_declarations.empty.name"))
                .lore(lang.gui("menu.guild_war_management.incoming_declarations.empty.description"))
            newPage.addItem(GuiItem(emptyItem) { }, 3, 1)
        } else {
            for ((index, decl) in pageDecls.withIndex()) {
                val x = index % 7
                val y = index / 7
                val enemyGuild = guildService.getGuild(decl.declaringGuildId)
                val enemyName = enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")

                val item = if (decl.isValid) {
                    ItemStack.of(Material.PAPER)
                        .name(lang.gui("menu.guild_war_management.incoming_declarations.item.name", "enemy" to enemyName))
                        .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.duration", "days" to decl.proposedDuration.toDays()))
                        .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.expires", "hours" to decl.remainingTime.toHours()))
                        .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.accept"))
                        .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.reject"))
                } else {
                    ItemStack.of(Material.GRAY_DYE)
                        .name(lang.gui("menu.guild_war_management.incoming_declarations.item.expired.name", "enemy" to enemyName))
                }

                newPage.addItem(GuiItem(item) {
                    if (decl.isValid) {
                        if (it.click == ClickType.LEFT) {
                            val result = warService.acceptWarDeclaration(decl.id, player.uniqueId)
                            if (result != null) {
                                player.sendMessage(lang.msg("menu.guild_war_management.incoming_declarations.accepted"))
                            } else {
                                player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                            }
                        } else if (it.click == ClickType.RIGHT) {
                            val result = warService.rejectWarDeclaration(decl.id, player.uniqueId)
                            if (result) {
                                player.sendMessage(lang.msg("menu.guild_war_management.incoming_declarations.rejected"))
                            } else {
                                player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                            }
                        }
                        buildIncomingDeclarationsMenu()
                    }
                }, x, y)
            }
        }

        declPane.addPage(newPage)
        declPane.page = 0

        // Navigation
        if (incomingPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.incoming_declarations.navigation.previous.name"))
                .lore(lang.gui("menu.guild_war_management.incoming_declarations.navigation.previous.description"))
            staticPane.addItem(GuiItem(prevItem) { incomingPage--; buildIncomingDeclarationsMenu() }, 0, 4)
        }

        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_war_management.incoming_declarations.navigation.page", "page" to incomingPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
        staticPane.addItem(GuiItem(pageItem) { }, 4, 4)

        if (incomingPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.incoming_declarations.navigation.next.name"))
                .lore(lang.gui("menu.guild_war_management.incoming_declarations.navigation.next.description"))
            staticPane.addItem(GuiItem(nextItem) { incomingPage++; buildIncomingDeclarationsMenu() }, 8, 4)
        }

        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.incoming_declarations.navigation.back.name"))
            .lore(lang.gui("menu.guild_war_management.incoming_declarations.navigation.back.description"))
        staticPane.addItem(GuiItem(backItem) { open() }, 4, 5)

        gui.addPane(declPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    // ========================================================================
    // 2d. openOutgoingDeclarationsMenu — PaginatedPane, click to cancel
    // ========================================================================

    private var outgoingPage = 0
    private val outgoingItemsPerPage = 28

    private fun openOutgoingDeclarationsMenu() {
        outgoingPage = 0
        buildOutgoingDeclarationsMenu()
    }

    private fun buildOutgoingDeclarationsMenu() {
        val declarations = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6,
            lang.guiTitle("menu.guild_war_management.outgoing_declarations.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val declPane = PaginatedPane(1, 0, 7, 4)
        val staticPane = StaticPane(0, 0, 9, 6)

        val totalPages = (declarations.size + outgoingItemsPerPage - 1) / outgoingItemsPerPage
        if (outgoingPage >= totalPages && totalPages > 0) outgoingPage = totalPages - 1

        val startIndex = outgoingPage * outgoingItemsPerPage
        val endIndex = minOf(startIndex + outgoingItemsPerPage, declarations.size)
        val pageDecls = declarations.toList().subList(startIndex, endIndex)

        declPane.clear()
        val newPage = StaticPane(0, 0, 7, 4)

        if (pageDecls.isEmpty()) {
            val emptyItem = ItemStack.of(Material.YELLOW_DYE)
                .name(lang.gui("menu.guild_war_management.outgoing_declarations.empty.name"))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.empty.description"))
            newPage.addItem(GuiItem(emptyItem) { }, 3, 1)
        } else {
            for ((index, decl) in pageDecls.withIndex()) {
                val x = index % 7
                val y = index / 7
                val enemyGuild = guildService.getGuild(decl.defendingGuildId)
                val enemyName = enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")

                val item = if (decl.isValid) {
                    ItemStack.of(Material.WRITABLE_BOOK)
                        .name(lang.gui("menu.guild_war_management.outgoing_declarations.item.name", "enemy" to enemyName))
                        .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.duration", "days" to decl.proposedDuration.toDays()))
                        .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.expires", "hours" to decl.remainingTime.toHours()))
                        .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.cancel"))
                } else {
                    ItemStack.of(Material.GRAY_DYE)
                        .name(lang.gui("menu.guild_war_management.outgoing_declarations.item.expired.name", "enemy" to enemyName))
                }

                newPage.addItem(GuiItem(item) {
                    if (decl.isValid) {
                        val result = warService.cancelWarDeclaration(decl.id, player.uniqueId)
                        if (result) {
                            player.sendMessage(lang.msg("menu.guild_war_management.outgoing_declarations.cancelled"))
                        } else {
                            player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                        }
                        buildOutgoingDeclarationsMenu()
                    }
                }, x, y)
            }
        }

        declPane.addPage(newPage)
        declPane.page = 0

        // Navigation
        if (outgoingPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.previous.name"))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.previous.description"))
            staticPane.addItem(GuiItem(prevItem) { outgoingPage--; buildOutgoingDeclarationsMenu() }, 0, 4)
        }

        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.page", "page" to outgoingPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
        staticPane.addItem(GuiItem(pageItem) { }, 4, 4)

        if (outgoingPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.next.name"))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.next.description"))
            staticPane.addItem(GuiItem(nextItem) { outgoingPage++; buildOutgoingDeclarationsMenu() }, 8, 4)
        }

        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.back.name"))
            .lore(lang.gui("menu.guild_war_management.outgoing_declarations.navigation.back.description"))
        staticPane.addItem(GuiItem(backItem) { open() }, 4, 5)

        gui.addPane(declPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    // ========================================================================
    // 2e. openWarStatsMenu — 3-row summary ChestGui
    // ========================================================================

    private fun openWarStatsMenu() {
        val allWars = warService.getWarsForGuild(guild.id)
        val winLossRatio = warService.getWinLossRatio(guild.id)
        val wins = allWars.count { it.winner == guild.id }
        val losses = allWars.count { it.loser == guild.id }
        val draws = allWars.count { it.isEnded && it.winner == null && it.loser == null }
        val activeCount = allWars.count { it.isActive }
        val total = allWars.size

        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3,
            lang.guiTitle("menu.guild_war_management.war_stats.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val pane = StaticPane(0, 0, 9, 3)
        gui.addPane(pane)

        // Center items
        val totalItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_war_management.war_stats.total", "count" to total))
            .lore(lang.gui("menu.guild_war_management.war_stats.wins", "count" to wins))
            .lore(lang.gui("menu.guild_war_management.war_stats.losses", "count" to losses))
            .lore(lang.gui("menu.guild_war_management.war_stats.draws", "count" to draws))
        pane.addItem(GuiItem(totalItem) { }, 1, 1)

        val ratioItem = ItemStack.of(Material.GOLDEN_APPLE)
            .name(lang.gui("menu.guild_war_management.war_stats.win_rate", "rate" to String.format("%.1f", winLossRatio * 100)))
            .lore(lang.gui("menu.guild_war_management.war_stats.active", "count" to activeCount))
        pane.addItem(GuiItem(ratioItem) { }, 4, 1)

        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_stats.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_stats.back.lore"))
        pane.addItem(GuiItem(backItem) { open() }, 8, 2)

        gui.show(player)
    }

    // ========================================================================
    // 2f. openWarHistoryMenu — PaginatedPane (6 rows) with war history
    // ========================================================================

    private var historyPage = 0
    private val historyItemsPerPage = 28

    private fun openWarHistoryMenu() {
        historyPage = 0
        buildWarHistoryMenu()
    }

    private fun buildWarHistoryMenu() {
        val history = warService.getWarHistory(guild.id, 50)

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6,
            lang.guiTitle("menu.guild_war_management.war_history.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val histPane = PaginatedPane(1, 0, 7, 4)
        val staticPane = StaticPane(0, 0, 9, 6)

        val totalPages = (history.size + historyItemsPerPage - 1) / historyItemsPerPage
        if (historyPage >= totalPages && totalPages > 0) historyPage = totalPages - 1

        val startIndex = historyPage * historyItemsPerPage
        val endIndex = minOf(startIndex + historyItemsPerPage, history.size)
        val pageHistory = history.toList().subList(startIndex, endIndex)

        histPane.clear()
        val newPage = StaticPane(0, 0, 7, 4)

        if (pageHistory.isEmpty()) {
            val emptyItem = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_war_management.war_history.empty.name"))
                .lore(lang.gui("menu.guild_war_management.war_history.empty.description"))
            newPage.addItem(GuiItem(emptyItem) { }, 3, 1)
        } else {
            for ((index, war) in pageHistory.withIndex()) {
                val x = index % 7
                val y = index / 7
                val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyGuildId)
                val enemyName = enemyGuild?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")

                val mat = when {
                    war.winner == guild.id -> Material.GREEN_DYE
                    war.loser == guild.id -> Material.RED_DYE
                    war.isEnded -> Material.YELLOW_DYE
                    else -> Material.GRAY_DYE
                }

                val endedDate = if (war.endedAt != null) dateFormatter.format(war.endedAt) else "---"

                val item = ItemStack.of(mat)
                    .name(lang.gui(when {
                        war.winner == guild.id -> "menu.guild_war_management.war_history.item.won"
                        war.loser == guild.id -> "menu.guild_war_management.war_history.item.lost"
                        war.isEnded -> "menu.guild_war_management.war_history.item.draw"
                        else -> "menu.guild_war_management.war_history.item.ended"
                    }, "enemy" to enemyName))
                    .lore(lang.gui("menu.guild_war_management.war_history.item.duration", "days" to war.duration.toDays()))
                    .lore(lang.gui("menu.guild_war_management.war_history.item.date", "date" to endedDate))
                newPage.addItem(GuiItem(item) { openWarDetailsMenu(war) }, x, y)
            }
        }

        histPane.addPage(newPage)
        histPane.page = 0

        // Navigation
        if (historyPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_history.navigation.previous.name"))
                .lore(lang.gui("menu.guild_war_management.war_history.navigation.previous.description"))
            staticPane.addItem(GuiItem(prevItem) { historyPage--; buildWarHistoryMenu() }, 0, 4)
        }

        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_war_management.war_history.navigation.page", "page" to historyPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
        staticPane.addItem(GuiItem(pageItem) { }, 4, 4)

        if (historyPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_history.navigation.next.name"))
                .lore(lang.gui("menu.guild_war_management.war_history.navigation.next.description"))
            staticPane.addItem(GuiItem(nextItem) { historyPage++; buildWarHistoryMenu() }, 8, 4)
        }

        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_history.navigation.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_history.navigation.back.description"))
        staticPane.addItem(GuiItem(backItem) { open() }, 4, 5)

        gui.addPane(histPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    // ========================================================================
    // 2g. openDetailedStatsMenu — 4-row ChestGui with comprehensive stats
    // ========================================================================

    private fun openDetailedStatsMenu() {
        val allWars = warService.getWarsForGuild(guild.id)
        val wins = allWars.count { it.winner == guild.id }
        val losses = allWars.count { it.loser == guild.id }
        val draws = allWars.count { it.isEnded && it.winner == null && it.loser == null }
        val activeCount = allWars.count { it.isActive }
        val total = allWars.size

        // Aggregate stats across all wars
        var totalKills = 0
        var totalDeaths = 0
        var totalClaimsCaptured = 0
        var totalClaimsLost = 0
        for (war in allWars) {
            try {
                val stats = warService.getWarStats(war.id)
                val isDeclaring = war.declaringGuildId == guild.id
                totalKills += if (isDeclaring) stats.declaringGuildKills else stats.defendingGuildKills
                totalDeaths += if (isDeclaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths
                totalClaimsCaptured += stats.claimsCaptured
                totalClaimsLost += stats.claimsLost
            } catch (_: Exception) {
                // Skip wars without stats
            }
        }
        val kdr = if (totalDeaths > 0) totalKills.toDouble() / totalDeaths.toDouble() else totalKills.toDouble()

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4,
            lang.guiTitle("menu.guild_war_management.war_stats.title")))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT) e.isCancelled = true
        }

        val pane = StaticPane(0, 0, 9, 4)
        gui.addPane(pane)

        // Total wars overview
        val overviewItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_war_management.war_stats.total", "count" to total))
            .lore(lang.gui("menu.guild_war_management.war_stats.wins", "count" to wins))
            .lore(lang.gui("menu.guild_war_management.war_stats.losses", "count" to losses))
            .lore(lang.gui("menu.guild_war_management.war_stats.draws", "count" to draws))
        pane.addItem(GuiItem(overviewItem) { }, 0, 0)

        // Kill stats
        val killItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.gui("menu.guild_war_management.war_stats.kills", "count" to totalKills))
            .lore(lang.gui("menu.guild_war_management.war_stats.deaths", "count" to totalDeaths))
            .lore(lang.gui("menu.guild_war_management.war_stats.kdr", "ratio" to String.format("%.2f", kdr)))
        pane.addItem(GuiItem(killItem) { }, 2, 0)

        // Objective / claims stats
        val objItem = ItemStack.of(Material.STRUCTURE_BLOCK)
            .name(lang.gui("menu.guild_war_management.war_stats.objectives", "count" to allWars.sumOf { war ->
                try { warService.getWarStats(war.id).claimsCaptured } catch (_: Exception) { 0 }
            }))
            .lore(lang.gui("menu.guild_war_management.war_stats.claims_captured", "count" to totalClaimsCaptured))
            .lore(lang.gui("menu.guild_war_management.war_stats.claims_lost", "count" to totalClaimsLost))
        pane.addItem(GuiItem(objItem) { }, 4, 0)

        // Active wars indicator
        val activeItem = ItemStack.of(Material.REDSTONE_TORCH)
            .name(lang.gui("menu.guild_war_management.war_stats.active", "count" to activeCount))
        pane.addItem(GuiItem(activeItem) { }, 6, 0)

        // Win rate centrepiece
        val winRate = if (total > 0) wins.toDouble() / total.toDouble() * 100.0 else 0.0
        val rateItem = ItemStack.of(Material.GOLDEN_APPLE)
            .name(lang.gui("menu.guild_war_management.war_stats.win_rate", "rate" to String.format("%.1f", winRate)))
        pane.addItem(GuiItem(rateItem) { }, 4, 2)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_stats.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_stats.back.lore"))
        pane.addItem(GuiItem(backItem) { open() }, 4, 3)

        gui.show(player)
    }

    // ========================================================================
    // Already-real methods (leave untouched)
    // ========================================================================

    private fun openDeclareWarMenu() {
        menuNavigator.openMenu(menuFactory.createGuildWarDeclarationMenu(menuNavigator, player, guild))
    }

    private fun openPeaceAgreementsMenu() {
        menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}