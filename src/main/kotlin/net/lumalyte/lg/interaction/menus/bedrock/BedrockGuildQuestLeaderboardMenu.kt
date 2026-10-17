package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.QuestDisplayFormatter
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

class BedrockGuildQuestLeaderboardMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val viewingGuild: Guild,
    private val questId: String,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val memberService: MemberService by inject()
    private val guildService: GuildService by inject()
    private val questService: QuestService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private var page = 0

    override fun getForm(): Form {
        if (memberService.getMember(player.uniqueId, viewingGuild.id) == null) {
            return statusForm(lang.bedrock("menu.quests.feedback.not_member"))
        }
        val active = questService.activeQuestSet()
        val quest = active?.quests?.firstOrNull { it.id == questId }
            ?: return statusForm(lang.bedrock("menu.quests.feedback.no_quests"))

        val rankedProgress = questService.leaderboardFor(questId)
        val progressByGuild = rankedProgress.associateBy(GuildQuestProgress::guildId)
        val allGuilds = guildService.getAllGuilds()
        val guildById = allGuilds.associateBy(Guild::id)
        val rankedGuilds = rankedProgress.mapNotNull { guildById[it.guildId] }
        val rankedIds = rankedGuilds.mapTo(mutableSetOf()) { it.id }
        val zeroGuilds = allGuilds.filterNot { it.id in rankedIds }
            .sortedBy { it.name.lowercase() }
        val orderedGuilds = rankedGuilds + zeroGuilds
        val rankByGuild = rankedProgress.mapIndexed { index, progress ->
            progress.guildId to (index + 1)
        }.toMap()

        val pageCount = maxOf(1, (orderedGuilds.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pageCount - 1)
        val ownScore = progressByGuild[viewingGuild.id]?.currentCount ?: 0
        val ownRank = rankByGuild[viewingGuild.id]?.let { "#$it" } ?: "—"
        val rows = orderedGuilds
            .drop(page * PAGE_SIZE)
            .take(PAGE_SIZE)
            .map { guild ->
                if (guild.id == viewingGuild.id) {
                    lang.bedrock(
                        "menu.quests.leaderboard.bedrock.you",
                        "guild" to guild.name,
                        "points" to (progressByGuild[guild.id]?.currentCount ?: 0),
                        "rank" to (rankByGuild[guild.id]?.let { "#$it" } ?: "—"),
                    )
                } else {
                    lang.bedrock(
                        "menu.quests.leaderboard.bedrock.row",
                        "guild" to guild.name,
                        "points" to (progressByGuild[guild.id]?.currentCount ?: 0),
                        "rank" to (rankByGuild[guild.id]?.let { "#$it" } ?: "—"),
                    )
                }
            }

        val content = buildList {
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.bedrock.objective",
                    "objective" to QuestDisplayFormatter.name(quest),
                )
            )
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.header.target",
                    "target" to quest.targetCount,
                )
            )
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.header.your_score",
                    "points" to ownScore,
                    "rank" to ownRank,
                )
            )
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.header.winner_reward",
                    "xp" to questService.leaderboardWinnerReward(quest),
                )
            )
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.header.reset",
                    "time" to QuestDisplayFormatter.duration(questService.timeRemaining()),
                )
            )
            add("")
            addAll(rows)
            add("")
            add(
                lang.bedrock(
                    "menu.quests.leaderboard.page",
                    "page" to page + 1,
                    "pages" to pageCount,
                )
            )
        }.joinToString("\n")

        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.quests.leaderboard.title"))
            .content(content)
        val actions = mutableListOf<() -> Unit>()
        if (page > 0) {
            builder = builder.button(lang.bedrock("menu.quests.item.prev_page.name"))
            actions += { changePage(-1) }
        }
        if (page + 1 < pageCount) {
            builder = builder.button(lang.bedrock("menu.quests.item.next_page.name"))
            actions += { changePage(1) }
        }
        builder = builder.button(lang.bedrock("menu.quests.leaderboard.back"))
        actions += { goBackOnServerThread() }

        return builder
            .validResultHandler { response ->
                onFormResponseReceived()
                actions.getOrNull(response.clickedButtonId())?.invoke()
            }
            .closedOrInvalidResultHandler { _, _ ->
                onFormResponseReceived()
                goBackOnServerThread()
            }
            .build()
    }

    private fun changePage(delta: Int) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            page = (page + delta).coerceAtLeast(0)
            open()
        })
    }

    private fun goBackOnServerThread() {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) bedrockNavigator.goBack()
        })
    }
    private fun statusForm(message: String): Form =
        SimpleForm.builder()
            .title(lang.bedrock("menu.quests.leaderboard.title"))
            .content(message)
            .button(lang.bedrock("menu.quests.leaderboard.back"))
            .validResultHandler {
                onFormResponseReceived()
                goBackOnServerThread()
            }
            .closedOrInvalidResultHandler { _, _ ->
                onFormResponseReceived()
                goBackOnServerThread()
            }
            .build()

    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private const val PAGE_SIZE = 15
    }
}
