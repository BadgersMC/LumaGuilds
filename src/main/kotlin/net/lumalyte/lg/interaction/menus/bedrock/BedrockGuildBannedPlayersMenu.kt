package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildBanService
import net.lumalyte.lg.application.services.UnbanResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/** REQ-194: Bedrock list of the guild's bans; each button unbans that player. */
class BedrockGuildBannedPlayersMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val banService: GuildBanService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val bans = banService.bans(guild.id).take(MAX_BUTTONS)
        val format = DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format"))
        val unknown = lang.bedrock("bedrock.member_list.unknown")
        var builder = SimpleForm.builder()
            .title(lang.bedrock("guild_page.bans_title", "guild" to guild.name))
            .content(if (bans.isEmpty()) lang.bedrock("guild_page.bans.empty") else lang.bedrock("guild_page.bans.unban"))
        val actions = mutableListOf<() -> Unit>()
        bans.forEach { ban ->
            val name = Bukkit.getOfflinePlayer(ban.playerId).name ?: unknown
            builder = builder.button(
                lang.bedrock("guild_page.bans.entry", "player" to name) + "\n" +
                    lang.bedrock("guild_page.bans.date", "date" to ban.bannedAt.atZone(ZoneId.systemDefault()).format(format)),
            )
            actions += {
                val ok = banService.unban(player.uniqueId, guild.id, ban.playerId) == UnbanResult.UNBANNED
                player.sendMessage(
                    if (ok) lang.msg("guild_page.bans.unbanned", "player" to name) else lang.msg("guild_page.bans.failed", "player" to name),
                )
                open()
            }
        }
        builder = builder.button(lang.bedrock("bedrock.member_detail.button.back"))
        actions += { bedrockNavigator.goBack() }
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
        const val MAX_BUTTONS = 50
    }
}
