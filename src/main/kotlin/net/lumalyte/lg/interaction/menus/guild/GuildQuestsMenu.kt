package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.QuestDisplayFormatter
import net.lumalyte.lg.utils.QuestIconProvider
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GuildQuestsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val memberService: MemberService,
    private val guildService: GuildService,
    private val questService: QuestService,
    private val lang: LangService
) : Menu {
    private var page = 0
    private var nativePage = 0
    private val nativeDialog = GuildMenuDialog()
    private val slots = listOf(20, 22, 24, 29, 31, 33)

    override fun open() {
        if (memberService.getMember(player.uniqueId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.quests.feedback.not_member"))
            menuNavigator.goBack()
            return
        }
        val active = questService.activeQuestSet()
        if (active == null) {
            player.sendMessage(lang.msg("menu.quests.feedback.no_quests"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.quests.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.addPane(pane)

        val progress = questService.guildProgress(guild.id).associateBy { it.questId }
        val claimed = progress.values.count { it.claimed }
        val header = NexoItemProvider.getItemStackOrFallback("lg_nav_quests") { ItemStack.of(Material.CLOCK) }.also { item -> item.editMeta { meta ->
            meta.displayName(lang.gui("menu.quests.item.header.name"))
            meta.lore(listOf(
                lang.gui("menu.quests.item.header.claimed", "claimed" to claimed, "total" to active.quests.count { it.targetCount > 0 }),
                lang.gui("menu.quests.item.header.reset", "time" to QuestDisplayFormatter.duration(questService.timeRemaining())),
                lang.gui("menu.quests.item.header.bonus", "xp" to questService.fullSetBonusExperience,
                    "status" to if (questService.isWeeklyBonusAwarded(guild.id)) "✓" else "…")
            ))
        }}
        if (RecruitmentPresentation.supportsDialog(player) && showNativeQuests(active, header, progress)) return
        pane.addItem(GuiItem(header), 4, 0)

        val pageQuests = active.quests.drop(page * slots.size).take(slots.size)
        pageQuests.forEachIndexed { index, quest ->
            val slot = slots[index]
            pane.addItem(GuiItem(questItem(quest, progress[quest.id])) {
                menuNavigator.openMenu(
                    GuildQuestLeaderboardMenu(
                        menuNavigator = menuNavigator,
                        player = player,
                        viewingGuild = guild,
                        questId = quest.id,
                        memberService = memberService,
                        guildService = guildService,
                        questService = questService,
                        lang = lang,
                    )
                )
            }, slot % 9, slot / 9)
        }

        val back = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.back.name")) } }
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 4, 5)
        if (page > 0) {
            val previous = NexoItemProvider.getItemStackOrFallback("lg_page_prev") { ItemStack.of(Material.ARROW) }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.prev_page.name")) } }
            pane.addItem(GuiItem(previous) { page--; open() }, 0, 5)
        }
        if ((page + 1) * slots.size < active.quests.size) {
            val next = NexoItemProvider.getItemStackOrFallback("lg_page_next") { ItemStack.of(Material.ARROW) }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.next_page.name")) } }
            pane.addItem(GuiItem(next) { page++; open() }, 8, 5)
        }
        gui.show(player)
    }

    private fun openLeaderboard(quest: QuestDefinition) {
        menuNavigator.openMenu(
            GuildQuestLeaderboardMenu(menuNavigator, player, guild, quest.id, memberService, guildService, questService, lang),
        )
    }

    private fun nativeAction(label: Component, action: () -> Unit): GuildDialogAction =
        GuildDialogAction(label, Component.empty(), action)

    private fun showNativeQuests(
        active: net.lumalyte.lg.domain.entities.WeeklyQuestSet,
        header: ItemStack,
        progress: Map<String, net.lumalyte.lg.domain.entities.GuildQuestProgress>,
    ): Boolean {
        val pages = maxOf(1, (active.quests.size + NATIVE_PAGE_SIZE - 1) / NATIVE_PAGE_SIZE)
        nativePage = nativePage.coerceIn(0, pages - 1)
        val quests = active.quests.drop(nativePage * NATIVE_PAGE_SIZE).take(NATIVE_PAGE_SIZE).map { quest ->
            val item = questItem(quest, progress[quest.id])
            item to GuildDialogAction(
                lang.gui("menu.quests.item.quest.name", "objective" to QuestDisplayFormatter.name(quest)),
                Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), item.lore() ?: emptyList()),
            ) { openLeaderboard(quest) }
        }
        val controls = buildList {
            add(nativeAction(lang.gui("guild_dialogs.quests.rankings")) { showNativeRankings(active) })
            if (nativePage > 0) add(nativeAction(lang.gui("recruitment.previous")) { nativePage--; open() })
            if (nativePage + 1 < pages) add(nativeAction(lang.gui("recruitment.next")) { nativePage++; open() })
        }
        val title = lang.gui("menu.quests.title", "guild" to guild.name)
            .append(Component.text(" ${nativePage + 1}/$pages"))
        return presentNative(
            guildQuestDialogPage(
                title, header, quests, controls,
                nativeAction(lang.gui("menu.quests.item.back.name")) { menuNavigator.goBack() }, guild.guiTheme,
            ),
            active.weekId,
        )
    }

    private fun showNativeRankings(active: net.lumalyte.lg.domain.entities.WeeklyQuestSet) {
        val description = GuildDialogItem(
            NexoItemProvider.getItemStackOrFallback("lg_nav_quests") { ItemStack.of(Material.BOOK) },
            lang.gui("guild_dialogs.quests.choose_ranking"),
        )
        val actions = active.quests.map { quest ->
            nativeAction(lang.gui("menu.quests.item.quest.name", "objective" to QuestDisplayFormatter.name(quest))) {
                openLeaderboard(quest)
            }
        }
        val shown = presentNative(
            GuildDialogPage(
                lang.gui("guild_dialogs.quests.rankings"), listOf(description), actions,
                nativeAction(lang.gui("menu.quests.item.back.name"), ::open), theme = guild.guiTheme,
            ),
            active.weekId,
        )
        if (!shown) open()
    }

    private fun presentNative(view: GuildDialogPage, weekId: String): Boolean {
        val token = menuNavigator.currentNavigationToken()
        return nativeDialog.show(player, view, {
            player.isOnline && menuNavigator.isNavigationCurrent(token) &&
                memberService.getMember(player.uniqueId, guild.id) != null &&
                player.hasPermission("lumaguilds.guild.quests") && questService.activeQuestSet()?.weekId == weekId
        }) { player.sendMessage(lang.msg("menu.quests.feedback.no_quests")) }
    }

    private fun questItem(quest: QuestDefinition, progress: net.lumalyte.lg.domain.entities.GuildQuestProgress?): ItemStack {
        val count = progress?.currentCount ?: 0
        val percent = if (quest.targetCount > 0) ((count.coerceAtMost(quest.targetCount) * 100) / quest.targetCount).toInt() else 0
        return QuestIconProvider.itemFor(quest)
            .also { item -> item.editMeta { meta ->
                meta.displayName(lang.gui("menu.quests.item.quest.name", "objective" to QuestDisplayFormatter.name(quest)))
                val lore = mutableListOf<Component>(
                    tierLabel(quest.tier),
                    lang.gui("menu.quests.item.quest.description", "objective" to QuestDisplayFormatter.description(quest)),
                    Component.empty(),
                    lang.gui("menu.quests.item.quest.progress", "count" to count, "target" to quest.targetCount, "percent" to percent),
                    lang.gui("menu.quests.item.quest.reward", "xp" to quest.experienceReward)
                )
                lore += lang.gui("menu.quests.item.quest.rank", "rank" to (questService.rankFor(guild.id, quest.id)?.let { "#$it" } ?: "—"))
                lore += when {
                    progress?.claimed == true -> lang.gui("menu.quests.item.quest.claimed")
                    progress?.completedAt != null -> lang.gui("menu.quests.item.quest.completed")
                    else -> lang.gui("menu.quests.item.quest.in_progress")
                }
                lore += Component.empty()
                lore += lang.gui("menu.quests.item.quest.view_leaderboard")
                meta.lore(lore)
            }}
    }

    private fun tierLabel(tier: QuestRewardTier): Component = when (tier) {
        QuestRewardTier.COMMON -> lang.gui("menu.quests.tier.common")
        QuestRewardTier.CHALLENGING -> lang.gui("menu.quests.tier.challenging")
        QuestRewardTier.HEADLINE -> lang.gui("menu.quests.tier.headline")
        QuestRewardTier.CONDITIONED -> lang.gui("menu.quests.tier.conditioned")
    }

    private companion object {
        const val NATIVE_PAGE_SIZE = 3
    }

}
