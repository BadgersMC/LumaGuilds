package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
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

/** Bedrock/Cumulus presentation of the shared weekly guild quest set. */
class BedrockGuildQuestsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val memberService: MemberService by inject()
    private val questService: QuestService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    private var page = 0

    override fun getForm(): Form {
        if (memberService.getMember(player.uniqueId, guild.id) == null) {
            return statusForm(lang.bedrock("menu.quests.feedback.not_member"))
        }
        val active = questService.activeQuestSet()
            ?: return statusForm(lang.bedrock("menu.quests.feedback.no_quests"))

        val progress = questService.guildProgress(guild.id).associateBy(GuildQuestProgress::questId)
        val total = active.quests.count { it.targetCount > 0 }
        val claimed = progress.values.count { it.claimed }
        val pageCount = maxOf(1, (active.quests.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pageCount - 1)

        val content = listOf(
            lang.bedrock("menu.quests.item.header.claimed", "claimed" to claimed, "total" to total),
            lang.bedrock(
                "menu.quests.item.header.reset",
                "time" to QuestDisplayFormatter.duration(questService.timeRemaining())
            ),
            lang.bedrock(
                "menu.quests.item.header.bonus",
                "xp" to questService.fullSetBonusExperience,
                "status" to if (questService.isWeeklyBonusAwarded(guild.id)) "✓" else "…"
            ),
            lang.bedrock("bedrock.quests.page", "page" to page + 1, "pages" to pageCount)
        ).joinToString("\n")

        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.quests.title", "guild" to guild.name))
            .content(content)
        val actions = mutableListOf<() -> Unit>()

        active.quests
            .drop(page * PAGE_SIZE)
            .take(PAGE_SIZE)
            .forEach { quest ->
                builder = builder.button(questButton(quest, progress[quest.id]))
                actions += { handleQuestSelection(quest.id) }
            }

        if (page > 0) {
            builder = builder.button(lang.bedrock("menu.quests.item.prev_page.name"))
            actions += { changePage(-1) }
        }
        if (page + 1 < pageCount) {
            builder = builder.button(lang.bedrock("menu.quests.item.next_page.name"))
            actions += { changePage(1) }
        }
        builder = builder.button(lang.bedrock("menu.quests.item.back.name"))
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

    private fun questButton(quest: QuestDefinition, progress: GuildQuestProgress?): String {
        val count = progress?.currentCount ?: 0
        val percent = if (quest.targetCount > 0) {
            ((count.coerceAtMost(quest.targetCount) * 100) / quest.targetCount).toInt()
        } else {
            0
        }
        val lines = mutableListOf(
            lang.bedrock(
                "menu.quests.item.quest.name",
                "objective" to QuestDisplayFormatter.name(quest)
            ),
            tierLabel(quest.tier),
            lang.bedrock(
                "menu.quests.item.quest.description",
                "objective" to QuestDisplayFormatter.description(quest)
            ),
            lang.bedrock(
                "menu.quests.item.quest.progress",
                "count" to count,
                "target" to quest.targetCount,
                "percent" to percent
            ),
            lang.bedrock("menu.quests.item.quest.reward", "xp" to quest.experienceReward)
        )
        if (quest.itemRewards.isNotEmpty()) {
            val items = quest.itemRewards.joinToString(", ") { reward ->
                "${reward.amount}× ${QuestDisplayFormatter.target(reward.itemId)}"
            }
            lines += lang.bedrock("bedrock.quests.item_rewards", "items" to items)
        }
        val rank = questService.rankFor(guild.id, quest.id)?.let { "#$it" } ?: "—"
        lines += lang.bedrock("menu.quests.item.quest.rank", "rank" to rank)
        lines += when {
            progress?.claimed == true -> lang.bedrock("menu.quests.item.quest.claimed")
            progress?.completedAt != null -> lang.bedrock("menu.quests.item.quest.completed")
            else -> lang.bedrock("menu.quests.item.quest.in_progress")
        }
        lines += lang.bedrock("menu.quests.item.quest.view_leaderboard")
        return lines.joinToString("\n")
    }

    private fun handleQuestSelection(questId: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            val questExists = questService.activeQuestSet()?.quests?.any { it.id == questId } == true
            if (!questExists) {
                player.sendMessage(lang.msg("menu.quests.feedback.no_quests"))
                open()
                return@Runnable
            }
            bedrockNavigator.openMenu(
                BedrockGuildQuestLeaderboardMenu(
                    menuNavigator = menuNavigator,
                    player = player,
                    viewingGuild = guild,
                    questId = questId,
                    logger = logger,
                )
            )
        })
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
            .title(lang.bedrock("menu.quests.title", "guild" to guild.name))
            .content(message)
            .button(lang.bedrock("menu.quests.item.back.name"))
            .validResultHandler {
                onFormResponseReceived()
                goBackOnServerThread()
            }
            .closedOrInvalidResultHandler { _, _ ->
                onFormResponseReceived()
                goBackOnServerThread()
            }
            .build()

    private fun tierLabel(tier: QuestRewardTier): String = when (tier) {
        QuestRewardTier.COMMON -> lang.bedrock("menu.quests.tier.common")
        QuestRewardTier.CHALLENGING -> lang.bedrock("menu.quests.tier.challenging")
        QuestRewardTier.HEADLINE -> lang.bedrock("menu.quests.tier.headline")
        QuestRewardTier.CONDITIONED -> lang.bedrock("menu.quests.tier.conditioned")
    }

    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private const val PAGE_SIZE = 6
    }
}
