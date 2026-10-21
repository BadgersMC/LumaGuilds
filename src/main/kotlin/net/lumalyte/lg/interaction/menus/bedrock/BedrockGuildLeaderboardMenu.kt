package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.RecruitmentResponseSession
import net.lumalyte.lg.interaction.menus.guild.guildLeaderboardSnapshot
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Same ranking snapshot as Java, presented through native Cumulus controls. */
internal class BedrockGuildLeaderboardMenu(
    navigator: MenuNavigator, player: Player, private val own: Guild, logger: Logger,
) : BaseBedrockMenu(navigator, player, logger) {
    private val guilds: GuildService by inject()
    private val members: MemberService by inject()
    private val quests: QuestService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private var tab = 0
    private var page = 0
    private val responses = RecruitmentResponseSession {
        val token = navigator.currentNavigationToken()
        val guard: () -> Boolean = { navigator.isNavigationCurrent(token) }
        guard
    }

    override fun getForm(): Form {
        val valid = responses.begin()
        val actions = mutableListOf<() -> Unit>()
        val builder = SimpleForm.builder().title(lang.bedrock("dialog_v2.leaderboard"))
        fun action(label: String, callback: () -> Unit) {
            builder.button(label)
            actions += callback
        }
        if (members.getMember(player.uniqueId, own.id) == null) {
            builder.content(lang.bedrock("command.migrated.guild.rename.you_are_not_in_a_guild"))
            action(lang.bedrock("recruitment.dialog.back")) { bedrockNavigator.goBack() }
        } else {
            val snapshot = guildLeaderboardSnapshot(player, guilds, members, quests, tab, page)
            page = snapshot.page
            builder.content("${page + 1}/${snapshot.pages} · " + lang.bedrock("dialog_v2.guild_rankings"))
            val keys = listOf("dialog_v2.rank_level", "dialog_v2.rank_wins", "dialog_v2.sort_online")
            keys.forEachIndexed { index, key ->
                action((if (tab == index) "[" else "") + lang.bedrock(key) + if (tab == index) "]" else "") {
                    tab = index; page = 0; open()
                }
            }
            snapshot.rows.forEach { row ->
                action("#${row.rank} ${row.guild.name}${if (row.guild.id == own.id) " (you)" else ""}\n" +
                    "${row.score} ${lang.bedrock(keys[tab])} · ${row.members} ${lang.bedrock("dialog_v2.members")}") {
                    menuNavigator.openMenu(BedrockGuildRecruitmentMenu(menuNavigator, player, logger, row.guild.id))
                }
            }
            if (page > 0) action(lang.bedrock("recruitment.previous")) { page--; open() }
            if (page + 1 < snapshot.pages) action(lang.bedrock("recruitment.next")) { page++; open() }
            action(lang.bedrock("recruitment.dialog.back")) { bedrockNavigator.goBack() }
        }
        fun submit(callback: () -> Unit) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline && valid()) {
                    onFormResponseReceived()
                    if (members.getMember(player.uniqueId, own.id) != null) callback()
                    else bedrockNavigator.goBack()
                }
            })
        }
        return builder.validResultHandler { result ->
            submit { actions.getOrNull(result.clickedButtonId())?.invoke() }
        }.closedOrInvalidResultHandler { _, _ -> submit { bedrockNavigator.goBack() } }.build()
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}
