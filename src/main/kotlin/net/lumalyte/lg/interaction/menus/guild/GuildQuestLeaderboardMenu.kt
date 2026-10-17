package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.QuestDisplayFormatter
import net.lumalyte.lg.utils.QuestIconProvider
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GuildQuestLeaderboardMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val viewingGuild: Guild,
    private var questId: String,
    private val memberService: MemberService,
    private val guildService: GuildService,
    private val questService: QuestService,
    private val lang: LangService,
) : Menu {
    private var page = 0
    private var nativePage = 0
    private val nativeDialog = GuildMenuDialog()

    override fun open() {
        if (memberService.getMember(player.uniqueId, viewingGuild.id) == null) {
            player.sendMessage(lang.msg("menu.quests.feedback.not_member"))
            menuNavigator.goBack()
            return
        }
        val active = questService.activeQuestSet()
        val quest = active?.quests?.firstOrNull { it.id == questId }
        if (quest == null) {
            player.sendMessage(lang.msg("menu.quests.feedback.no_quests"))
            menuNavigator.goBack()
            return
        }

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
        val pageCount = maxOf(1, (orderedGuilds.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
        page = page.coerceIn(0, pageCount - 1)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.build(
                viewingGuild.guiTheme,
                6,
                lang.guiTitle("menu.quests.leaderboard.title"),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)

        val ownProgress = progressByGuild[viewingGuild.id]?.currentCount ?: 0
        val ownRank = rankByGuild[viewingGuild.id]?.let { "#$it" } ?: "—"
        val header = QuestIconProvider.itemFor(quest).also { item ->
            item.editMeta { meta ->
                meta.displayName(
                    lang.gui(
                        "menu.quests.leaderboard.header.name",
                        "objective" to QuestDisplayFormatter.name(quest),
                    )
                )
                meta.lore(
                    listOf(
                        lang.gui(
                            "menu.quests.leaderboard.header.target",
                            "target" to quest.targetCount,
                        ),
                        lang.gui(
                            "menu.quests.leaderboard.header.your_score",
                            "points" to ownProgress,
                            "rank" to ownRank,
                        ),
                        lang.gui(
                            "menu.quests.leaderboard.header.winner_reward",
                            "xp" to questService.leaderboardWinnerReward(quest),
                        ),
                        lang.gui(
                            "menu.quests.leaderboard.header.reset",
                            "time" to QuestDisplayFormatter.duration(questService.timeRemaining()),
                        ),
                    )
                )
            }
        }
        pane.addItem(GuiItem(header), 4, 0)
        if (
            RecruitmentPresentation.supportsDialog(player) &&
            showNativeLeaderboard(header, orderedGuilds, progressByGuild, rankByGuild)
        ) {
            return
        }

        orderedGuilds
            .drop(page * ITEMS_PER_PAGE)
            .take(ITEMS_PER_PAGE)
            .forEachIndexed { index, guild ->
                val x = index % 9
                val y = 1 + index / 9
                val score = progressByGuild[guild.id]?.currentCount ?: 0
                val rank = rankByGuild[guild.id]?.let { "#$it" } ?: "—"
                val banner = guild.banner
                    ?.deserializeToItemStack()
                    ?.clone()
                    ?: ItemStack.of(Material.WHITE_BANNER)
                banner.editMeta { meta ->
                    meta.displayName(
                        if (guild.id == viewingGuild.id) {
                            lang.gui(
                                "menu.quests.leaderboard.guild.you",
                                "guild" to guild.name,
                                "rank" to rank,
                            )
                        } else {
                            lang.gui(
                                "menu.quests.leaderboard.guild.name",
                                "guild" to guild.name,
                                "rank" to rank,
                            )
                        }
                    )
                    val completed = if (score >= quest.targetCount) {
                        lang.gui("menu.quests.leaderboard.guild.completed")
                    } else {
                        lang.gui("menu.quests.leaderboard.guild.in_progress")
                    }
                    meta.lore(
                        listOf(
                            lang.gui(
                                "menu.quests.leaderboard.guild.score",
                                "points" to score,
                            ),
                            lang.gui(
                                "menu.quests.leaderboard.guild.target",
                                "target" to quest.targetCount,
                            ),
                            completed,
                        )
                    )
                }
                pane.addItem(GuiItem(banner), x, y)
            }
        val back = NexoItemProvider.getItemStackOrFallback("lg_back") {
            ItemStack.of(Material.ARROW)
        }.also { item ->
            item.editMeta { meta ->
                meta.displayName(lang.gui("menu.quests.leaderboard.back"))
            }
        }
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 4, 5)

        if (page > 0) {
            val previous = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
                ItemStack.of(Material.ARROW)
            }.also { item ->
                item.editMeta { meta ->
                    meta.displayName(lang.gui("menu.quests.item.prev_page.name"))
                }
            }
            pane.addItem(GuiItem(previous) { page--; open() }, 0, 5)
        }
        if (page + 1 < pageCount) {
            val next = NexoItemProvider.getItemStackOrFallback("lg_page_next") {
                ItemStack.of(Material.ARROW)
            }.also { item ->
                item.editMeta { meta ->
                    meta.displayName(lang.gui("menu.quests.item.next_page.name"))
                }
            }
            pane.addItem(GuiItem(next) { page++; open() }, 8, 5)
        }
        val pageItem = ItemStack.of(Material.PAPER).also { item ->
            item.editMeta { meta ->
                meta.displayName(
                    lang.gui(
                        "menu.quests.leaderboard.page",
                        "page" to page + 1,
                        "pages" to pageCount,
                    )
                )
            }
        }
        pane.addItem(GuiItem(pageItem), 2, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun showNativeLeaderboard(
        header: ItemStack,
        guilds: List<Guild>,
        progress: Map<java.util.UUID, GuildQuestProgress>,
        ranks: Map<java.util.UUID, Int>,
    ): Boolean {
        val pages = maxOf(1, (guilds.size + NATIVE_PAGE_SIZE - 1) / NATIVE_PAGE_SIZE)
        nativePage = nativePage.coerceIn(0, pages - 1)
        val actions = nativeLeaderboardActions(pages)
        val token = menuNavigator.currentNavigationToken()
        val active = questService.activeQuestSet()
        return nativeDialog.show(
            player,
            GuildDialogPage(
                lang.gui("menu.quests.leaderboard.title"),
                nativeLeaderboardItems(guilds, progress, ranks),
                actions,
                nativeAction(lang.gui("menu.quests.leaderboard.back")) { menuNavigator.goBack() },
                theme = viewingGuild.guiTheme,
                columns = 2,
                framedHeading = true,
                subtitle = (header.itemMeta?.displayName() ?: Component.empty()).append(Component.text(" · "))
                    .append(lang.gui("menu.quests.leaderboard.page", "page" to nativePage + 1, "pages" to pages)),
                bodyActions = active?.quests?.map { quest ->
                    referenceAction(Component.text((if (quest.id == questId) "[" else "") + QuestDisplayFormatter.name(quest) + (if (quest.id == questId) "]" else "")), "lg_nav_quests") {
                        questId = quest.id; nativePage = 0; open()
                    }
                } ?: emptyList(),
            ),
            {
                player.isOnline && menuNavigator.isNavigationCurrent(token) &&
                    memberService.getMember(player.uniqueId, viewingGuild.id) != null &&
                    questService.activeQuestSet()?.weekId == active?.weekId && player.hasPermission("lumaguilds.guild.quests")
            },
        ) { player.sendMessage(lang.msg("menu.quests.feedback.no_quests")) }
    }

    private fun nativeLeaderboardItems(
        guilds: List<Guild>,
        progress: Map<java.util.UUID, GuildQuestProgress>,
        ranks: Map<java.util.UUID, Int>,
    ): List<GuildDialogItem> {
        val items = mutableListOf<GuildDialogItem>()
        for (current in guilds.drop(nativePage * NATIVE_PAGE_SIZE).take(NATIVE_PAGE_SIZE)) {
            items += nativeGuildItem(current, progress[current.id]?.currentCount ?: 0, ranks[current.id])
        }
        return items
    }

    private fun nativeGuildItem(current: Guild, score: Long, rank: Int?): GuildDialogItem {
        val lines = listOf(
            Component.text("${rank?.let { "#$it" } ?: "—"} ${current.name}${if (current.id == viewingGuild.id) " (you)" else ""}"),
            questProgressBar(score, questService.activeQuestSet()?.quests?.firstOrNull { it.id == questId }?.targetCount ?: 0),
        )
        return GuildDialogItem(
            GuildBannerItemResolver.resolveForDisplay(current),
            Component.join(JoinConfiguration.newlines(), lines),
            cardLines = lines,
            rightLines = listOf(Component.text(score.toString()), Component.text("/ " +
                (questService.activeQuestSet()?.quests?.firstOrNull { it.id == questId }?.targetCount ?: 0))),
        )
    }

    private fun nativeAction(label: Component, callback: () -> Unit) =
        GuildDialogAction(label, Component.empty(), callback)

    private fun nativeLeaderboardActions(pages: Int): List<GuildDialogAction> {
        return buildList {
            if (nativePage > 0) add(nativeAction(lang.gui("recruitment.previous"), ::previousNativePage))
            if (nativePage + 1 < pages) add(nativeAction(lang.gui("recruitment.next"), ::nextNativePage))
        }
    }

    private fun previousNativePage() {
        nativePage--
        open()
    }

    private fun nextNativePage() {
        nativePage++
        open()
    }

    companion object {
        private const val NATIVE_PAGE_SIZE = 4
        private const val ITEMS_PER_PAGE = 36
    }
}
