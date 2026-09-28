package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Selected-member Bedrock actions matching the supported Java rank-change and kick flows. */
class BedrockGuildMemberDetailMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val member: Member,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val targetName = Bukkit.getOfflinePlayer(member.playerId).name
            ?: lang.bedrock("bedrock.member_list.unknown")
        val rankName = rankService.getRank(member.rankId)?.name
            ?: lang.bedrock("bedrock.member_list.unknown")
        val canManage = guildService.hasPermission(
            player.uniqueId,
            guild.id,
            RankPermission.MANAGE_MEMBERS
        )
        val canModerateTarget = canManage && member.playerId != player.uniqueId
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(lang.bedrock("bedrock.member_detail.title", "player" to targetName))
            .content(
                lang.bedrock(
                    "bedrock.member_detail.content",
                    "player" to targetName,
                    "rank" to rankName,
                    "joined" to member.joinedAt
                )
            )

        if (canModerateTarget) {
            builder = builder.button(lang.bedrock("bedrock.member_detail.button.rank"))
            actions += { openRankChange() }
            builder = builder.button(lang.bedrock("bedrock.member_detail.button.kick"))
            actions += { openKickConfirmation() }
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

    private fun canManageTarget(): Boolean =
        member.playerId != player.uniqueId &&
            guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)

    private fun openRankChange() {
        if (!canManageTarget()) {
            player.sendMessage(lang.msg("bedrock.member_detail.feedback.no_permission"))
            open()
            return
        }

        bedrockNavigator.openMenu(
            menuFactory.createGuildMemberRankMenu(menuNavigator, player, guild, member)
        )
    }

    private fun openKickConfirmation() {
        if (!canManageTarget()) {
            player.sendMessage(lang.msg("bedrock.member_detail.feedback.no_permission"))
            open()
            return
        }
        bedrockNavigator.openMenu(
            menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, member)
        )
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
