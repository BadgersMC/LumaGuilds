package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.QuestDisplayFormatter
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GuildQuestsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val memberService: MemberService,
    private val questService: QuestService,
    private val lang: LangService
) : Menu {
    private var page = 0
    private val slots = listOf(10, 13, 19, 22, 28, 31, 37, 40)

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
        val header = ItemStack.of(Material.CLOCK).also { item -> item.editMeta { meta ->
            meta.displayName(lang.gui("menu.quests.item.header.name"))
            meta.lore(listOf(
                lang.gui("menu.quests.item.header.claimed", "claimed" to claimed, "total" to active.quests.count { it.targetCount > 0 }),
                lang.gui("menu.quests.item.header.reset", "time" to QuestDisplayFormatter.duration(questService.timeRemaining())),
                lang.gui("menu.quests.item.header.bonus", "xp" to questService.fullSetBonusExperience,
                    "status" to if (questService.isWeeklyBonusAwarded(guild.id)) "✓" else "…")
            ))
        }}
        pane.addItem(GuiItem(header), 4, 0)

        val pageQuests = active.quests.drop(page * slots.size).take(slots.size)
        pageQuests.forEachIndexed { index, quest ->
            val slot = slots[index]
            pane.addItem(GuiItem(questItem(quest, progress[quest.id])) {
                val current = progress[quest.id]
                if (current?.isCompletable(quest.targetCount) == true && questService.claimQuest(player.uniqueId, guild.id, quest.id)) {
                    player.sendMessage(lang.msg("menu.quests.feedback.claimed", "xp" to quest.experienceReward))
                    open()
                }
            }, slot % 9, slot / 9)
        }

        val back = ItemStack.of(Material.ARROW).also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.back.name")) } }
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 8, 0)
        if (page > 0) {
            val previous = ItemStack.of(Material.ARROW).also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.prev_page.name")) } }
            pane.addItem(GuiItem(previous) { page--; open() }, 0, 5)
        }
        if ((page + 1) * slots.size < active.quests.size) {
            val next = ItemStack.of(Material.ARROW).also { it.editMeta { meta -> meta.displayName(lang.gui("menu.quests.item.next_page.name")) } }
            pane.addItem(GuiItem(next) { page++; open() }, 8, 5)
        }
        gui.show(player)
    }

    private fun questItem(quest: QuestDefinition, progress: net.lumalyte.lg.domain.entities.GuildQuestProgress?): ItemStack {
        val count = progress?.currentCount ?: 0
        val percent = if (quest.targetCount > 0) ((count.coerceAtMost(quest.targetCount) * 100) / quest.targetCount).toInt() else 0
        val material = when (quest.tier) {
            QuestRewardTier.COMMON -> Material.PAPER
            QuestRewardTier.CHALLENGING -> Material.IRON_INGOT
            QuestRewardTier.HEADLINE -> Material.GOLD_INGOT
            QuestRewardTier.CONDITIONED -> Material.DIAMOND
        }
        return NexoItemProvider.getItemStackOrFallback("lg_quest_${quest.tier.name.lowercase()}") { ItemStack.of(material) }
            .also { item -> item.editMeta { meta ->
                meta.displayName(lang.gui("menu.quests.item.quest.name", "action" to QuestDisplayFormatter.token(quest.action.name), "target" to QuestDisplayFormatter.token(quest.target.id)))
                val lore = mutableListOf<Component>(
                    tierLabel(quest.tier),
                    lang.gui("menu.quests.item.quest.description", "amount" to quest.targetCount, "target" to QuestDisplayFormatter.token(quest.target.id),
                        "condition" to (quest.condition?.let { " ${QuestDisplayFormatter.token(it.type.name)} ${it.value?.let(QuestDisplayFormatter::token).orEmpty()}" } ?: "")),
                    Component.empty(),
                    lang.gui("menu.quests.item.quest.progress", "count" to count, "target" to quest.targetCount, "percent" to percent),
                    lang.gui("menu.quests.item.quest.reward", "xp" to quest.experienceReward)
                )
                if (quest.leaderboard) lore += lang.gui("menu.quests.item.quest.rank", "rank" to (questService.rankFor(guild.id, quest.id)?.let { "#$it" } ?: "—"))
                lore += when {
                    progress?.claimed == true -> lang.gui("menu.quests.item.quest.claimed")
                    progress?.isCompletable(quest.targetCount) == true -> lang.gui("menu.quests.item.quest.completed")
                    else -> lang.gui("menu.quests.item.quest.in_progress")
                }
                meta.lore(lore)
            }}
    }

    private fun tierLabel(tier: QuestRewardTier): Component = when (tier) {
        QuestRewardTier.COMMON -> lang.gui("menu.quests.tier.common")
        QuestRewardTier.CHALLENGING -> lang.gui("menu.quests.tier.challenging")
        QuestRewardTier.HEADLINE -> lang.gui("menu.quests.tier.headline")
        QuestRewardTier.CONDITIONED -> lang.gui("menu.quests.tier.conditioned")
    }

}
