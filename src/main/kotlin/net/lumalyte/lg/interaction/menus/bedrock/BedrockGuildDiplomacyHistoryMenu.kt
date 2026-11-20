package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.DiplomacyLog
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.DiplomacyHistoryText
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** REQ-204: Bedrock diplomacy history, 20 entries per page. */
class BedrockGuildDiplomacyHistoryMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val history: DiplomacyLog by inject()
    private val guildService: GuildService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    private var page = 0

    override fun getForm(): Form {
        val total = history.count(guild.id)
        val pages = maxOf(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pages - 1)
        val unknown = lang.bedrock("menu.statistics.common.unknown_guild")
        val lines = history.page(guild.id, page * PAGE_SIZE, PAGE_SIZE).map { entry ->
            DiplomacyHistoryText.bedrockLine(lang, entry, guildService.getGuild(entry.otherGuildId)?.name ?: unknown)
        }
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(lang.bedrock("diplomacy_history.title", "guild" to guild.name))
            .content(if (lines.isEmpty()) lang.bedrock("diplomacy_history.empty.lore") else lines.joinToString("\n\n"))
        if (page > 0) { builder = builder.button(lang.bedrock("guild_page.nav.previous")); actions += { page--; open() } }
        if (page < pages - 1) { builder = builder.button(lang.bedrock("guild_page.nav.next")); actions += { page++; open() } }
        builder = builder.button(lang.bedrock("guild_page.nav.back")); actions += { bedrockNavigator.goBack() }
        return builder
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    onFormResponseReceived()
                    actions.getOrNull(response.clickedButtonId())?.invoke()
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit

    private companion object {
        const val PAGE_SIZE = 20
    }
}
