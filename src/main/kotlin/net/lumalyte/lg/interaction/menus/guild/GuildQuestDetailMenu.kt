package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.QuestDisplayFormatter

/** Read-only detail: the existing automatic settlement is the only reward path. */
internal class GuildQuestDetailMenu(
    private val navigator: MenuNavigator,
    private val player: org.bukkit.entity.Player,
    private val guild: Guild,
    private val questId: String,
    private val members: MemberService,
    private val guilds: GuildService,
    private val quests: QuestService,
    private val lang: LangService,
) : Menu {
    private val dialog = GuildMenuDialog()

    override fun open() {
        val week = quests.activeQuestSet()
        val quest = week?.quests?.firstOrNull { it.id == questId }
        if (quest == null || members.getMember(player.uniqueId, guild.id) == null ||
            !player.hasPermission("lumaguilds.guild.quests")
        ) {
            navigator.goBack()
            return
        }
        val progress = quests.progressFor(guild.id, questId)
        val count = progress.currentCount
        val rank = quests.rankFor(guild.id, questId)?.let { "#$it" } ?: "—"
        val top =
            quests.leaderboardFor(questId, 3).mapIndexed { index, value ->
                Component.text(
                    "${index + 1}. ${guilds.getGuild(value.guildId)?.name ?: "—"} · ${value.currentCount}",
                )
            }
        val title = Component.text(QuestDisplayFormatter.name(quest))
        val token = navigator.currentNavigationToken()
        val page =
            GuildDialogPage(
                title,
                listOf(
                    referenceItem(
                        "lg_nav_quests",
                        listOf(
                            questProgressBar(count, quest.targetCount),
                            Component.text("$count / ${quest.targetCount} · $rank"),
                        ),
                    ),
                    referenceItem(
                        "lg_reward",
                        listOf(
                            lang.gui("dialog_v2.winner_reward", "xp" to quests.leaderboardWinnerReward(quest)),
                            lang.gui("dialog_v2.automatic_rewards"),
                            lang.gui(
                                if (progress.claimed) {
                                    "menu.quests.item.quest.claimed"
                                } else if (progress.completedAt != null) {
                                    "menu.quests.item.quest.completed"
                                } else {
                                    "menu.quests.item.quest.in_progress"
                                },
                            ),
                        ),
                    ),
                    referenceItem(
                        "lg_created",
                        listOf(
                            lang.gui(
                                "dialog_v2.resets",
                                "time" to QuestDisplayFormatter.duration(quests.timeRemaining()),
                            ),
                        ),
                    ),
                    referenceItem("lg_sort", listOf(lang.gui("dialog_v2.top_three")) + top),
                ),
                listOf(
                    referenceAction(lang.gui("dialog_v2.full_rankings"), "lg_sort") {
                        navigator.openMenu(
                            GuildQuestLeaderboardMenu(navigator, player, guild, questId, members, guilds, quests, lang),
                        )
                    },
                ),
                referenceAction(lang.gui("dialog_v2.other_quests"), "lg_back") { navigator.goBack() },
                theme = guild.guiTheme,
                subtitle = Component.text(QuestDisplayFormatter.description(quest)),
            )
        dialog.show(player, page, {
            player.isOnline && navigator.isNavigationCurrent(token) &&
                members.getMember(player.uniqueId, guild.id) != null &&
                player.hasPermission("lumaguilds.guild.quests") && quests.activeQuestSet()?.weekId == week.weekId
        }) { player.sendMessage(lang.msg("menu.quests.feedback.no_quests")) }
    }
}
