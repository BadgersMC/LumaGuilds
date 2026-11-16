package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.achievements.AchievementRecord
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * REQ-263: Guild Advancements in the card layout: four cards a page in two columns, category
 * buttons along the top with Combat milestones last, and a detail page with Track. Progress,
 * tracking and rewards come from the same runtime as before; the full tree stays in Advancements.
 */
internal class GuildAchievementsMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val presenter: GuildDialogPresenter = GuildMenuDialog(),
    private var guildMilestones: Boolean = false,
    private val fromStatistics: Boolean = false,
    private val adminOverrideService: AdminOverrideService? = null,
) : Menu, KoinComponent {
    private val runtime: GuildAchievementRuntime by inject()
    private val members: MemberService by inject()
    private val guilds: GuildService by inject()
    private val lang: LangService by inject()
    private val kills: net.lumalyte.lg.application.services.KillService by inject()
    private val wars: net.lumalyte.lg.application.services.WarService by inject()
    private var guildPage = 0
    private var category = "Foundations"
    private var page = 0
    private var selected: String? = null

    override fun open() {
        val token = navigator.currentNavigationToken()
        if (!valid(token)) return
        if (guildMilestones) {
            showGuildMilestones(token)
            return
        }
        val record = runtime.read(player) ?: return player.sendMessage(lang.gui("achievements.unavailable"))
        selected?.let { id -> runtime.engine.definitions.find { it.id == id } }?.let { node ->
            showDetail(token, record, node)
            return
        }
        val nodes = runtime.engine.definitions.filter { it.category == category && it.requirement != "ROOT" }
        val pages = ((nodes.size + PAGE - 1) / PAGE).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val cards = nodes.drop(page * PAGE).take(PAGE).map { node -> card(node, record) }
        val done = nodes.count { (record.progress[it.id] ?: 0) >= it.goal }
        val actions = buildList {
            if (page > 0) add(referenceAction(lang.gui("achievements.previous"), "lg_page_prev") { page--; open() })
            if (page + 1 < pages) add(referenceAction(lang.gui("achievements.next"), "lg_page_next") { page++; open() })
            record.tracked?.let { id -> runtime.engine.definitions.find { it.id == id } }?.let { tracked ->
                add(referenceAction(lang.gui("achievements.tracking", "title" to tracked.title,
                    "current" to n(record.progress[tracked.id] ?: 0), "goal" to n(tracked.goal)), "lg_reward") {
                    category = tracked.category
                    selected = tracked.id
                    open()
                })
            }
            add(treeAction())
        }
        show(token, cards, actions, lang.gui("achievements.subtitle", "category" to category,
            "done" to done, "total" to nodes.size, "page" to page + 1, "pages" to pages))
    }

    /** REQ-263: one advancement as a card: icon, title, progress, what to do and its guild XP. */
    private fun card(node: GuildAchievement, record: AchievementRecord): GuildDialogItem {
        val progress = record.progress[node.id] ?: 0
        val lines = buildList {
            add(Component.text(node.title, NamedTextColor.GOLD))
            add(status(progress, node.goal))
            node.description.firstOrNull()?.let { add(Component.text(it, NamedTextColor.GRAY)) }
            if (node.guildXp > 0) add(lang.gui("achievements.card_reward", "amount" to n(node.guildXp)))
            if (record.tracked == node.id) add(lang.gui("achievements.card_tracked"))
        }
        return referenceItem(icon(node), lines, referenceAction(Component.text(node.title), icon(node)) {
            selected = node.id
            open()
        })
    }

    private fun status(progress: Int, goal: Int): Component =
        if (progress >= goal) lang.gui("achievements.completed", "current" to n(progress), "goal" to n(goal))
        else lang.gui("achievements.progress", "current" to n(progress), "goal" to n(goal))

    /** REQ-263: everything about one advancement, with Track and the way back to its category. */
    private fun showDetail(token: Long, record: AchievementRecord, node: GuildAchievement) {
        val progress = record.progress[node.id] ?: 0
        val lines = buildList {
            add(Component.text(node.title, NamedTextColor.GOLD))
            add(status(progress, node.goal))
            node.description.forEach { add(Component.text(it)) }
            add(Component.text(earning(node), NamedTextColor.GRAY))
            add(Component.text(credit(node), NamedTextColor.GRAY))
            add(if (node.guildXp > 0) lang.gui("achievements.guild_xp", "amount" to n(node.guildXp)) else lang.gui("achievements.no_guild_xp"))
            if (node.personalXp > 0) add(lang.gui("achievements.personal_xp", "amount" to node.personalXp))
        }
        val tracked = record.tracked == node.id
        val actions = listOf(
            referenceAction(if (tracked) lang.gui("achievements.untrack") else lang.gui("achievements.track"), "lg_reward") {
                if (valid(token)) runtime.track(player, if (tracked) null else node.id)
                open()
            },
            referenceAction(lang.gui("achievements.back_category", "category" to node.category), "lg_back") {
                category = node.category
                page = runtime.engine.definitions.filter { it.category == category && it.requirement != "ROOT" }
                    .indexOf(node).coerceAtLeast(0) / PAGE
                selected = null
                open()
            },
        )
        show(token, listOf(referenceItem(icon(node), lines)), actions, lang.gui("achievements.detail_subtitle", "category" to node.category), columns = 1)
    }

    private fun treeAction(): GuildDialogAction = referenceAction(lang.gui("achievements.tree_action"), "lg_sources") {
        if (runtime.projectionAvailable()) {
            navigator.invalidateCurrentNavigation()
            player.closeDialog()
            player.sendMessage(lang.gui("achievements.tree_key_hint")
                .append(Component.keybind("key.advancements"))
                .append(lang.gui("achievements.tree_find_hint")))
        } else {
            player.sendMessage(lang.gui("achievements.renderer_unavailable"))
            // afterAction NONE dismisses terminal callbacks unless a replacement is shown.
            open()
        }
    }

    /** Category buttons along the top; Combat milestones is the guild-wide last category. */
    private fun tabs(): List<GuildDialogAction> = CATEGORIES.map { cat ->
        GuildDialogAction(tab(Component.text(cat), !guildMilestones && cat == category), Component.empty()) {
            guildMilestones = false; category = cat; page = 0; selected = null; open()
        }
    } + GuildDialogAction(tab(lang.gui("achievements.milestones_tab"), guildMilestones), Component.empty()) {
        guildMilestones = true; guildPage = 0; selected = null; open()
    }

    private fun tab(label: Component, current: Boolean): Component =
        if (current) Component.text("[", NamedTextColor.GOLD).append(label).append(Component.text("]", NamedTextColor.GOLD)) else label

    private fun show(token: Long, cards: List<GuildDialogItem>, actions: List<GuildDialogAction>, subtitle: Component, columns: Int = 2) {
        presenter.show(player, GuildDialogPage(lang.gui("achievements.title"), cards, actions,
            referenceAction(backLabel(), "lg_back", navigator::goBack), columns = columns,
            theme = guilds.getGuild(guild.id)?.guiTheme ?: guild.guiTheme,
            subtitle = subtitle, bodyActions = tabs()),
            { valid(token) }, { player.sendMessage(lang.gui("achievements.unavailable")) })
    }

    private fun showGuildMilestones(token: Long) {
        val stats = kills.getGuildKillStats(guild.id)
        val history = wars.getWarHistory(guild.id, 100)
        val badges = net.lumalyte.lg.domain.achievements.GuildCombatMilestones.evaluate(
            stats.totalKills, stats.totalDeaths, stats.netKills, stats.killDeathRatio,
            history.size, history.count { it.winner == guild.id },
        )
        val pages = ((badges.size + PAGE - 1) / PAGE).coerceAtLeast(1)
        guildPage = guildPage.coerceIn(0, pages - 1)
        val cards = badges.drop(guildPage * PAGE).take(PAGE).map { badge ->
            referenceItem(if (badge.achieved) "lg_confirm" else "lg_combat", listOf(
                Component.text(badge.title, if (badge.achieved) NamedTextColor.GOLD else NamedTextColor.GRAY),
                if (badge.achieved) lang.gui("achievements.guild_met") else lang.gui("achievements.guild_not_met"),
                Component.text(badge.condition, NamedTextColor.GRAY), Component.text(badge.progress),
            ))
        }
        val actions = buildList {
            if (guildPage > 0) add(referenceAction(lang.gui("achievements.previous"), "lg_page_prev") { guildPage--; open() })
            if (guildPage + 1 < pages) add(referenceAction(lang.gui("achievements.next"), "lg_page_next") { guildPage++; open() })
            add(treeAction())
        }
        show(token, cards, actions, lang.gui("achievements.guild_summary", "count" to badges.count { it.achieved },
            "total" to badges.size, "page" to guildPage + 1, "pages" to pages))
    }

    private fun backLabel() = if (fromStatistics) lang.gui("achievements.back_statistics") else lang.gui("achievements.back")
    private fun isMember() = members.getMember(player.uniqueId, guild.id) != null

    private fun valid(token: Long): Boolean {
        val navigationValid = player.isOnline && navigator.isNavigationCurrent(token)
        val scopedMilestones = guildMilestones && adminOverrideService?.getViewedGuild(player.uniqueId) == guild.id
        return navigationValid && (isMember() || scopedMilestones)
    }
    private fun icon(node: GuildAchievement) = when (node.category) {
        "Combat" -> "lg_combat"
        "Commerce" -> "lg_bank"
        "Cooperation" -> "lg_sources"
        "Reputation" -> "lg_prestige"
        else -> "lg_banner"
    }
    private fun credit(node: GuildAchievement): String = when (node.requirement) {
        "GUILD_CREATE", "GUILD_BANNER_SET", "GUILD_HOME_SET", "GUILD_VAULT_PLACED", "GUILD_MEMBER_REMOVED",
        "GUILD_WAR_KILL", "GUILD_FRIENDLY_FIRE", "GUILD_OWNERSHIP_TRANSFER", "SHOP_CREATED" -> "Credit: the acting player (new owner for ownership transfers)."
        "GUILD_COLLECTIVE_BOSS_KILL" -> "Credit: online guild members in the boss's world."
        "GUILD_ALLIANCE_FORMED" -> "Credit: online members of both allied guilds."
        else -> "Credit: online members of the qualifying guild; records remain personal."
    }
    companion object {
        private const val PAGE = 4
        val CATEGORIES = listOf("Foundations", "Cooperation", "Combat", "Commerce", "Reputation")

        fun earning(node: GuildAchievement): String = when (node.requirement) {
            "GUILD_STALL_OWNED" -> "Market must confirm your guild owns a stall (active or rent grace). Existing ownership qualifies; checked on join and each minute. No purchase receipt is required."
            "GUILD_SHOP_SALE" -> "Complete ${n(node.goal)} paid guild SELL shop sales confirmed by Market. Own-guild, personal, free, BUY and barter transactions do not count."
            "GUILD_LEVEL_UP" -> "Reach guild level ${node.target}; current level is checked when joining."
            "GUILD_COMBINED_PLAYTIME" -> "Accumulate ${n(node.goal)} combined vanilla playtime hours among current guild members."
            "GUILD_COLLECTIVE_CROP_HARVEST" -> "Break ${n(node.goal)} mature eligible crops, melons or pumpkins in Survival or Adventure."
            "GUILD_COLLECTIVE_SMELT" -> "Collect ${n(node.goal)} smelted or cooked furnace items in Survival or Adventure."
            "GUILD_COLLECTIVE_BLOCK_BREAK" -> "Break ${n(node.goal)} blocks in Survival or Adventure; cancelled actions do not count."
            "GUILD_COLLECTIVE_BOSS_KILL" -> if (node.target == "ENDER_DRAGON") "Kill the Ender Dragon ${times(node.goal)}with at least three guild members in the End world." else "Kill a Wither ${times(node.goal)}with a guild member credited as killer."
            "GUILD_COLLECTIVE_MOB_KILL" -> "Kill ${n(node.goal)} hostile mobs in Survival or Adventure."
            "GUILD_WAR_KILL" -> "Kill ${n(node.goal)} players from a guild you are at war with."
            "GUILD_WAR_WON" -> "Win ${n(node.goal)} guild wars."
            "KOTH_CAPTURE" -> "Capture the Warzone KOTH ${n(node.goal)} times."
            "GUILD_ALLIANCE_FORMED" -> "Form ${n(node.goal)} alliances with other guilds."
            "GUILD_MEMBER_COUNT" -> "Have ${n(node.goal)} members in your guild at once; checked when joining and each minute."
            "GUILD_AGE_DAYS" -> "Keep your guild for ${n(node.goal)} days since it was founded; checked when joining and each minute."
            "GUILD_LEADERBOARD_RANK" -> "Enter a qualifying top-${node.target} rank on a guild leaderboard."
            "GUILD_MEMBER_REMOVED" -> "Kick another member from your guild; voluntary departures do not count."
            else -> node.description.joinToString(" ")
        }
        fun n(value: Int) = "%,d".format(java.util.Locale.ROOT, value)
        private fun times(goal: Int) = if (goal > 1) "${n(goal)} times " else ""
    }
}
