package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Branches and selection use native clickable text; the complete panning tree is in Advancements. */
internal class GuildAchievementsMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val presenter: GuildDialogPresenter = GuildMenuDialog(),
) : Menu, KoinComponent {
    private val runtime: GuildAchievementRuntime by inject()
    private val members: MemberService by inject()
    private val guilds: GuildService by inject()
    private val lang: LangService by inject()
    private var category = "Foundations"
    private var page = 0
    private var selected: String? = null
    private var detailTab = 0

    override fun open() {
        val token = navigator.currentNavigationToken()
        if (!valid(token)) return
        val record = runtime.read(player) ?: return player.sendMessage(lang.gui("achievements.unavailable"))
        val nodes = runtime.engine.definitions.filter { it.category == category && it.requirement != "ROOT" }
        val pages = ((nodes.size + 3) / 4).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val visible = nodes.drop(page * 4).take(4)
        val node = runtime.engine.definitions.find { it.id == selected } ?: visible.first()
        selected = node.id
        fun action(label: Component, run: () -> Unit) = GuildDialogAction(label, Component.empty(), run)
        fun nodeAction(n: GuildAchievement): GuildDialogAction {
            val complete = (record.progress[n.id] ?: 0) >= n.goal
            val prefix = if (complete) "[✓] " else "[◇] "
            val label = Component.text(prefix + shortName(n.title), if (complete) NamedTextColor.GOLD else NamedTextColor.GRAY)
            return GuildDialogAction(label, Component.text(n.title + " · " + (record.progress[n.id] ?: 0) + "/" + n.goal)) {
                selected = n.id
                if (n.category != category) {
                    category = n.category
                    page = runtime.engine.definitions.filter { it.category == category && it.requirement != "ROOT" }
                        .indexOf(n).coerceAtLeast(0) / 4
                }
                open()
            }
        }
        val detailTabs = listOf(lang.gui("achievements.inspect_overview"), lang.gui("achievements.inspect_earning"),
            lang.gui("achievements.inspect_reward"), lang.gui("achievements.inspect_rules")).mapIndexed { index, label ->
            action(if (index == detailTab) Component.text("[").append(label).append(Component.text("]")) else label) {
                detailTab = index; open()
            }
        }
        val diagram = listOf(detailTabs) + visible.map { child ->
            val parent = runtime.engine.definitions.find { it.id == child.parent }
            listOfNotNull(parent?.let(::nodeAction), nodeAction(child))
        }
        val progress = record.progress[node.id] ?: 0
        val complete = progress >= node.goal
        // One inspector section at a time keeps the underlay and actions inside the native viewport.
        val cards = listOf(
            referenceItem(icon(node), listOf(Component.text(node.title),
                if (complete) lang.gui("achievements.completed", "current" to progress, "goal" to node.goal)
                else lang.gui("achievements.progress", "current" to progress, "goal" to node.goal),
                *node.description.map { Component.text(it) }.toTypedArray())),
            referenceItem("lg_sources", listOf(lang.gui("achievements.how"),
                Component.text(earning(node)), Component.text(credit(node)))),
            referenceItem("lg_reward", listOf(lang.gui("achievements.reward"),
                if (node.personalXp > 0) lang.gui("achievements.personal_xp", "amount" to node.personalXp)
                else lang.gui("achievements.no_extra"), lang.gui("achievements.no_guild_xp"))),
            referenceItem("lg_prestige", listOf(lang.gui("achievements.record_rules"), lang.gui("achievements.offline"),
                lang.gui("achievements.retained"), lang.gui("achievements.connections"))),
        ).let { listOf(it[detailTab]) }
        val actions = buildList {
            if (page > 0) add(action(lang.gui("achievements.previous")) { page--; selected = null; open() })
            if (page + 1 < pages) add(action(lang.gui("achievements.next")) { page++; selected = null; open() })
            add(action(if (record.tracked == node.id) lang.gui("achievements.untrack") else lang.gui("achievements.track")) {
                if (valid(token)) runtime.track(player, if (record.tracked == node.id) null else node.id)
                open()
            })
            record.tracked?.let { id -> add(action(lang.gui("achievements.goal")) {
                runtime.engine.definitions.find { it.id == id }?.let {
                    category = it.category
                    page = runtime.engine.definitions.filter { n -> n.category == category && n.requirement != "ROOT" }.indexOf(it) / 4
                    selected = id
                }; open()
            }) }
            add(action(lang.gui("achievements.vanilla")) {
                player.sendMessage(if (runtime.projectionAvailable()) lang.gui("achievements.open_vanilla") else lang.gui("achievements.renderer_unavailable"))
                open()
            })
        }
        presenter.show(player, GuildDialogPage(lang.gui("achievements.title"), cards, actions,
            action(lang.gui("achievements.back"), navigator::goBack), columns = 2,
            theme = guilds.getGuild(guild.id)?.guiTheme ?: guild.guiTheme,
            subtitle = lang.gui("achievements.subtitle", "category" to category, "page" to page + 1, "pages" to pages),
            bodyActions = CATEGORIES.map { cat -> action(Component.text(if (cat == category) "[$cat]" else cat)) {
                category = cat; page = 0; selected = null; open()
            } }, diagram = diagram), { valid(token) }, { player.sendMessage(lang.gui("achievements.unavailable")) })
    }

    private fun valid(token: Long) = player.isOnline && navigator.isNavigationCurrent(token) && members.getMember(player.uniqueId, guild.id) != null
    private fun shortName(name: String) = if (name.length <= 19) name else name.take(18) + "…"
    private fun icon(node: GuildAchievement) = when (node.category) {
        "Combat" -> "lg_combat"
        "Commerce" -> "lg_bank"
        "Cooperation" -> "lg_sources"
        "Reputation" -> "lg_prestige"
        else -> "lg_banner"
    }
    private fun earning(node: GuildAchievement): String = when (node.requirement) {
        "GUILD_STALL_OWNED" -> "Market must confirm your guild owns a stall (active or rent grace). Existing ownership qualifies; checked on join and each minute. No purchase receipt is required."
        "GUILD_SHOP_SALE" -> "Complete ${node.goal} paid guild SELL shop sales confirmed by Market. Own-guild, personal, free, BUY and barter transactions do not count."
        "GUILD_LEVEL_UP" -> "Reach guild level ${node.target}; current level is checked when joining."
        "GUILD_COMBINED_PLAYTIME" -> "Accumulate 1,000 combined vanilla playtime hours among current guild members."
        "GUILD_COLLECTIVE_CROP_HARVEST" -> "Break mature eligible crops, melons or pumpkins in Survival or Adventure."
        "GUILD_COLLECTIVE_SMELT" -> "Collect smelted or cooked furnace output in Survival or Adventure."
        "GUILD_COLLECTIVE_BLOCK_BREAK" -> "Break blocks in Survival or Adventure; cancelled actions do not count."
        "GUILD_COLLECTIVE_BOSS_KILL" -> if (node.target == "ENDER_DRAGON") "Kill the Ender Dragon with at least three guild members in the End world." else "Kill a Wither with a guild member credited as killer."
        "GUILD_LEADERBOARD_RANK" -> "Enter a qualifying top-${node.target} rank on a guild leaderboard."
        "GUILD_MEMBER_REMOVED" -> "Kick another member from your guild; voluntary departures do not count."
        else -> node.description.joinToString(" ")
    }
    private fun credit(node: GuildAchievement): String = when (node.requirement) {
        "GUILD_CREATE", "GUILD_BANNER_SET", "GUILD_HOME_SET", "GUILD_VAULT_PLACED", "GUILD_MEMBER_REMOVED",
        "GUILD_WAR_KILL", "GUILD_FRIENDLY_FIRE", "GUILD_OWNERSHIP_TRANSFER", "SHOP_CREATED" -> "Credit: the acting player (new owner for ownership transfers)."
        "GUILD_COLLECTIVE_BOSS_KILL" -> "Credit: online guild members in the boss's world."
        "GUILD_ALLIANCE_FORMED" -> "Credit: online members of both allied guilds."
        else -> "Credit: online members of the qualifying guild; records remain personal."
    }
    companion object { val CATEGORIES = listOf("Foundations", "Cooperation", "Combat", "Commerce", "Reputation") }
}
