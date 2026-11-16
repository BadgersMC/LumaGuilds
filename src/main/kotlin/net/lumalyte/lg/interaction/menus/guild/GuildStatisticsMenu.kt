package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.NexoItemProvider
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.PaginatedPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.*

class GuildStatisticsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild): Menu, KoinComponent {

    private val killService: KillService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()
    private val leaderboardService: LeaderboardService by inject()
    private val invitationStatisticsService: InvitationStatisticsService by inject()
    private val killLog: net.lumalyte.lg.application.persistence.GuildKillLog by inject()

    private val logger = LoggerFactory.getLogger(GuildStatisticsMenu::class.java)

    private val decimalFormat = DecimalFormat("#.##")

    companion object {
        internal const val INVITERS_PER_PAGE = 10
        private const val MAX_RIVALS = 50
    }

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.guiTitle("menu.statistics.title", "guild" to guild.name),
        ))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                guiEvent.isCancelled = true
        }

        val pane = StaticPane(0, 0, 9, 3)
        gui.addPane(pane)

        // REQ-201: the guild's record on top, the people behind it in the middle.
        addCombatButton(pane, 2, 0)
        addWarStatsButton(pane, 3, 0)
        addRecentActivityButton(pane, 4, 0)
        addRivalryStatsButton(pane, 5, 0)
        addAchievementsButton(pane, 6, 0)
        addTopKillersButton(pane, 3, 1)
        addTopContributorsButton(pane, 4, 1)
        addTopInvitersButton(pane, 5, 1)
        addBackButton(pane, 4, 2)

        gui.show(player)
    }

    /** REQ-200/REQ-201: kills, deaths and K/D, then this week, the last 30 days and all time. */
    private fun addCombatButton(pane: StaticPane, x: Int, y: Int) {
        val now = java.time.Instant.now()
        val all = killService.getGuildKillStats(guild.id)
        val week = killService.getKillStatsForPeriod(guild.id, now.minus(java.time.Duration.ofDays(7)), now)
        val month = killService.getKillStatsForPeriod(guild.id, now.minus(java.time.Duration.ofDays(30)), now)
        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_kills") { ItemStack.of(Material.DIAMOND_SWORD) }
            .name(lang.gui("menu.statistics.item.combat.name"))
            .lore(lang.gui("menu.statistics.item.combat.lore.totals", "kills" to all.totalKills, "deaths" to all.totalDeaths))
            .lore(lang.gui("menu.statistics.common.kd_ratio", "ratio" to decimalFormat.format(all.killDeathRatio)))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.statistics.item.combat.lore.week", "kills" to week.totalKills, "deaths" to week.totalDeaths))
            .lore(lang.gui("menu.statistics.item.combat.lore.month", "kills" to month.totalKills, "deaths" to month.totalDeaths))
            .lore(lang.gui("menu.statistics.item.combat.lore.all_time", "kills" to all.totalKills, "deaths" to all.totalDeaths))
        if (all.totalKills == 0 && all.totalDeaths == 0) item.lore(lang.gui("menu.statistics.item.combat.lore.empty"))
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addWarStatsButton(pane: StaticPane, x: Int, y: Int) {
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            val warHistory = warService.getWarHistory(guild.id, 50)

            val wins = warHistory.count { it.winner == guild.id }
            val losses = warHistory.count { it.winner != null && it.winner != guild.id }
            val draws = warHistory.count { it.winner == null }

            val item = NexoItemProvider.getItemStackOrFallback("lg_stats_wars") { ItemStack.of(Material.WHITE_BANNER) }
                .name(lang.gui("menu.statistics.item.wars.name"))
                .lore(lang.gui("menu.statistics.common.active_wars", "count" to activeWars.size))
                .lore(lang.gui("menu.statistics.common.total_wars", "count" to warHistory.size))
                .lore(lang.gui("menu.statistics.common.wins", "count" to wins))
                .lore(lang.gui("menu.statistics.common.losses", "count" to losses))
                .lore(lang.gui("menu.statistics.common.draws", "count" to draws))
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.statistics.common.win_rate", "rate" to decimalFormat.format(calculateWinRate(wins, warHistory.size))))

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        } catch (e: Exception) {
            logger.error("Unable to load war statistics for guild ${guild.id}", e)
            val item = NexoItemProvider.getItemStackOrFallback("lg_stats_wars") { ItemStack.of(Material.BARRIER) }
                .name(lang.gui("menu.statistics.item.wars.name"))
                .lore(lang.gui("menu.statistics.item.wars.lore.unavailable"))
                .lore(lang.gui("menu.statistics.common.unavailable"))
            pane.addItem(GuiItem(item), x, y)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.common.item.back.name"))

        val guiItem = GuiItem(item) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun statisticsGui(rows: Int): ChestGui = ChestGui(
        rows,
        MenuTitleBuilder.build(guild.guiTheme, rows, lang.guiTitle("menu.statistics.title", "guild" to guild.name)),
    )

    // Helper functions for calculations and ratings
    private fun calculateWinRate(wins: Int, totalWars: Int): Double {
        return if (totalWars > 0) (wins.toDouble() / totalWars) * 100 else 0.0
    }

    // Detail view functions
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
            .name(lang.gui("menu.statistics.detail.wars.active.name", "count" to activeWars.size))
            .lore(lang.gui("menu.statistics.detail.wars.active.description"))

        if (activeWars.isNotEmpty()) {
            activeWarsItem.lore(lang.gui("menu.common.blank"))
            activeWars.take(3).forEach { war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName: Any = enemyGuild?.name ?: lang.gui("menu.statistics.common.unknown_guild")
                val status = if (war.declaringGuildId == guild.id) {
                    lang.gui("menu.statistics.common.attacker")
                } else {
                    lang.gui("menu.statistics.common.defender")
                }
                val remainingTime = war.remainingDuration

                activeWarsItem.lore(lang.gui("menu.statistics.detail.wars.active.opponent", "guild" to enemyName, "status" to status))
                if (war.objectiveBased) {
                    activeWarsItem.lore(lang.gui("war_duration.objective"))
                } else if (remainingTime != null) {
                    val days = remainingTime.toDays()
                    val hours = remainingTime.toHours() % 24
                    activeWarsItem.lore(lang.gui("menu.statistics.detail.wars.active.remaining", "days" to days, "hours" to hours))
                } else {
                    activeWarsItem.lore(lang.gui("menu.statistics.detail.wars.active.expired"))
                }
            }

            if (activeWars.size > 3) {
                activeWarsItem.lore(lang.gui("menu.statistics.common.more", "count" to activeWars.size - 3))
            }
        } else {
            activeWarsItem.lore(lang.gui("menu.statistics.detail.wars.active.none"))
        }

        pane.addItem(GuiItem(activeWarsItem), 1, 0)
    }

    private fun addWarHistorySection(pane: StaticPane) {
        val warHistory = warService.getWarHistory(guild.id, 5)

        val historyItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.statistics.detail.wars.history.name"))
            .lore(lang.gui("menu.statistics.detail.wars.history.description"))

        if (warHistory.isNotEmpty()) {
            historyItem.lore(lang.gui("menu.common.blank"))

            warHistory.take(4).forEachIndexed { index, war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName: Any = enemyGuild?.name ?: lang.gui("menu.statistics.common.unknown_guild")

                val result = when {
                    war.winner == guild.id -> lang.gui("menu.statistics.detail.wars.history.result.won")
                    war.winner != null -> lang.gui("menu.statistics.detail.wars.history.result.lost")
                    else -> lang.gui("menu.statistics.detail.wars.history.result.draw")
                }

                historyItem.lore(lang.gui("menu.statistics.detail.wars.history.row", "rank" to index + 1, "guild" to enemyName, "result" to result))

                // Show duration if available
                val duration = war.startedAt?.let { start ->
                    war.endedAt?.let { end ->
                        java.time.Duration.between(start, end)
                    }
                }

                if (duration != null) {
                    val days = duration.toDays()
                    val hours = duration.toHours() % 24
                    historyItem.lore(lang.gui("menu.statistics.detail.wars.history.duration", "days" to days, "hours" to hours))
                }
            }

            if (warHistory.size > 4) {
                historyItem.lore(lang.gui("menu.statistics.detail.wars.history.more", "count" to warHistory.size - 4))
            }
        } else {
            historyItem.lore(lang.gui("menu.statistics.detail.wars.history.none"))
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
            .name(lang.gui("menu.statistics.detail.wars.summary.name"))
            .lore(lang.gui("menu.statistics.detail.wars.summary.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.statistics.common.total_wars", "count" to warHistory.size))
            .lore(lang.gui("menu.statistics.common.wins", "count" to wins))
            .lore(lang.gui("menu.statistics.common.losses", "count" to losses))
            .lore(lang.gui("menu.statistics.common.draws", "count" to draws))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.statistics.common.win_rate", "rate" to String.format("%.1f", winRate)))

        // Add current win streak
        val recentWars = warHistory.take(10)
        val currentStreak = calculateCurrentStreak(recentWars, guild.id)
        if (currentStreak > 0) {
            val streakLore = if (recentWars.first().winner == guild.id) {
                lang.gui("menu.statistics.detail.wars.summary.win_streak", "count" to currentStreak)
            } else {
                lang.gui("menu.statistics.detail.wars.summary.loss_streak", "count" to currentStreak)
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
                .name(lang.gui("menu.statistics.detail.wars.kills.name"))
                .lore(lang.gui("menu.statistics.detail.wars.kills.description"))

            // Get kill stats for each active war
            activeWars.forEach { war ->
                val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyGuildId)

                if (enemyGuild != null) {
                    val killsBetween = killService.getKillsBetweenGuilds(guild.id, enemyGuildId, 100)

                    val guildKills = killsBetween.count { it.killerGuildId == guild.id }
                    val enemyKills = killsBetween.count { it.killerGuildId == enemyGuildId }

                    warKillStats.lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.statistics.detail.wars.kills.opponent", "guild" to enemyGuild.name))
                        .lore(lang.gui("menu.statistics.detail.wars.kills.yours", "count" to guildKills))
                        .lore(lang.gui("menu.statistics.detail.wars.kills.enemy", "count" to enemyKills))
                        .lore(lang.gui("menu.statistics.detail.wars.kills.ratio", "ratio" to calculateKillRatio(guildKills, enemyKills)))
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

    private fun addTopKillersButton(pane: StaticPane, x: Int, y: Int) {
        val guildMembers = memberService.getGuildMembers(guild.id).map { it.playerId }
        val topKillers = killService.getTopKillers(guildMembers, 5)

        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_top_killers") { ItemStack.of(Material.TOTEM_OF_UNDYING) }
            .name(lang.gui("menu.statistics.item.top_killers.name"))
            .lore(lang.gui("menu.statistics.item.top_killers.lore.description"))

        if (topKillers.isNotEmpty()) {
            item.lore(lang.gui("menu.common.blank"))
            topKillers.take(3).forEachIndexed { index, (playerId, stats) ->
                val playerName: Any = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
                item.lore(rankedKillsLore(index + 1, playerName, stats.totalKills))
            }
        } else {
            item.lore(lang.gui("menu.statistics.common.no_kill_data"))
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

        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_top_contributors") { ItemStack.of(Material.GOLD_BLOCK) }
            .name(lang.gui("menu.statistics.item.top_contributors.name"))
            .lore(lang.gui("menu.statistics.item.top_contributors.lore.description"))

        if (topContributors.isNotEmpty()) {
            item.lore(lang.gui("menu.common.blank"))
            topContributors.forEachIndexed { index, contribution ->
                val playerName: Any = contribution.playerName ?: lang.gui("general.unknown")
                item.lore(rankedContributionLore(index + 1, playerName, contribution.netContribution))
            }
        } else {
            item.lore(lang.gui("menu.statistics.common.no_contribution_data"))
        }

        val guiItem = GuiItem(item) {
            openTopContributorsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTopInvitersButton(pane: StaticPane, x: Int, y: Int) {
        val topInviters = invitationStatisticsService.getLeaderboard(guild.id, 3)
        val totalInvitations = invitationStatisticsService.getTotalInvitations(guild.id)
        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_top_inviters") { ItemStack.of(Material.WRITABLE_BOOK) }
            .name(lang.gui("menu.statistics.item.top_inviters.name"))
            .lore(lang.gui("menu.statistics.item.top_inviters.lore.description"))
            .lore(lang.gui("menu.statistics.common.total_invitations", "count" to totalInvitations))

        if (topInviters.isNotEmpty()) {
            item.lore(lang.gui("menu.common.blank"))
            topInviters.forEachIndexed { index, entry ->
                val playerName = Bukkit.getOfflinePlayer(entry.inviterPlayerId).name
                    ?: entry.inviterPlayerId.toString().take(8)
                item.lore(rankedInvitationsLore(index + 1, playerName, entry.inviteCount))
            }
        } else {
            item.lore(lang.gui("menu.statistics.common.no_invitation_data"))
        }

        pane.addItem(GuiItem(item) { openTopInvitersDetail() }, x, y)
    }
    private fun addRecentActivityButton(pane: StaticPane, x: Int, y: Int) {
        val recentKills = killService.getRecentGuildKills(guild.id, 10)
        val recentActivity = recentKills.size

        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_recent") { ItemStack.of(Material.CLOCK) }
            .name(lang.gui("menu.statistics.item.recent.name"))
            .lore(lang.gui("menu.statistics.item.recent.lore.latest"))
            .lore(lang.gui("menu.statistics.item.recent.lore.kills", "count" to recentActivity))
            .lore(activityLevelLore(recentActivity))

        val guiItem = GuiItem(item) {
            openRecentActivityDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRivalryStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_stats_rivalry") { ItemStack.of(Material.RED_BANNER) }
            .name(lang.gui("menu.statistics.item.rivalry.name"))
            .lore(lang.gui("menu.statistics.item.rivalry.lore.description"))
            .lore(lang.gui("menu.statistics.item.rivalry.lore.rankings"))

        val guiItem = GuiItem(item) {
            openRivalryStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }


    /** REQ-274: the same Guild Advancements as everywhere else, with this guild's progress on the button. */
    private fun addAchievementsButton(pane: StaticPane, x: Int, y: Int) {
        val record = runCatching {
            getKoin().getOrNull<net.lumalyte.lg.infrastructure.services.GuildAchievementRuntime>()?.let { runtime ->
                runtime.read(player)?.let { it to runtime.engine.definitions.filter { node -> node.requirement != "ROOT" } }
            }
        }.getOrNull()
        val milestones = runCatching { combatMilestones() }.getOrDefault(emptyList())

        var item = NexoItemProvider.getItemStackOrFallback("lg_stats_achievements") { ItemStack.of(Material.TROPICAL_FISH_BUCKET) }
            .name(lang.gui("menu.statistics.item.achievements.name"))
        if (record != null) {
            val (progress, nodes) = record
            val earned = nodes.count { (progress.progress[it.id] ?: 0) >= it.goal }
            item = item.lore(lang.gui("menu.statistics.item.achievements.lore.earned", "count" to earned, "total" to nodes.size))
            progress.tracked?.let { id -> nodes.find { it.id == id } }?.let { tracked ->
                item = item.lore(lang.gui("menu.statistics.item.achievements.lore.tracking", "title" to tracked.title,
                    "current" to (progress.progress[tracked.id] ?: 0), "goal" to tracked.goal))
            }
        }
        item = item.lore(lang.gui("menu.statistics.item.achievements.lore.count",
            "count" to milestones.count { it.achieved }, "total" to milestones.size))
            .lore(lang.gui("menu.statistics.item.achievements.lore.open"))

        val guiItem = GuiItem(item) {
            if (net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.supportsDialog(player)) {
                menuNavigator.openMenu(GuildAchievementsMenu(menuNavigator, player, guild, fromStatistics = true))
            } else {
                openAchievementsDetail()
            }
        }
        pane.addItem(guiItem, x, y)
    }


    private fun rankedKillsLore(rank: Int, playerName: Any, kills: Int): Component {
        return when (rank) {
            1 -> lang.gui("menu.statistics.common.ranked_kills.first", "rank" to rank, "player" to playerName, "kills" to kills)
            2 -> lang.gui("menu.statistics.common.ranked_kills.second", "rank" to rank, "player" to playerName, "kills" to kills)
            3 -> lang.gui("menu.statistics.common.ranked_kills.third", "rank" to rank, "player" to playerName, "kills" to kills)
            else -> lang.gui("menu.statistics.common.ranked_kills.other", "rank" to rank, "player" to playerName, "kills" to kills)
        }
    }

    private fun rankedContributionLore(rank: Int, playerName: Any, contribution: Int): Component {
        return when (rank) {
            1 -> lang.gui("menu.statistics.common.ranked_contribution.first", "rank" to rank, "player" to playerName, "amount" to contribution)
            2 -> lang.gui("menu.statistics.common.ranked_contribution.second", "rank" to rank, "player" to playerName, "amount" to contribution)
            3 -> lang.gui("menu.statistics.common.ranked_contribution.third", "rank" to rank, "player" to playerName, "amount" to contribution)
            else -> lang.gui("menu.statistics.common.ranked_contribution.other", "rank" to rank, "player" to playerName, "amount" to contribution)
        }
    }

    private fun rankedInvitationsLore(rank: Int, playerName: Any, count: Int): Component {
        return when (rank) {
            1 -> lang.gui("menu.statistics.common.ranked_invitations.first", "rank" to rank, "player" to playerName, "count" to count)
            2 -> lang.gui("menu.statistics.common.ranked_invitations.second", "rank" to rank, "player" to playerName, "count" to count)
            3 -> lang.gui("menu.statistics.common.ranked_invitations.third", "rank" to rank, "player" to playerName, "count" to count)
            else -> lang.gui("menu.statistics.common.ranked_invitations.other", "rank" to rank, "player" to playerName, "count" to count)
        }
    }
    private fun activityLevelLore(activity: Int): Component {
        val level = getActivityLevel(activity)
        return when {
            activity >= 20 -> lang.gui("menu.statistics.common.activity_level.green", "level" to level)
            activity >= 10 -> lang.gui("menu.statistics.common.activity_level.yellow", "level" to level)
            activity >= 5 -> lang.gui("menu.statistics.common.activity_level.gold", "level" to level)
            else -> lang.gui("menu.statistics.common.activity_level.red", "level" to level)
        }
    }

    private fun getActivityLevel(activity: Int): Component {
        return when {
            activity >= 50 -> lang.gui("menu.statistics.rating.activity.extremely_active")
            activity >= 20 -> lang.gui("menu.statistics.rating.activity.very_active")
            activity >= 10 -> lang.gui("menu.statistics.rating.activity.active")
            activity >= 5 -> lang.gui("menu.statistics.rating.activity.moderately_active")
            activity >= 1 -> lang.gui("menu.statistics.rating.activity.lightly_active")
            else -> lang.gui("menu.statistics.rating.activity.inactive")
        }
    }

    private fun combatMilestones(): List<net.lumalyte.lg.domain.achievements.GuildCombatMilestone> {
        val stats = killService.getGuildKillStats(guild.id)
        val wars = warService.getWarHistory(guild.id, 100)
        return net.lumalyte.lg.domain.achievements.GuildCombatMilestones.evaluate(
            stats.totalKills, stats.totalDeaths, stats.netKills, stats.killDeathRatio,
            wars.size, wars.count { it.winner == guild.id },
        )
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

            val titleItem = NexoItemProvider.getItemStackOrFallback("lg_stats_top_killers") { ItemStack.of(Material.TOTEM_OF_UNDYING) }
                .name(lang.gui("menu.statistics.detail.top_killers.name"))
                .lore(lang.gui("menu.statistics.detail.top_killers.description"))

            if (topKillers.isNotEmpty()) {
                titleItem.lore(lang.gui("menu.common.blank"))
                topKillers.take(10).forEachIndexed { index, (playerId, stats) ->
                    val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
                    titleItem.lore(rankedKillsLore(index + 1, playerName, stats.totalKills))
                }
            } else {
                titleItem.lore(lang.gui("menu.statistics.common.no_kill_data"))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
                .lore(lang.gui("menu.statistics.item.back.lore"))
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

            val titleItem = NexoItemProvider.getItemStackOrFallback("lg_stats_top_contributors") { ItemStack.of(Material.GOLD_BLOCK) }
                .name(lang.gui("menu.statistics.detail.top_contributors.name"))
                .lore(lang.gui("menu.statistics.item.top_contributors.lore.description"))

            if (topContributors.isNotEmpty()) {
                titleItem.lore(lang.gui("menu.common.blank"))
                topContributors.forEachIndexed { index, contribution ->
                    val playerName: Any = contribution.playerName ?: lang.gui("general.unknown")
                    titleItem.lore(rankedContributionLore(index + 1, playerName, contribution.netContribution))
                }
            } else {
                titleItem.lore(lang.gui("menu.statistics.common.no_contribution_data"))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
                .lore(lang.gui("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.top_contributors"))
            logger.error("Error opening top contributors detail for guild ${guild.id}", e)
        }
    }

    private fun openTopInvitersDetail(page: Int = 0) {
        try {
            val leaderboardPage = invitationStatisticsService.getLeaderboardPage(guild.id, page, INVITERS_PER_PAGE)
            val totalInvitations = invitationStatisticsService.getTotalInvitations(guild.id)
            val gui = statisticsGui(5)
            gui.setOnTopClick { it.isCancelled = true }
            gui.setOnBottomClick { event ->
                if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) event.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 5)
            gui.addPane(pane)

            val titleItem = ItemStack.of(Material.WRITABLE_BOOK)
                .name(lang.gui("menu.statistics.detail.top_inviters.name"))
                .lore(lang.gui("menu.statistics.detail.top_inviters.description"))
                .lore(lang.gui("menu.statistics.common.total_invitations", "count" to totalInvitations))

            if (leaderboardPage.entries.isNotEmpty()) {
                titleItem.lore(lang.gui("menu.common.blank"))
                val rankOffset = leaderboardPage.page * leaderboardPage.pageSize
                leaderboardPage.entries.forEachIndexed { index, entry ->
                    val playerName = Bukkit.getOfflinePlayer(entry.inviterPlayerId).name
                        ?: entry.inviterPlayerId.toString().take(8)
                    titleItem.lore(rankedInvitationsLore(rankOffset + index + 1, playerName, entry.inviteCount))
                }
            } else {
                titleItem.lore(lang.gui("menu.statistics.common.no_invitation_data"))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            if (leaderboardPage.totalPages > 1) {
                if (leaderboardPage.page > 0) {
                    val previous = ItemStack.of(Material.ARROW)
                        .name(lang.gui("menu.statistics.item.previous_page.name"))
                    pane.addItem(GuiItem(previous) { openTopInvitersDetail(leaderboardPage.page - 1) }, 2, 4)
                }

                val pageIndicator = ItemStack.of(Material.PAPER)
                    .name(lang.gui("menu.statistics.common.page_info", "page" to leaderboardPage.page + 1, "total" to leaderboardPage.totalPages))
                pane.addItem(GuiItem(pageIndicator), 4, 3)

                if (leaderboardPage.page < leaderboardPage.totalPages - 1) {
                    val next = ItemStack.of(Material.ARROW)
                        .name(lang.gui("menu.statistics.item.next_page.name"))
                    pane.addItem(GuiItem(next) { openTopInvitersDetail(leaderboardPage.page + 1) }, 6, 4)
                }
            }

            val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
                .lore(lang.gui("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)
            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.top_inviters"))
            logger.error("Error opening top inviters detail for guild ${guild.id}", e)
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
                .name(lang.gui("menu.statistics.detail.recent.name"))
                .lore(lang.gui("menu.statistics.item.recent.lore.kills", "count" to recentKills.size))
                .lore(activityLevelLore(recentKills.size))

            if (recentKills.isNotEmpty()) {
                titleItem.lore(lang.gui("menu.common.blank"))
                recentKills.take(10).forEach { kill ->
                    val killerName = Bukkit.getOfflinePlayer(kill.killerId).name ?: kill.killerId.toString().take(8)
                    val victimName = Bukkit.getOfflinePlayer(kill.victimId).name ?: kill.victimId.toString().take(8)
                    titleItem.lore(if (!kill.weapon.isNullOrEmpty()) {
                        lang.gui("menu.statistics.detail.recent.kill_with_weapon", "killer" to killerName, "victim" to victimName, "weapon" to kill.weapon)
                    } else {
                        lang.gui("menu.statistics.detail.recent.kill", "killer" to killerName, "victim" to victimName)
                    })
                }
            }
            if (recentKills.size > 10) {
                titleItem.lore(lang.gui("menu.statistics.common.more", "count" to recentKills.size - 10))
            }
            pane.addItem(GuiItem(titleItem), 4, 1)

            val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
                .lore(lang.gui("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 4)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.recent"))
            logger.error("Error opening recent activity for guild ${guild.id}", e)
        }
    }

    private var rivalryPage = 0
    private val rivalryItemsPerPage = 5

    private fun openRivalryStatsDetail() {
        rivalryPage = 0
        buildRivalryStatsDetail()
    }

    private fun buildRivalryStatsDetail() {
        try {
            val warHistory = warService.getWarHistory(guild.id, 100)
            // REQ-202: guilds fought most (kill log) first, then anyone fought in a war.
            val fought = runCatching { killLog.rivals(guild.id, MAX_RIVALS).map { it.guildId } }
                .onFailure { logger.error("Unable to read rivals for guild ${guild.id}", it) }
                .getOrDefault(emptyList())
            val rivalGuildIds = (fought + warHistory.map { war ->
                if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            }).distinct().filter { guildService.getGuild(it) != null }
            if (rivalGuildIds.isEmpty()) rivalryPage = 0

            val gui = statisticsGui(6)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val paginatedPane = PaginatedPane(0, 0, 9, 5)
            gui.addPane(paginatedPane)

            val totalPages = (rivalGuildIds.size + rivalryItemsPerPage - 1) / rivalryItemsPerPage
            if (rivalryPage >= totalPages && totalPages > 0) rivalryPage = totalPages - 1

            val startIndex = rivalryPage * rivalryItemsPerPage
            val endIndex = minOf(startIndex + rivalryItemsPerPage, rivalGuildIds.size)
            val pageGuilds = rivalGuildIds.subList(startIndex, endIndex)

            val pagePane = StaticPane(0, 0, 9, 5)
            if (rivalGuildIds.isEmpty()) {
                val emptyItem = NexoItemProvider.getItemStackOrFallback("lg_stats_rivalry") {
                    ItemStack.of(Material.GRAY_BANNER)
                }
                    .name(lang.gui("menu.statistics.item.rivalry.name"))
                    .lore(lang.gui("menu.statistics.common.no_rivalry_data"))
                pagePane.addItem(GuiItem(emptyItem), 4, 2)
            }
            pageGuilds.forEachIndexed { index, rivalId ->
                val enemyGuild = guildService.getGuild(rivalId)
                val enemyName: Any = enemyGuild?.name ?: lang.gui("menu.statistics.common.unknown_guild")
                val warsBetween = warHistory.filter { war ->
                    (war.declaringGuildId == guild.id && war.defendingGuildId == rivalId) ||
                            (war.declaringGuildId == rivalId && war.defendingGuildId == guild.id)
                }
                val wins = warsBetween.count { it.winner == guild.id }
                val losses = warsBetween.count { it.winner == rivalId }
                val draws = warsBetween.count { it.winner == null }

                val killsBetween = killService.getKillsBetweenGuilds(guild.id, rivalId, 100)
                val guildKills = killsBetween.count { it.killerGuildId == guild.id }
                val enemyKills = killsBetween.count { it.killerGuildId == rivalId }
                val kdr = if (enemyKills > 0) String.format("%.2f", guildKills.toDouble() / enemyKills) else guildKills.toString()

                val item = ItemStack.of(Material.RED_BANNER)
                    .name(lang.gui("menu.statistics.detail.rivalry.enemy", "guild" to enemyName))
                    .lore(lang.gui("menu.statistics.common.wars_fought", "count" to warsBetween.size))
                    .lore(lang.gui("menu.statistics.common.wins", "count" to wins))
                    .lore(lang.gui("menu.statistics.common.losses", "count" to losses))
                    .lore(lang.gui("menu.statistics.common.draws", "count" to draws))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.statistics.common.kdr_against", "ratio" to kdr))

                pagePane.addItem(GuiItem(item), 1, index)
            }
            paginatedPane.addPage(pagePane)
            paginatedPane.page = 0

            val navPane = StaticPane(0, 5, 9, 1)
            if (totalPages > 1) {
                if (rivalryPage > 0) {
                    val prevItem = NexoItemProvider.getItemStackOrFallback("lg_page_prev") { ItemStack.of(Material.ARROW) }
                        .name(lang.gui("menu.statistics.item.previous_page.name"))
                    navPane.addItem(GuiItem(prevItem) {
                        rivalryPage--; buildRivalryStatsDetail()
                    }, 2, 0)
                }

                val pageIndicator = ItemStack.of(Material.PAPER)
                    .name(lang.gui("menu.statistics.common.page_info", "page" to rivalryPage + 1, "total" to totalPages))
                navPane.addItem(GuiItem(pageIndicator), 4, 0)

                if (rivalryPage < totalPages - 1) {
                    val nextItem = NexoItemProvider.getItemStackOrFallback("lg_page_next") { ItemStack.of(Material.ARROW) }
                        .name(lang.gui("menu.statistics.item.next_page.name"))
                    navPane.addItem(GuiItem(nextItem) {
                        rivalryPage++; buildRivalryStatsDetail()
                    }, 6, 0)
                }
            }

            addBackButton(navPane, 8, 0)
            gui.addPane(navPane)
            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.rivalry"))
            logger.error("Error opening rivalry stats for guild ${guild.id}", e)
        }
    }

    private fun openAchievementsDetail() {
        try {
            val achievements = combatMilestones()

            val gui = statisticsGui(3)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                    guiEvent.isCancelled = true
            }
            val pane = StaticPane(0, 0, 9, 3)
            gui.addPane(pane)

            achievements.forEachIndexed { index, achievement ->
                val material = if (achievement.achieved) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE
                val itemName = if (achievement.achieved)
                    lang.gui("menu.statistics.detail.achievements.${achievement.key}.unlocked")
                else
                    lang.gui("menu.statistics.detail.achievements.${achievement.key}.locked")
                val item = ItemStack.of(material)
                    .name(itemName)
                    .lore(Component.text(achievement.condition))
                    .lore(Component.text(achievement.progress))
                    .lore(lang.gui("achievements.guild_rules"))
                val col = index % 7
                val row = index / 7
                pane.addItem(GuiItem(item), 1 + col, row)
            }

            val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
                .lore(lang.gui("menu.statistics.item.back.lore"))
            pane.addItem(GuiItem(backItem) { open() }, 4, 2)

            gui.show(player)
        } catch (e: Exception) {
            player.sendMessage(lang.msg("menu.statistics.feedback.load_failed.achievements"))
            logger.error("Error opening achievements for guild ${guild.id}", e)
        }
    }



    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

