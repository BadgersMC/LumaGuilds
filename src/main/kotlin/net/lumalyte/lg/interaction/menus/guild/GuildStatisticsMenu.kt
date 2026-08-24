package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class GuildStatisticsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild): Menu, KoinComponent {

    private val killService: KillService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val mapRendererService: MapRendererService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private val logger = LoggerFactory.getLogger(GuildStatisticsMenu::class.java)

    private val decimalFormat = DecimalFormat("#.##")

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(
            guild.guiTheme,
            6,
            lang.legacy("menu.statistics.title", "guild" to guild.name),
        ))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                guiEvent.isCancelled = true
        }

        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        // Row 1: Overview Statistics
        addKillStatsButton(pane, 0, 0)
        addWarStatsButton(pane, 1, 0)
        addMemberStatsButton(pane, 2, 0)
        addPerformanceButton(pane, 3, 0)

        // Row 2: Rankings and Top Performers
        addTopKillersButton(pane, 0, 1)
        addTopContributorsButton(pane, 1, 1)
        addKillDeathRatiosButton(pane, 2, 1)
        addRecentActivityButton(pane, 3, 1)

        // Row 3: Advanced Analytics
        addPeriodStatsButton(pane, 0, 2)
        addRivalryStatsButton(pane, 1, 2)
        addAchievementsButton(pane, 3, 2)

        // Row 4: Visualizations (Future)
        addGraphPlaceholderButton(pane, 0, 3)
        addTrendAnalysisButton(pane, 1, 3)
        addComparisonButton(pane, 2, 3)
        addExportStatsButton(pane, 3, 3)

        // Row 5: Navigation
        addRefreshStatsButton(pane, 0, 4)
        addBackButton(pane, 7, 4)

        gui.show(player)
    }

    private fun addKillStatsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.legacy("menu.statistics.item.kills.name"))
            .lore(lang.legacy("menu.statistics.common.total_kills", "count" to killStats.totalKills))
            .lore(lang.legacy("menu.statistics.common.total_deaths", "count" to killStats.totalDeaths))
            .lore(netKillsLore(killStats.netKills))
            .lore(lang.legacy("menu.statistics.common.kd_ratio", "ratio" to decimalFormat.format(killStats.killDeathRatio)))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.item.kills.lore.action"))

        val guiItem = GuiItem(item) {
            openKillStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarStatsButton(pane: StaticPane, x: Int, y: Int) {
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            val warHistory = warService.getWarHistory(guild.id, 50)

            val wins = warHistory.count { it.winner == guild.id }
            val losses = warHistory.count { it.winner != null && it.winner != guild.id }
            val draws = warHistory.count { it.winner == null }

            val item = ItemStack.of(Material.WHITE_BANNER)
                .name(lang.legacy("menu.statistics.item.wars.name"))
                .lore(lang.legacy("menu.statistics.common.active_wars", "count" to activeWars.size))
                .lore(lang.legacy("menu.statistics.common.total_wars", "count" to warHistory.size))
                .lore(lang.legacy("menu.statistics.common.wins", "count" to wins))
                .lore(lang.legacy("menu.statistics.common.losses", "count" to losses))
                .lore(lang.legacy("menu.statistics.common.draws", "count" to draws))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.statistics.common.win_rate", "rate" to decimalFormat.format(calculateWinRate(wins, warHistory.size))))

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback to placeholder if war service fails
            val item = ItemStack.of(Material.WHITE_BANNER)
                .name(lang.legacy("menu.statistics.item.wars.name"))
                .lore(lang.legacy("menu.statistics.common.active_wars", "count" to 0))
                .lore(lang.legacy("menu.statistics.common.total_wars", "count" to 0))
                .lore(lang.legacy("menu.statistics.common.wins", "count" to 0))
                .lore(lang.legacy("menu.statistics.common.losses", "count" to 0))
                .lore(lang.legacy("menu.statistics.common.draws", "count" to 0))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.statistics.common.win_rate", "rate" to decimalFormat.format(0)))
                .lore(lang.legacy("menu.statistics.item.wars.lore.unavailable"))

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        }
    }

    private fun addMemberStatsButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        // Placeholder until online membership tracking is implemented for this overview.
        val onlineMembers = 0

        val item = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.legacy("menu.statistics.item.members.name"))
            .lore(lang.legacy("menu.statistics.common.total_members", "count" to memberCount))
            .lore(lang.legacy("menu.statistics.common.online", "count" to onlineMembers))
            .lore(lang.legacy("menu.statistics.common.offline", "count" to memberCount - onlineMembers))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.common.activity_rate", "rate" to calculateActivityRate(memberCount, onlineMembers)))
            .lore(lang.legacy("menu.statistics.item.members.lore.placeholder"))

        val guiItem = GuiItem(item) {
            openMemberStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPerformanceButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)
        val memberCount = memberService.getMemberCount(guild.id)

        val avgKillsPerMember = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        val avgDeathsPerMember = if (memberCount > 0) killStats.totalDeaths.toDouble() / memberCount else 0.0

        val item = ItemStack.of(Material.EXPERIENCE_BOTTLE)
            .name(lang.legacy("menu.statistics.item.performance.name"))
            .lore(lang.legacy("menu.statistics.common.average_kills", "average" to decimalFormat.format(avgKillsPerMember)))
            .lore(lang.legacy("menu.statistics.common.average_deaths", "average" to decimalFormat.format(avgDeathsPerMember)))
            .lore(lang.legacy("menu.statistics.common.efficiency", "percent" to calculateEfficiency(killStats)))
            .lore(lang.legacy("menu.common.blank"))
            .lore(performanceRatingLore(killStats, memberCount))

        val guiItem = GuiItem(item) {
            openPerformanceDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGraphPlaceholderButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.FILLED_MAP)
            .name(lang.legacy("menu.statistics.item.charts.name"))
            .lore(lang.legacy("menu.statistics.item.charts.lore.description"))
            .lore(lang.legacy("menu.statistics.item.charts.lore.trends"))
            .lore(lang.legacy("menu.statistics.item.charts.lore.visualizations"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.item.charts.lore.action"))
            .lore(lang.legacy("menu.statistics.item.charts.lore.rendering"))

        val guiItem = GuiItem(item) {
            renderGuildBalanceChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.statistics.item.back_control.name"))
            .lore(lang.legacy("menu.statistics.item.back_control.lore"))

        val guiItem = GuiItem(item) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun statisticsGui(rows: Int): ChestGui = ChestGui(
        rows,
        MenuTitleBuilder.build(guild.guiTheme, rows, lang.legacy("menu.statistics.title", "guild" to guild.name)),
    )

    // Helper functions for calculations and ratings
    private fun calculateWinRate(wins: Int, totalWars: Int): Double {
        return if (totalWars > 0) (wins.toDouble() / totalWars) * 100 else 0.0
    }

    private fun calculateActivityRate(totalMembers: Int, onlineMembers: Int): String {
        return if (totalMembers > 0) decimalFormat.format((onlineMembers.toDouble() / totalMembers) * 100) else "0"
    }

    private fun calculateEfficiency(killStats: GuildKillStats): String {
        val totalActions = killStats.totalKills + killStats.totalDeaths
        return if (totalActions > 0) decimalFormat.format((killStats.totalKills.toDouble() / totalActions) * 100) else "0"
    }

    private fun netKillsLore(netKills: Int): String = if (netKills >= 0) {
        lang.legacy("menu.statistics.common.net_kills.positive", "count" to netKills)
    } else {
        lang.legacy("menu.statistics.common.net_kills.negative", "count" to netKills)
    }

    private fun performanceRatingLore(killStats: GuildKillStats, memberCount: Int): String {
        val rating = getPerformanceRating(killStats, memberCount)
        val averageKills = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        return when {
            averageKills >= 50 -> lang.legacy("menu.statistics.common.overall_rating.green", "rating" to rating)
            averageKills >= 25 -> lang.legacy("menu.statistics.common.overall_rating.yellow", "rating" to rating)
            averageKills >= 10 -> lang.legacy("menu.statistics.common.overall_rating.gold", "rating" to rating)
            else -> lang.legacy("menu.statistics.common.overall_rating.red", "rating" to rating)
        }
    }

    private fun getPerformanceRating(killStats: GuildKillStats, memberCount: Int): String {
        val avgKills = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        return when {
            avgKills >= 100 -> lang.raw("menu.statistics.rating.performance.legendary")
            avgKills >= 50 -> lang.raw("menu.statistics.rating.performance.elite")
            avgKills >= 25 -> lang.raw("menu.statistics.rating.performance.veteran")
            avgKills >= 10 -> lang.raw("menu.statistics.rating.performance.skilled")
            avgKills >= 5 -> lang.raw("menu.statistics.rating.performance.novice")
            else -> lang.raw("menu.statistics.rating.performance.recruit")
        }
    }

    // Detail view functions
    private fun openKillStatsDetail() {
        try {
            val killStats = killService.getGuildKillStats(guild.id)

            val gui = statisticsGui(4)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 4)
            gui.addPane(pane)

            val summaryItem = ItemStack.of(Material.DIAMOND_SWORD)
                .name(lang.legacy("menu.statistics.detail.kills.name"))
                .lore(lang.legacy("menu.statistics.common.total_kills_green", "count" to killStats.totalKills))
                .lore(lang.legacy("menu.statistics.common.total_deaths_red", "count" to killStats.totalDeaths))
                .lore(netKillsLore(killStats.netKills))
                .lore(lang.legacy("menu.statistics.common.kd_ratio", "ratio" to decimalFormat.format(killStats.killDeathRatio)))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.statistics.common.efficiency_white", "percent" to calculateEfficiency(killStats)))
            pane.addItem(GuiItem(summaryItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 3)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.kills"))
            logger.error("Error opening kill stats detail for guild ${guild.id}", e)
        }
    }

    private fun openWarStatsDetail() {
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            val warHistory = warService.getWarHistory(guild.id, 20)

            val gui = statisticsGui(6)
            val pane = StaticPane(0, 0, 9, 6)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                    guiEvent.isCancelled = true
                }
            }
            gui.addPane(pane)

            // Active Wars Section
            addActiveWarsSection(pane)

            // War History Section
            addWarHistorySection(pane)

            // War Statistics Section
            addWarStatisticsSection(pane)

            // Navigation
            addBackButton(pane, 8, 5)

            gui.show(player)
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.wars"))
            logger.error("Error opening war stats detail for guild ${guild.id}", e)
        }
    }

    private fun addActiveWarsSection(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        val activeWarsItem = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.legacy("menu.statistics.detail.wars.active.name", "count" to activeWars.size))
            .lore(lang.legacy("menu.statistics.detail.wars.active.description"))

        if (activeWars.isNotEmpty()) {
            activeWarsItem.lore(lang.legacy("menu.common.blank"))
            activeWars.take(3).forEach { war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName = enemyGuild?.name ?: lang.raw("menu.statistics.common.unknown_guild")
                val status = if (war.declaringGuildId == guild.id) {
                    lang.raw("menu.statistics.common.attacker")
                } else {
                    lang.raw("menu.statistics.common.defender")
                }
                val remainingTime = war.remainingDuration

                activeWarsItem.lore(lang.legacy("menu.statistics.detail.wars.active.opponent", "guild" to enemyName, "status" to status))
                if (remainingTime != null) {
                    val days = remainingTime.toDays()
                    val hours = remainingTime.toHours() % 24
                    activeWarsItem.lore(lang.legacy("menu.statistics.detail.wars.active.remaining", "days" to days, "hours" to hours))
                } else {
                    activeWarsItem.lore(lang.legacy("menu.statistics.detail.wars.active.expired"))
                }
            }

            if (activeWars.size > 3) {
                activeWarsItem.lore(lang.legacy("menu.statistics.common.more", "count" to activeWars.size - 3))
            }
        } else {
            activeWarsItem.lore(lang.legacy("menu.statistics.detail.wars.active.none"))
        }

        pane.addItem(GuiItem(activeWarsItem), 1, 0)
    }

    private fun addWarHistorySection(pane: StaticPane) {
        val warHistory = warService.getWarHistory(guild.id, 5)

        val historyItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.statistics.detail.wars.history.name"))
            .lore(lang.legacy("menu.statistics.detail.wars.history.description"))

        if (warHistory.isNotEmpty()) {
            historyItem.lore(lang.legacy("menu.common.blank"))

            warHistory.take(4).forEachIndexed { index, war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName = enemyGuild?.name ?: lang.raw("menu.statistics.common.unknown_guild")

                val result = when {
                    war.winner == guild.id -> lang.raw("menu.statistics.detail.wars.history.result.won")
                    war.winner != null -> lang.raw("menu.statistics.detail.wars.history.result.lost")
                    else -> lang.raw("menu.statistics.detail.wars.history.result.draw")
                }

                historyItem.lore(lang.legacy("menu.statistics.detail.wars.history.row", "rank" to index + 1, "guild" to enemyName, "result" to result))

                // Show duration if available
                val duration = war.startedAt?.let { start ->
                    war.endedAt?.let { end ->
                        java.time.Duration.between(start, end)
                    }
                }

                if (duration != null) {
                    val days = duration.toDays()
                    val hours = duration.toHours() % 24
                    historyItem.lore(lang.legacy("menu.statistics.detail.wars.history.duration", "days" to days, "hours" to hours))
                }
            }

            if (warHistory.size > 4) {
                historyItem.lore(lang.legacy("menu.statistics.detail.wars.history.more", "count" to warHistory.size - 4))
            }
        } else {
            historyItem.lore(lang.legacy("menu.statistics.detail.wars.history.none"))
        }

        pane.addItem(GuiItem(historyItem), 3, 0)
    }

    private fun addWarStatisticsSection(pane: StaticPane) {
        val warHistory = warService.getWarHistory(guild.id, 50)
        val wins = warHistory.count { it.winner == guild.id }
        val losses = warHistory.count { it.winner != null && it.winner != guild.id }
        val draws = warHistory.count { it.winner == null }

        val winRate = calculateWinRate(wins, warHistory.size)

        val statsItem = ItemStack.of(Material.TOTEM_OF_UNDYING)
            .name(lang.legacy("menu.statistics.detail.wars.summary.name"))
            .lore(lang.legacy("menu.statistics.detail.wars.summary.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.common.total_wars", "count" to warHistory.size))
            .lore(lang.legacy("menu.statistics.common.wins", "count" to wins))
            .lore(lang.legacy("menu.statistics.common.losses", "count" to losses))
            .lore(lang.legacy("menu.statistics.common.draws", "count" to draws))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.common.win_rate", "rate" to String.format("%.1f", winRate)))

        // Add current win streak
        val recentWars = warHistory.take(10)
        val currentStreak = calculateCurrentStreak(recentWars, guild.id)
        if (currentStreak > 0) {
            val streakLore = if (recentWars.first().winner == guild.id) {
                lang.legacy("menu.statistics.detail.wars.summary.win_streak", "count" to currentStreak)
            } else {
                lang.legacy("menu.statistics.detail.wars.summary.loss_streak", "count" to currentStreak)
            }
            statsItem.lore(streakLore)
        }

        pane.addItem(GuiItem(statsItem), 5, 0)

        // Kill Statistics During Wars
        addWarKillStats(pane)
    }

    private fun addWarKillStats(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        if (activeWars.isNotEmpty()) {
            val warKillStats = ItemStack.of(Material.IRON_SWORD)
                .name(lang.legacy("menu.statistics.detail.wars.kills.name"))
                .lore(lang.legacy("menu.statistics.detail.wars.kills.description"))

            // Get kill stats for each active war
            activeWars.forEach { war ->
                val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyGuildId)

                if (enemyGuild != null) {
                    val killsBetween = killService.getKillsBetweenGuilds(guild.id, enemyGuildId, 100)

                    val guildKills = killsBetween.count { it.killerGuildId == guild.id }
                    val enemyKills = killsBetween.count { it.killerGuildId == enemyGuildId }

                    warKillStats.lore(lang.legacy("menu.common.blank"))
                        .lore(lang.legacy("menu.statistics.detail.wars.kills.opponent", "guild" to enemyGuild.name))
                        .lore(lang.legacy("menu.statistics.detail.wars.kills.yours", "count" to guildKills))
                        .lore(lang.legacy("menu.statistics.detail.wars.kills.enemy", "count" to enemyKills))
                        .lore(lang.legacy("menu.statistics.detail.wars.kills.ratio", "ratio" to calculateKillRatio(guildKills, enemyKills)))
                }
            }

            pane.addItem(GuiItem(warKillStats), 7, 0)
        }
    }

    private fun calculateCurrentStreak(wars: List<War>, guildId: UUID): Int {
        if (wars.isEmpty()) return 0

        var streak = 0
        val firstWarWinner = wars.first().winner

        if (firstWarWinner == guildId || firstWarWinner == null) {
            streak = 1
            for (war in wars.drop(1)) {
                if (war.winner == firstWarWinner) {
                    streak++
                } else {
                    break
                }
            }
        }

        return streak
    }

    private fun calculateKillRatio(guildKills: Int, enemyKills: Int): String {
        return when {
            enemyKills == 0 -> if (guildKills > 0) "∞" else "0.00"
            else -> String.format("%.2f", guildKills.toDouble() / enemyKills.toDouble())
        }
    }

    private fun openMemberStatsDetail() {
        try {
            val members = memberService.getGuildMembers(guild.id)
            val memberCount = memberService.getMemberCount(guild.id)

            val gui = statisticsGui(5)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 5)
            gui.addPane(pane)

            val summaryItem = ItemStack.of(Material.PLAYER_HEAD)
                .name(lang.legacy("menu.statistics.detail.members.name"))
                .lore(lang.legacy("menu.statistics.common.total_members", "count" to memberCount))
                .lore(lang.legacy("menu.statistics.common.online", "count" to Bukkit.getOnlinePlayers().count { p -> members.any { it.playerId == p.uniqueId } }))
            pane.addItem(GuiItem(summaryItem), 4, 0)

            var col = 0
            var row = 0
            members.take(21).forEach { member ->
                val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: member.playerId.toString().take(8)
                val isOnline = Bukkit.getPlayer(member.playerId) != null
                val memberItem = ItemStack.of(Material.PLAYER_HEAD)
                    .name(if (isOnline) {
                        lang.legacy("menu.statistics.detail.members.player.online_name", "player" to playerName)
                    } else {
                        lang.legacy("menu.statistics.detail.members.player.offline_name", "player" to playerName)
                    })
                    .lore(if (isOnline) {
                        lang.legacy("menu.statistics.detail.members.player.online")
                    } else {
                        lang.legacy("menu.statistics.detail.members.player.offline")
                    })
                pane.addItem(GuiItem(memberItem), 1 + col, 1 + row)
                col++
                if (col >= 7) {
                    col = 0
                    row++
                }
            }

            if (members.size > 21) {
                val moreItem = ItemStack.of(Material.PAPER)
                    .name(lang.legacy("menu.statistics.detail.members.more", "count" to members.size - 21))
                pane.addItem(GuiItem(moreItem), 4, 4)
            }

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 8, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.members"))
            logger.error("Error opening member stats detail for guild ${guild.id}", e)
        }
    }

    private fun openPerformanceDetail() {
        try {
            val killStats = killService.getGuildKillStats(guild.id)
            val memberCount = memberService.getMemberCount(guild.id)

            val avgKillsPerMember = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
            val avgDeathsPerMember = if (memberCount > 0) killStats.totalDeaths.toDouble() / memberCount else 0.0

            val gui = statisticsGui(4)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 4)
            gui.addPane(pane)

            val perfItem = ItemStack.of(Material.EXPERIENCE_BOTTLE)
                .name(lang.legacy("menu.statistics.detail.performance.name"))
                .lore(lang.legacy("menu.statistics.common.average_kills", "average" to decimalFormat.format(avgKillsPerMember)))
                .lore(lang.legacy("menu.statistics.common.average_deaths", "average" to decimalFormat.format(avgDeathsPerMember)))
                .lore(lang.legacy("menu.statistics.common.efficiency", "percent" to calculateEfficiency(killStats)))
                .lore(lang.legacy("menu.statistics.common.kd_ratio", "ratio" to decimalFormat.format(killStats.killDeathRatio)))
                .lore(lang.legacy("menu.common.blank"))
                .lore(performanceRatingLore(killStats, memberCount))
            pane.addItem(GuiItem(perfItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 3)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.performance"))
            logger.error("Error opening performance detail for guild ${guild.id}", e)
        }
    }

    private fun addTopKillersButton(pane: StaticPane, x: Int, y: Int) {
        val guildMembers = memberService.getGuildMembers(guild.id).map { it.playerId }
        val topKillers = killService.getTopKillers(guildMembers, 5)

        val item = ItemStack.of(Material.TOTEM_OF_UNDYING)
            .name(lang.legacy("menu.statistics.item.top_killers.name"))
            .lore(lang.legacy("menu.statistics.item.top_killers.lore.description"))

        if (topKillers.isNotEmpty()) {
            item.lore(lang.legacy("menu.common.blank"))
            topKillers.take(3).forEachIndexed { index, (playerId, stats) ->
                val playerName = Bukkit.getPlayer(playerId)?.name ?: lang.raw("general.unknown")
                item.lore(rankedKillsLore(index + 1, playerName, stats.totalKills))
            }
        } else {
            item.lore(lang.legacy("menu.statistics.common.no_kill_data"))
        }

        val guiItem = GuiItem(item) {
            openTopKillersDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTopContributorsButton(pane: StaticPane, x: Int, y: Int) {
        val contributions = bankService.getMemberContributions(guild.id)
        val topContributors = contributions
            .filter { it.netContribution > 0 }
            .sortedByDescending { it.netContribution }
            .take(3)

        val item = ItemStack.of(Material.GOLD_BLOCK)
            .name(lang.legacy("menu.statistics.item.top_contributors.name"))
            .lore(lang.legacy("menu.statistics.item.top_contributors.lore.description"))

        if (topContributors.isNotEmpty()) {
            item.lore(lang.legacy("menu.common.blank"))
            topContributors.forEachIndexed { index, contribution ->
                val playerName = contribution.playerName ?: lang.raw("general.unknown")
                item.lore(rankedContributionLore(index + 1, playerName, contribution.netContribution))
            }
        } else {
            item.lore(lang.legacy("menu.statistics.common.no_contribution_data"))
        }

        val guiItem = GuiItem(item) {
            openTopContributorsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKillDeathRatiosButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack.of(Material.COMPARATOR)
            .name(lang.legacy("menu.statistics.item.kd.name"))
            .lore(lang.legacy("menu.statistics.common.kill_death_ratio", "ratio" to decimalFormat.format(killStats.killDeathRatio)))
            .lore(kdRatingLore(killStats.killDeathRatio))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.statistics.common.efficiency_score", "value" to calculateEfficiencyScore(killStats)))

        val guiItem = GuiItem(item) {
            openKDAnalysisDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRecentActivityButton(pane: StaticPane, x: Int, y: Int) {
        val recentKills = killService.getRecentGuildKills(guild.id, 10)
        val recentActivity = recentKills.size

        val item = ItemStack.of(Material.CLOCK)
            .name(lang.legacy("menu.statistics.item.recent.name"))
            .lore(lang.legacy("menu.statistics.item.recent.lore.latest"))
            .lore(lang.legacy("menu.statistics.item.recent.lore.kills", "count" to recentActivity))
            .lore(activityLevelLore(recentActivity))

        val guiItem = GuiItem(item) {
            openRecentActivityDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPeriodStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.statistics.item.period.name"))
            .lore(lang.legacy("menu.statistics.item.period.lore.description"))
            .lore(lang.legacy("menu.statistics.item.period.lore.periods"))
            .lore(lang.legacy("menu.statistics.common.coming_soon"))

        val guiItem = GuiItem(item) {
            openPeriodStatsMenu()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRivalryStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.RED_BANNER)
            .name(lang.legacy("menu.statistics.item.rivalry.name"))
            .lore(lang.legacy("menu.statistics.item.rivalry.lore.description"))
            .lore(lang.legacy("menu.statistics.item.rivalry.lore.rankings"))
            .lore(lang.legacy("menu.statistics.common.coming_soon"))

        val guiItem = GuiItem(item) {
            openRivalryStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }


    private fun addAchievementsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)
        val achievementCount = calculateAchievementCount(killStats)

        val item = ItemStack.of(Material.TROPICAL_FISH_BUCKET)
            .name(lang.legacy("menu.statistics.item.achievements.name"))
            .lore(lang.legacy("menu.statistics.item.achievements.lore.count", "count" to achievementCount))
            .lore(achievementKillsLore(killStats.totalKills))
            .lore(achievementNetKillsLore(killStats.netKills))
            .lore(lang.legacy("menu.statistics.common.coming_soon"))

        val guiItem = GuiItem(item) {
            openAchievementsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTrendAnalysisButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.REPEATER)
            .name(lang.legacy("menu.statistics.item.kill_trends.name"))
            .lore(lang.legacy("menu.statistics.item.kill_trends.lore.description"))
            .lore(lang.legacy("menu.statistics.item.kill_trends.lore.patterns"))
            .lore(lang.legacy("menu.statistics.item.kill_trends.lore.chart"))

        val guiItem = GuiItem(item) {
            renderKillTrendChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addComparisonButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.COMPARATOR)
            .name(lang.legacy("menu.statistics.item.contribution_chart.name"))
            .lore(lang.legacy("menu.statistics.item.contribution_chart.lore.description"))
            .lore(lang.legacy("menu.statistics.item.contribution_chart.lore.chart"))
            .lore(lang.legacy("menu.statistics.item.contribution_chart.lore.details"))

        val guiItem = GuiItem(item) {
            renderMemberContributionsChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addExportStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.statistics.item.export.name"))
            .lore(lang.legacy("menu.statistics.item.export.lore.description"))
            .lore(lang.legacy("menu.statistics.item.export.lore.format"))
            .lore(lang.legacy("menu.statistics.common.coming_soon"))

        val guiItem = GuiItem(item) {
            exportGuildStatistics()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRefreshStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.legacy("menu.statistics.item.refresh.name"))
            .lore(lang.legacy("menu.statistics.item.refresh.lore.description"))
            .lore(lang.legacy("menu.statistics.item.refresh.lore.details"))

        val guiItem = GuiItem(item) {
            player.sendMessage(lang.msg("menu.statistics.feedback.refreshing"))
            // Reopen the menu to refresh all data
            open()
        }
        pane.addItem(guiItem, x, y)
    }


    private fun getRankColor(rank: Int): String {
        return when (rank) {
            1 -> "6" // Gold
            2 -> "7" // Gray
            3 -> "c" // Red
            else -> "f" // White
        }
    }

    private fun rankedKillsLore(rank: Int, playerName: String, kills: Int): String {
        return when (rank) {
            1 -> lang.legacy("menu.statistics.common.ranked_kills.first", "rank" to rank, "player" to playerName, "kills" to kills)
            2 -> lang.legacy("menu.statistics.common.ranked_kills.second", "rank" to rank, "player" to playerName, "kills" to kills)
            3 -> lang.legacy("menu.statistics.common.ranked_kills.third", "rank" to rank, "player" to playerName, "kills" to kills)
            else -> lang.legacy("menu.statistics.common.ranked_kills.other", "rank" to rank, "player" to playerName, "kills" to kills)
        }
    }

    private fun rankedContributionLore(rank: Int, playerName: String, contribution: Int): String {
        return when (rank) {
            1 -> lang.legacy("menu.statistics.common.ranked_contribution.first", "rank" to rank, "player" to playerName, "amount" to contribution)
            2 -> lang.legacy("menu.statistics.common.ranked_contribution.second", "rank" to rank, "player" to playerName, "amount" to contribution)
            3 -> lang.legacy("menu.statistics.common.ranked_contribution.third", "rank" to rank, "player" to playerName, "amount" to contribution)
            else -> lang.legacy("menu.statistics.common.ranked_contribution.other", "rank" to rank, "player" to playerName, "amount" to contribution)
        }
    }

    private fun kdRatingLore(ratio: Double): String {
        val rating = getKDRating(ratio)
        return when {
            ratio >= 3.0 -> lang.legacy("menu.statistics.common.performance_grade.green", "rating" to rating)
            ratio >= 1.5 -> lang.legacy("menu.statistics.common.performance_grade.yellow", "rating" to rating)
            ratio >= 1.0 -> lang.legacy("menu.statistics.common.performance_grade.gold", "rating" to rating)
            else -> lang.legacy("menu.statistics.common.performance_grade.red", "rating" to rating)
        }
    }

    private fun activityLevelLore(activity: Int): String {
        val level = getActivityLevel(activity)
        return when {
            activity >= 20 -> lang.legacy("menu.statistics.common.activity_level.green", "level" to level)
            activity >= 10 -> lang.legacy("menu.statistics.common.activity_level.yellow", "level" to level)
            activity >= 5 -> lang.legacy("menu.statistics.common.activity_level.gold", "level" to level)
            else -> lang.legacy("menu.statistics.common.activity_level.red", "level" to level)
        }
    }

    private fun achievementKillsLore(kills: Int): String = lang.legacy(
        "menu.statistics.item.achievements.lore.total_kills",
        "milestone" to getKillMilestone(kills),
    )

    private fun achievementNetKillsLore(netKills: Int): String = lang.legacy(
        "menu.statistics.item.achievements.lore.net_kills",
        "milestone" to getNetKillMilestone(netKills),
    )

    private fun getKDRating(kdRatio: Double): String {
        return when {
            kdRatio >= 5.0 -> lang.raw("menu.statistics.rating.kd.godlike")
            kdRatio >= 3.0 -> lang.raw("menu.statistics.rating.kd.excellent")
            kdRatio >= 2.0 -> lang.raw("menu.statistics.rating.kd.very_good")
            kdRatio >= 1.5 -> lang.raw("menu.statistics.rating.kd.good")
            kdRatio >= 1.0 -> lang.raw("menu.statistics.rating.kd.average")
            kdRatio >= 0.5 -> lang.raw("menu.statistics.rating.kd.below_average")
            else -> lang.raw("menu.statistics.rating.kd.needs_improvement")
        }
    }

    private fun calculateEfficiencyScore(killStats: GuildKillStats): Int {
        val kdRatio = killStats.killDeathRatio
        return when {
            kdRatio >= 3.0 -> 100
            kdRatio >= 2.0 -> 85
            kdRatio >= 1.5 -> 70
            kdRatio >= 1.0 -> 55
            kdRatio >= 0.7 -> 40
            else -> 25
        }
    }

    private fun getActivityLevel(activity: Int): String {
        return when {
            activity >= 50 -> lang.raw("menu.statistics.rating.activity.extremely_active")
            activity >= 20 -> lang.raw("menu.statistics.rating.activity.very_active")
            activity >= 10 -> lang.raw("menu.statistics.rating.activity.active")
            activity >= 5 -> lang.raw("menu.statistics.rating.activity.moderately_active")
            activity >= 1 -> lang.raw("menu.statistics.rating.activity.lightly_active")
            else -> lang.raw("menu.statistics.rating.activity.inactive")
        }
    }

    private fun calculateAchievementCount(killStats: GuildKillStats): Int {
        var count = 0
        if (killStats.totalKills >= 100) count++
        if (killStats.totalKills >= 500) count++
        if (killStats.totalKills >= 1000) count++
        if (killStats.netKills >= 100) count++
        if (killStats.netKills >= 500) count++
        if (killStats.killDeathRatio >= 2.0) count++
        return count
    }

    private fun getAchievementColor(value: Int): String {
        return when {
            value >= 1000 -> "6" // Gold
            value >= 500 -> "e" // Yellow
            value >= 100 -> "a" // Green
            value >= 50 -> "b" // Aqua
            else -> "f" // White
        }
    }

    private fun getKillMilestone(kills: Int): String {
        return when {
            kills >= 10000 -> lang.raw("menu.statistics.rating.kill_milestone.massacre")
            kills >= 5000 -> lang.raw("menu.statistics.rating.kill_milestone.slaughter")
            kills >= 1000 -> lang.raw("menu.statistics.rating.kill_milestone.carnage")
            kills >= 500 -> lang.raw("menu.statistics.rating.kill_milestone.bloodbath")
            kills >= 100 -> lang.raw("menu.statistics.rating.kill_milestone.butcher")
            else -> lang.raw("menu.statistics.rating.kill_milestone.novice")
        }
    }

    private fun getNetKillMilestone(netKills: Int): String {
        return when {
            netKills >= 1000 -> lang.raw("menu.statistics.rating.net_kill_milestone.dominator")
            netKills >= 500 -> lang.raw("menu.statistics.rating.net_kill_milestone.conqueror")
            netKills >= 100 -> lang.raw("menu.statistics.rating.net_kill_milestone.warrior")
            netKills >= 0 -> lang.raw("menu.statistics.rating.net_kill_milestone.balanced")
            netKills >= -50 -> lang.raw("menu.statistics.rating.net_kill_milestone.challenged")
            else -> lang.raw("menu.statistics.rating.net_kill_milestone.struggling")
        }
    }

    // Additional detail view functions
    private fun openTopKillersDetail() {
        try {
            val guildMembers = memberService.getGuildMembers(guild.id).map { it.playerId }
            val topKillers = killService.getTopKillers(guildMembers, 10)

            val gui = statisticsGui(5)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 5)
            gui.addPane(pane)

            val titleItem = ItemStack.of(Material.TOTEM_OF_UNDYING)
                .name(lang.legacy("menu.statistics.detail.top_killers.name"))
                .lore(lang.legacy("menu.statistics.detail.top_killers.description"))

            if (topKillers.isNotEmpty()) {
                titleItem.lore(lang.legacy("menu.common.blank"))
                topKillers.take(10).forEachIndexed { index, (playerId, stats) ->
                    val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
                    titleItem.lore(rankedKillsLore(index + 1, playerName, stats.totalKills))
                }
            } else {
                titleItem.lore(lang.legacy("menu.statistics.common.no_kill_data"))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.top_killers"))
            logger.error("Error opening top killers detail for guild ${guild.id}", e)
        }
    }

    private fun openTopContributorsDetail() {
        try {
            val contributions = bankService.getMemberContributions(guild.id)
            val topContributors = contributions
                .filter { it.netContribution > 0 }
                .sortedByDescending { it.netContribution }
                .take(10)

            val gui = statisticsGui(5)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 5)
            gui.addPane(pane)

            val titleItem = ItemStack.of(Material.GOLD_BLOCK)
                .name(lang.legacy("menu.statistics.detail.top_contributors.name"))
                .lore(lang.legacy("menu.statistics.item.top_contributors.lore.description"))

            if (topContributors.isNotEmpty()) {
                titleItem.lore(lang.legacy("menu.common.blank"))
                topContributors.forEachIndexed { index, contribution ->
                    val playerName = contribution.playerName ?: lang.raw("general.unknown")
                    titleItem.lore(rankedContributionLore(index + 1, playerName, contribution.netContribution))
                }
            } else {
                titleItem.lore(lang.legacy("menu.statistics.common.no_contribution_data"))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.top_contributors"))
            logger.error("Error opening top contributors detail for guild ${guild.id}", e)
        }
    }

    private fun openKDAnalysisDetail() {
        try {
            val killStats = killService.getGuildKillStats(guild.id)

            val gui = statisticsGui(4)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 4)
            gui.addPane(pane)

            val kdItem = ItemStack.of(Material.COMPARATOR)
                .name(lang.legacy("menu.statistics.detail.kd.name"))
                .lore(lang.legacy("menu.statistics.common.kd_ratio", "ratio" to decimalFormat.format(killStats.killDeathRatio)))
                .lore(lang.legacy("menu.statistics.common.total_kills_green", "count" to killStats.totalKills))
                .lore(lang.legacy("menu.statistics.common.total_deaths_red", "count" to killStats.totalDeaths))
                .lore(netKillsLore(killStats.netKills))
                .lore(lang.legacy("menu.common.blank"))
                .lore(kdRatingLore(killStats.killDeathRatio))
                .lore(lang.legacy("menu.statistics.common.efficiency_score", "value" to calculateEfficiencyScore(killStats)))
            pane.addItem(GuiItem(kdItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 3)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.kd"))
            logger.error("Error opening K/D analysis for guild ${guild.id}", e)
        }
    }

    private fun openRecentActivityDetail() {
        try {
            val recentKills = killService.getRecentGuildKills(guild.id, 15)

            val gui = statisticsGui(5)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 5)
            gui.addPane(pane)

            val titleItem = ItemStack.of(Material.CLOCK)
                .name(lang.legacy("menu.statistics.detail.recent.name"))
                .lore(lang.legacy("menu.statistics.item.recent.lore.kills", "count" to recentKills.size))
                .lore(activityLevelLore(recentKills.size))

            if (recentKills.isNotEmpty()) {
                titleItem.lore(lang.legacy("menu.common.blank"))
                recentKills.take(10).forEach { kill ->
                    val killerName = Bukkit.getOfflinePlayer(kill.killerId).name ?: kill.killerId.toString().take(8)
                    val victimName = Bukkit.getOfflinePlayer(kill.victimId).name ?: kill.victimId.toString().take(8)
                    titleItem.lore(if (!kill.weapon.isNullOrEmpty()) {
                        lang.legacy("menu.statistics.detail.recent.kill_with_weapon", "killer" to killerName, "victim" to victimName, "weapon" to kill.weapon)
                    } else {
                        lang.legacy("menu.statistics.detail.recent.kill", "killer" to killerName, "victim" to victimName)
                    })
                }
            }
            if (recentKills.size > 10) {
                titleItem.lore(lang.legacy("menu.statistics.common.more", "count" to recentKills.size - 10))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.statistics.item.back.name"))
                .lore(lang.legacy("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.recent"))
            logger.error("Error opening recent activity for guild ${guild.id}", e)
        }
    }

    private fun openPeriodStatsMenu() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.period"))
    }

    private fun openRivalryStatsDetail() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.rivalry"))
    }


    private fun openAchievementsDetail() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.achievements"))
    }

    private fun openTrendAnalysis() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.trends"))
    }

    private fun openGuildComparison() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.comparison"))
    }


    private fun exportGuildStatistics() {
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.export"))
        player.sendMessage(lang.msg("menu.statistics.feedback.coming_soon.export_description"))
    }

    // Chart rendering methods
    private fun renderGuildBalanceChart() {
        try {
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.generating"))

            // Get real transaction history from the database
            val transactions = bankService.getTransactionHistory(guild.id, 50)

            if (transactions.isEmpty()) {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.no_data"))
                return
            }

            // Group transactions by date and calculate balance progression
            val dateToBalance = mutableMapOf<LocalDate, Int>()
            for (transaction in transactions) {
                val date = LocalDateTime.ofInstant(transaction.timestamp, ZoneId.systemDefault()).toLocalDate()
                dateToBalance[date] = (dateToBalance[date] ?: 0) + transaction.amount
            }

            val dailyBalances = dateToBalance.toList()
                .sortedBy { it.first }
                .takeLast(30) // Last 30 days
                .map { it.first.toString() to it.second }

            if (dailyBalances.isEmpty()) {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.process_failed"))
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = lang.legacy("menu.statistics.chart.title.balance", "guild" to guild.name),
                dataPoints = dailyBalances,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.success"))
            } else {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.failure"))
            }

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.balance.error"))
            e.printStackTrace()
        }
    }

    private fun renderKillTrendChart() {
        try {
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.kills.generating"))

            // Get real kill data for the past 7 weeks
            val now = Instant.now()
            val weekTrends = mutableListOf<Pair<String, Int>>()

            for (weeksAgo in 6 downTo 0) {
                val weekStart = now.minusSeconds(weeksAgo * 7L * 24L * 60L * 60L)
                val weekEnd = weekStart.plusSeconds(7L * 24L * 60L * 60L)

                try {
                    val weekStats = killService.getKillStatsForPeriod(guild.id, weekStart, weekEnd)
                    val weekLabel = if (weeksAgo == 0) {
                        lang.raw("menu.statistics.chart.label.this_week")
                    } else {
                        lang.legacy("menu.statistics.chart.label.weeks_ago", "weeks" to weeksAgo)
                    }
                    weekTrends.add(weekLabel to weekStats.totalKills)
                } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                    // If we can't get data for this week, use 0
                    val weekLabel = if (weeksAgo == 0) {
                        lang.raw("menu.statistics.chart.label.this_week")
                    } else {
                        lang.legacy("menu.statistics.chart.label.weeks_ago", "weeks" to weeksAgo)
                    }
                    weekTrends.add(weekLabel to 0)
                }
            }

            // Reverse to show chronological order
            weekTrends.reverse()

            if (weekTrends.all { it.second == 0 }) {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.kills.no_data"))
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = lang.legacy("menu.statistics.chart.title.kills", "guild" to guild.name),
                dataPoints = weekTrends,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.kills.success"))
            } else {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.kills.failure"))
            }

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.kills.error"))
            e.printStackTrace()
        }
    }

    private fun renderMemberContributionsChart() {
        try {
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.generating"))

            // Get real member contribution data from BankService
            val contributions = bankService.getMemberContributions(guild.id)

            if (contributions.isEmpty()) {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.no_data"))
                return
            }

            // Convert to chart data format with player names
            val chartData = contributions
                .filter { it.netContribution > 0 }
                .sortedByDescending { it.netContribution }
                .take(10) // Top 10 contributors
                .map { (it.playerName ?: lang.raw("general.unknown")) to it.netContribution }

            if (chartData.isEmpty()) {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.no_positive"))
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = lang.legacy("menu.statistics.chart.title.contributions", "guild" to guild.name),
                dataPoints = chartData,
                chartType = ChartType.BAR,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.success"))
            } else {
                player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.failure"))
            }

        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.statistics.feedback.chart.contributions.error"))
            e.printStackTrace()
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

