package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.InvitationLeaderboardPage
import net.lumalyte.lg.application.services.InvitationStatisticsService
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildKillStats
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.domain.entities.LeaderboardType
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock statistics are a presentation adapter over the same authoritative
 * services used by the Java statistics UI. Missing data remains visibly missing;
 * this menu never invents plausible values for an unwired statistic.
 */
class BedrockGuildStatisticsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private var guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val killService: KillService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val guildService: GuildService by inject()
    private val leaderboardService: LeaderboardService by inject()
    private val invitationStatisticsService: InvitationStatisticsService by inject()
    private val progressionRepository: ProgressionRepository by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private var invitationPage = 0

    companion object {
        internal const val INVITERS_PER_PAGE = 5
        internal const val INVITATION_PAGE_DROPDOWN_INDEX = 17
    }

    override fun getForm(): Form {
        guild = guildService.getGuild(guild.id) ?: guild
        val config = getBedrockConfig()
        val statsIcon = BedrockFormUtils.createFormImage(
            config,
            config.guildSettingsIconUrl,
            config.guildSettingsIconPath
        )
        val leaderboardPage = invitationStatisticsService.getLeaderboardPage(guild.id, invitationPage, INVITERS_PER_PAGE)
        invitationPage = leaderboardPage.page

        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.statistics.title", "guild" to guild.name))
            .apply { statsIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.statistics.description"))
            .label(section(lang.bedrock("bedrock.statistics.header.overview")))
            .label(createOverviewSection())
            .label(section(lang.bedrock("menu.statistics.detail.kills.name")))
            .label(createKillSection())
            .label(section(lang.bedrock("menu.statistics.detail.wars.summary.name")))
            .label(createWarSection())
            .label(section(lang.bedrock("menu.statistics.detail.performance.name")))
            .label(createMemberPerformanceSection())
            .label(section(lang.bedrock("menu.statistics.detail.recent.name")))
            .label(createRecentActivitySection())
            .label(section(lang.bedrock("menu.statistics.detail.top_killers.name")))
            .label(createTopKillersSection())
            .label(section(lang.bedrock("menu.statistics.detail.top_contributors.name")))
            .label(createTopContributorsSection())
            .label(section(lang.bedrock("bedrock.statistics.header.invitations")))
            .label(createInvitationSection(leaderboardPage))

        if (leaderboardPage.totalPages > 1) {
            builder.dropdown(
                lang.bedrock("bedrock.statistics.invitations.page_selector"),
                (1..leaderboardPage.totalPages).map { page ->
                    lang.bedrock("bedrock.statistics.invitations.page_option", "page" to page)
                },
                leaderboardPage.page
            )
        }

        builder
            .label(section(lang.bedrock("menu.statistics.detail.kd.name")))
            .label(createKdSection())
            .label(section(lang.bedrock("menu.statistics.item.period.name")))
            .label(createPeriodicSection())
            .label(section(lang.bedrock("menu.statistics.item.rivalry.name")))
            .label(createRivalrySection())
            .label(section(lang.bedrock("menu.statistics.item.achievements.name")))
            .label(createAchievementsSection())
            .label(section(lang.bedrock("bedrock.statistics.header.economy")))
            .label(createEconomySection())

        return builder
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (!player.isOnline) return@Runnable
                    if (leaderboardPage.totalPages > 1) {
                        val selectedPage = response.asDropdown(INVITATION_PAGE_DROPDOWN_INDEX)
                        if (selectedPage != invitationPage) {
                            invitationPage = selectedPage
                            open()
                            return@Runnable
                        }
                    }
                    bedrockNavigator.goBack()
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()
    }

    private fun section(title: String): String =
        lang.bedrock("bedrock.statistics.header.format", "title" to title)

    private fun createOverviewSection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val online = members.count { player.server.getPlayer(it.playerId)?.isOnline == true }
        val created = DateTimeFormatter.ofPattern(lang.raw("bedrock.statistics.date_format.overview"))
            .format(guild.createdAt.atZone(ZoneId.systemDefault()))
        val progression = progressionRepository.getGuildProgression(guild.id)
        val experience = progression?.let {
            "${it.experienceThisLevel}/${it.experienceForNextLevel}"
        } ?: lang.bedrock("menu.statistics.common.unavailable")

        return lang.bedrock(
            "bedrock.statistics.overview",
            "total_members" to members.size,
            "online_members" to online,
            "created" to created,
            "level" to (progression?.currentLevel ?: guild.level),
            "experience" to experience
        )
    }

    private fun createKillSection(): String {
        val stats = runCatching { killService.getGuildKillStats(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        if (stats.totalKills == 0 && stats.totalDeaths == 0) {
            return lang.bedrock("menu.statistics.common.no_kill_data")
        }
        return listOf(
            lang.bedrock("menu.statistics.common.total_kills", "count" to stats.totalKills),
            lang.bedrock("menu.statistics.common.total_deaths", "count" to stats.totalDeaths),
            netKills(stats),
            lang.bedrock("menu.statistics.common.kd_ratio", "ratio" to format(stats.killDeathRatio))
        ).joinToString("\n")
    }

    private fun createWarSection(): String {
        val wars = runCatching { warService.getWarsForGuild(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val history = runCatching { warService.getWarHistory(guild.id, 100) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val wins = history.count { it.winner == guild.id }
        val losses = history.count { it.winner != null && it.winner != guild.id }
        val draws = history.count { it.winner == null }
        return listOf(
            lang.bedrock("menu.statistics.common.active_wars", "count" to wars.count { it.isActive }),
            lang.bedrock("menu.statistics.common.total_wars", "count" to history.size),
            lang.bedrock("menu.statistics.common.wins", "count" to wins),
            lang.bedrock("menu.statistics.common.losses", "count" to losses),
            lang.bedrock("menu.statistics.common.draws", "count" to draws)
        ).joinToString("\n")
    }

    private fun createMemberPerformanceSection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val online = members.count { player.server.getPlayer(it.playerId)?.isOnline == true }
        val stats = runCatching { killService.getGuildKillStats(guild.id) }.getOrNull()
            ?: return listOf(
                lang.bedrock("menu.statistics.common.total_members", "count" to members.size),
                lang.bedrock("menu.statistics.common.online", "count" to online),
                lang.bedrock("menu.statistics.common.unavailable")
            ).joinToString("\n")
        val avgKills = if (members.isEmpty()) 0.0 else stats.totalKills.toDouble() / members.size
        val avgDeaths = if (members.isEmpty()) 0.0 else stats.totalDeaths.toDouble() / members.size
        return listOf(
            lang.bedrock("menu.statistics.common.total_members", "count" to members.size),
            lang.bedrock("menu.statistics.common.online", "count" to online),
            lang.bedrock("menu.statistics.common.offline", "count" to members.size - online),
            lang.bedrock("menu.statistics.common.average_kills", "average" to format(avgKills)),
            lang.bedrock("menu.statistics.common.average_deaths", "average" to format(avgDeaths))
        ).joinToString("\n")
    }

    private fun createRecentActivitySection(): String {
        val kills = runCatching { killService.getRecentGuildKills(guild.id, 10) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        if (kills.isEmpty()) return lang.bedrock("menu.statistics.common.no_kill_data")
        val last = kills.maxByOrNull { it.timestamp } ?: return lang.bedrock("menu.statistics.common.no_kill_data")
        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.statistics.date_format.activity"))
        val whenText = formatter.format(last.timestamp.atZone(ZoneId.systemDefault()))
        return listOf(
            lang.bedrock("menu.statistics.item.recent.lore.kills", "count" to kills.size),
            whenText
        ).joinToString("\n")
    }

    private fun createTopKillersSection(): String {
        val memberIds = memberService.getGuildMembers(guild.id).map { it.playerId }
        val top = runCatching { killService.getTopKillers(memberIds, 5) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        if (top.isEmpty()) return lang.bedrock("menu.statistics.common.no_kill_data")
        return top.mapIndexed { index, (playerId, stats) ->
            rankedKills(
                index + 1,
                player.server.getOfflinePlayer(playerId).name ?: playerId.toString().take(8),
                stats.totalKills
            )
        }.joinToString("\n")
    }

    private fun createTopContributorsSection(): String {
        val top = runCatching { bankService.getMemberContributions(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val contributors = top.filter { it.netContribution > 0 }
            .sortedByDescending { it.netContribution }
            .take(5)
        if (contributors.isEmpty()) {
            return lang.bedrock("menu.statistics.common.no_contribution_data")
        }
        return contributors.mapIndexed { index, contribution ->
            rankedContribution(
                index + 1,
                contribution.playerName ?: contribution.playerId.toString().take(8),
                contribution.netContribution
            )
        }.joinToString("\n")
    }

    private fun createInvitationSection(page: InvitationLeaderboardPage): String {
        val total = runCatching { invitationStatisticsService.getTotalInvitations(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val rendered = if (page.entries.isEmpty()) {
            lang.bedrock("bedrock.statistics.invitations.none")
        } else {
            val rankOffset = page.page * page.pageSize
            page.entries.mapIndexed { index, entry ->
                lang.bedrock(
                    "bedrock.statistics.invitations.entry",
                    "rank" to rankOffset + index + 1,
                    "player" to (
                        player.server.getOfflinePlayer(entry.inviterPlayerId).name
                            ?: entry.inviterPlayerId.toString().take(8)
                        ),
                    "count" to entry.inviteCount
                )
            }.joinToString("\n")
        }
        return lang.bedrock(
            "bedrock.statistics.invitations.content",
            "total" to total,
            "page" to page.page + 1,
            "pages" to page.totalPages,
            "leaderboard" to rendered
        )
    }

    private fun createKdSection(): String {
        val stats = runCatching { killService.getGuildKillStats(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        if (stats.totalKills == 0 && stats.totalDeaths == 0) {
            return lang.bedrock("menu.statistics.common.no_kill_data")
        }
        return listOf(
            lang.bedrock("menu.statistics.common.kill_death_ratio", "ratio" to format(stats.killDeathRatio)),
            netKills(stats)
        ).joinToString("\n")
    }

    private fun createPeriodicSection(): String {
        val periods = listOf(
            "daily" to LeaderboardPeriod.DAILY,
            "weekly" to LeaderboardPeriod.WEEKLY,
            "monthly" to LeaderboardPeriod.MONTHLY,
            "all_time" to LeaderboardPeriod.ALL_TIME
        )
        return periods.joinToString("\n\n") { (key, period) ->
            val name = lang.bedrock("menu.statistics.period.$key")
            val entry = runCatching {
                leaderboardService.getEntityEntry(LeaderboardType.KILLS, guild.id, period)
            }.getOrNull()
            if (entry == null) {
                "$name\n${lang.bedrock("menu.statistics.common.no_period_data")}"
            } else {
                val lines = mutableListOf(
                    name,
                    lang.bedrock("menu.statistics.common.total_kills", "count" to entry.value.toInt())
                )
                if (entry.rank > 0) {
                    lines += lang.bedrock("menu.statistics.common.ranking", "rank" to entry.rank)
                }
                lines.joinToString("\n")
            }
        }
    }

    private fun createRivalrySection(): String {
        val history = runCatching { warService.getWarHistory(guild.id, 100) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val rivals = history.map {
            if (it.declaringGuildId == guild.id) it.defendingGuildId else it.declaringGuildId
        }.distinct().take(5)
        if (rivals.isEmpty()) return lang.bedrock("menu.statistics.common.no_rivalry_data")

        return rivals.joinToString("\n\n") { rivalId ->
            val rivalName = guildService.getGuild(rivalId)?.name
                ?: lang.bedrock("menu.statistics.common.unknown_guild")
            val wars = history.filter {
                (it.declaringGuildId == guild.id && it.defendingGuildId == rivalId) ||
                    (it.declaringGuildId == rivalId && it.defendingGuildId == guild.id)
            }
            val kills = runCatching {
                killService.getKillsBetweenGuilds(guild.id, rivalId, 100)
            }.getOrNull()
            val lines = mutableListOf(
                lang.bedrock("menu.statistics.detail.rivalry.enemy", "guild" to rivalName),
                lang.bedrock("menu.statistics.common.wars_fought", "count" to wars.size),
                lang.bedrock("menu.statistics.common.wins", "count" to wars.count { it.winner == guild.id }),
                lang.bedrock("menu.statistics.common.losses", "count" to wars.count { it.winner == rivalId }),
                lang.bedrock("menu.statistics.common.draws", "count" to wars.count { it.winner == null })
            )
            if (kills == null) {
                lines += lang.bedrock("menu.statistics.common.unavailable")
            } else {
                val ours = kills.count { it.killerGuildId == guild.id }
                val theirs = kills.count { it.killerGuildId == rivalId }
                val ratio = if (theirs > 0) ours.toDouble() / theirs else ours.toDouble()
                lines += lang.bedrock("menu.statistics.common.kdr_against", "ratio" to format(ratio))
            }
            lines.joinToString("\n")
        }
    }

    private fun createAchievementsSection(): String {
        val kills = runCatching { killService.getGuildKillStats(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val wars = runCatching { warService.getWarHistory(guild.id, 100) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val wins = wars.count { it.winner == guild.id }

        return listOf(
            achievementLabel("first_blood", kills.totalKills >= 1),
            achievementLabel("centurion", kills.totalKills >= 100),
            achievementLabel("warlord", kills.totalKills >= 1000),
            achievementLabel("survivor", wars.size >= 10),
            achievementLabel("champion", wins >= 5),
            achievementLabel("veteran", wars.size >= 25),
            achievementLabel("net_positive", kills.netKills > 0),
            achievementLabel(
                "well_rounded",
                kills.totalKills > 0 && kills.totalDeaths > 0 &&
                    kills.killDeathRatio > 0.0 && wars.isNotEmpty()
            )
        ).joinToString("\n")
    }

    private fun achievementLabel(key: String, unlocked: Boolean): String = when (key) {
        "first_blood" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.first_blood.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.first_blood.locked")
        }
        "centurion" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.centurion.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.centurion.locked")
        }
        "warlord" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.warlord.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.warlord.locked")
        }
        "survivor" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.survivor.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.survivor.locked")
        }
        "champion" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.champion.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.champion.locked")
        }
        "veteran" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.veteran.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.veteran.locked")
        }
        "net_positive" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.net_positive.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.net_positive.locked")
        }
        "well_rounded" -> if (unlocked) {
            lang.bedrock("menu.statistics.detail.achievements.well_rounded.unlocked")
        } else {
            lang.bedrock("menu.statistics.detail.achievements.well_rounded.locked")
        }
        else -> lang.bedrock("menu.statistics.common.unavailable")
    }

    private fun createEconomySection(): String {
        val stats = runCatching { bankService.getBankStats(guild.id) }.getOrNull()
            ?: return lang.bedrock("menu.statistics.common.unavailable")
        val average = if (stats.totalTransactions > 0) {
            stats.transactionVolume.toDouble() / stats.totalTransactions
        } else {
            0.0
        }
        return lang.bedrock(
            "bedrock.statistics.economy",
            "balance" to stats.currentBalance,
            "transactions" to stats.totalTransactions,
            "average" to format(average)
        )
    }

    private fun rankedKills(rank: Int, name: String, kills: Int): String = when (rank) {
        1 -> lang.bedrock("menu.statistics.common.ranked_kills.first", "rank" to rank, "player" to name, "kills" to kills)
        2 -> lang.bedrock("menu.statistics.common.ranked_kills.second", "rank" to rank, "player" to name, "kills" to kills)
        3 -> lang.bedrock("menu.statistics.common.ranked_kills.third", "rank" to rank, "player" to name, "kills" to kills)
        else -> lang.bedrock("menu.statistics.common.ranked_kills.other", "rank" to rank, "player" to name, "kills" to kills)
    }

    private fun rankedContribution(rank: Int, name: String, amount: Int): String = when (rank) {
        1 -> lang.bedrock("menu.statistics.common.ranked_contribution.first", "rank" to rank, "player" to name, "amount" to amount)
        2 -> lang.bedrock("menu.statistics.common.ranked_contribution.second", "rank" to rank, "player" to name, "amount" to amount)
        3 -> lang.bedrock("menu.statistics.common.ranked_contribution.third", "rank" to rank, "player" to name, "amount" to amount)
        else -> lang.bedrock("menu.statistics.common.ranked_contribution.other", "rank" to rank, "player" to name, "amount" to amount)
    }

    private fun rankedInvitations(rank: Int, name: String, count: Int): String = when (rank) {
        1 -> lang.bedrock("menu.statistics.common.ranked_invitations.first", "rank" to rank, "player" to name, "count" to count)
        2 -> lang.bedrock("menu.statistics.common.ranked_invitations.second", "rank" to rank, "player" to name, "count" to count)
        3 -> lang.bedrock("menu.statistics.common.ranked_invitations.third", "rank" to rank, "player" to name, "count" to count)
        else -> lang.bedrock("menu.statistics.common.ranked_invitations.other", "rank" to rank, "player" to name, "count" to count)
    }
    private fun netKills(stats: GuildKillStats): String =
        if (stats.netKills >= 0) {
            lang.bedrock("menu.statistics.common.net_kills.positive", "count" to stats.netKills)
        } else {
            lang.bedrock("menu.statistics.common.net_kills.negative", "count" to stats.netKills)
        }

    private fun format(value: Double): String = String.format("%.2f", value)

    override fun shouldCacheForm(): Boolean = false

    override fun handleResponse(player: Player, response: Any?) {
        onFormResponseReceived()
    }
}
