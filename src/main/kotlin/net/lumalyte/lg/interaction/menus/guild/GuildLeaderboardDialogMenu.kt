package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player

internal class GuildLeaderboardDialogMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private var own: Guild,
    private val guilds: GuildService,
    private val members: MemberService,
    private val quests: QuestService,
    private val lang: LangService,
) : Menu {
    private val dialog = GuildMenuDialog()
    private var tab = 0
    private var page = 0
    private var wins: Map<java.util.UUID, Int>? = null

    override fun open() {
        own = guilds.getGuild(own.id) ?: own
        if (members.getMember(player.uniqueId, own.id) == null) {
            navigator.goBack()
            return
        }
        if (tab == 1 && wins == null) wins = quests.recordedWins()
        val all = guilds.getAllGuilds()
        val values =
            all.associate { guild ->
                guild.id to
                    when (tab) {
                        0 -> guild.level
                        1 -> wins?.get(guild.id) ?: 0
                        else ->
                            members.getGuildMembers(guild.id).count { member ->
                                Bukkit.getPlayer(member.playerId)?.let { it.isOnline && player.canSee(it) } == true
                            }
                    }
            }
        val sorted = all.sortedWith(compareByDescending<Guild> { values[it.id] }.thenBy { it.name.lowercase() })
        val pages = maxOf(1, (sorted.size + 3) / 4)
        page = page.coerceIn(0, pages - 1)
        val cards =
            sorted.drop(page * 4).take(4).mapIndexed { index, guild ->
                val rank = page * 4 + index + 1
                val lines =
                    listOf(
                        Component.text(
                            "#$rank ${guild.name}${if (guild.id == own.id) " (you)" else ""}",
                            if (guild.id == own.id) NamedTextColor.GREEN else NamedTextColor.GOLD,
                        ),
                        Component.text(
                            "${members.getMemberCount(
                                guild.id,
                            )} ${lang.raw("dialog_v2.members")}",
                        ),
                    )
                GuildDialogItem(
                    GuildBannerItemResolver.resolveForDisplay(guild),
                    Component.join(JoinConfiguration.newlines(), lines),
                    lines,
                    referenceAction(Component.text(guild.name), "lg_nav_info") {
                        navigator.openMenu(GuildRecruitmentMenu(navigator, player, guild.id))
                    },
                    rightLines = listOf(Component.text(values[guild.id].toString()), when (tab) {
                        0 -> lang.gui("dialog_v2.rank_level")
                        1 -> lang.gui("dialog_v2.rank_wins")
                        else -> lang.gui("dialog_v2.sort_online")
                    }),
                )
            }
        val controls =
            buildList {
                if (page > 0) {
                    add(
                        referenceAction(lang.gui("recruitment.previous"), "lg_page_prev") {
                            page--
                            open()
                        },
                    )
                }
                if (page + 1 < pages) {
                    add(
                        referenceAction(lang.gui("recruitment.next"), "lg_page_next") {
                            page++
                            open()
                        },
                    )
                }
            }
        val token = navigator.currentNavigationToken()
        dialog.show(
            player,
            GuildDialogPage(
                lang.gui("dialog_v2.leaderboard"),
                cards,
                controls,
                referenceAction(lang.gui("recruitment.dialog.back"), "lg_back") { navigator.goBack() },
                theme = own.guiTheme,
                columns = 2,
                framedHeading = true,
                subtitle =
                    Component.text(
                        "${page + 1}/$pages · ${lang.raw(
                            if (tab == 1) "dialog_v2.recorded_wins" else "dialog_v2.guild_rankings",
                        )}",
                    ),
                bodyActions =
                    listOf("dialog_v2.rank_level", "dialog_v2.rank_wins", "dialog_v2.sort_online").mapIndexed {
                            index,
                            key,
                        ->
                        val name = lang.raw(key)
                        referenceAction(Component.text(if (tab == index) "[$name]" else name), "lg_sort") {
                            tab = index
                            page = 0
                            open()
                        }
                    },
            ),
            {
                player.isOnline && navigator.isNavigationCurrent(token) &&
                    members.getMember(player.uniqueId, own.id) != null
            },
        ) { player.sendMessage(lang.msg("recruitment.feedback.failed")) }
    }
}
