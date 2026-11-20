package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BanResult
import net.lumalyte.lg.application.services.GuildBanService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.UUID
import java.util.logging.Logger

/** REQ-194: Bedrock confirmation before banning a player from the guild. */
class BedrockGuildBanConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val targetId: UUID,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val banService: GuildBanService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val name = Bukkit.getOfflinePlayer(targetId).name ?: lang.bedrock("bedrock.member_list.unknown")
        val wasMember = (banService.check(player.uniqueId, guild.id, targetId) as? BanResult.Banned)?.wasMember ?: true
        return SimpleForm.builder()
            .title(lang.bedrock("guild_ban.confirm.title", "player" to name))
            .content(
                (if (wasMember) lang.bedrock("guild_ban.confirm.member_lore") else lang.bedrock("guild_ban.confirm.outsider_lore")) + "\n\n" +
                    lang.bedrock("guild_ban.confirm.undo_lore"),
            )
            .button(lang.bedrock("guild_ban.confirm.confirm", "player" to name))
            .button(lang.bedrock("guild_ban.confirm.cancel"))
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline) return@Runnable
                    onFormResponseReceived()
                    if (response.clickedButtonId() == 0) confirm(name) else bedrockNavigator.goBack()
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

    private fun confirm(name: String) {
        val result = banService.ban(player.uniqueId, guild.id, targetId)
        player.sendMessage(net.lumalyte.lg.interaction.menus.guild.GuildBanMessages.ban(lang, result, name))
        if (result is BanResult.Banned) {
            Bukkit.getPlayer(targetId)?.sendMessage(lang.msg("guild_ban.notify.target", "guild" to guild.name))
            bedrockNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            bedrockNavigator.goBack()
        }
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
