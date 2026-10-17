package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.utils.RankNameContent

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
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

/** Bedrock form for the same per-home rank whitelist used by Java. */
class BedrockHomeAccessMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val homeName: String,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            return statusForm(lang.bedrock("menu.home_access.permission_denied"))
        }
        val home = guildService.getHome(guild.id, homeName)
            ?: return statusForm(lang.bedrock("menu.home_access.missing", "home" to homeName))
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val ownerRankId = rankService.getHighestRank(guild.id)?.id
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.home_access.title", "home" to homeName))
            .content(lang.bedrock("bedrock.home_access.description"))

        ranks.forEach { rank ->
            val isOwner = rank.id == ownerRankId
            val allowed = isOwner || rank.id in home.allowedRankIds
            val label = if (allowed) {
                lang.bedrock("menu.home_access.rank.allowed", "rank" to RankNameContent.miniMessage(rank.name))
            } else {
                lang.bedrock("menu.home_access.rank.denied", "rank" to RankNameContent.miniMessage(rank.name))
            }
            builder = builder.button(label)
            val action: () -> Unit = if (isOwner) {
                { open() }
            } else {
                { toggleRank(rank.id) }
            }
            actions.add(action)
        }

        builder = builder.button(lang.bedrock("menu.home_access.back"))
        actions += { bedrockNavigator.goBack() }

        return builder
            .validResultHandler { response ->
                runOnServerThread {
                    onFormResponseReceived()
                    actions.getOrNull(response.clickedButtonId())?.invoke()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                runOnServerThread {
                    onFormResponseReceived()
                    bedrockNavigator.goBack()
                }
            }
            .build()
    }

    private fun toggleRank(rankId: UUID) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.home_access.permission_denied"))
            bedrockNavigator.goBack()
            return
        }
        val current = guildService.getHome(guild.id, homeName)
        if (current == null) {
            player.sendMessage(lang.msg("menu.home_access.missing", "home" to homeName))
            bedrockNavigator.goBack()
            return
        }

        val allowed = current.allowedRankIds.toMutableSet()
        if (!allowed.add(rankId)) allowed.remove(rankId)
        guildService.setHomeAllowedRanks(guild.id, homeName, allowed.toSet(), player.uniqueId)
        open()
    }

    private fun statusForm(message: String): Form = SimpleForm.builder()
        .title(lang.bedrock("menu.home_access.title", "home" to homeName))
        .content(message)
        .button(lang.bedrock("menu.home_access.back"))
        .validResultHandler {
            runOnServerThread { bedrockNavigator.goBack() }
        }
        .closedOrInvalidResultHandler { _, _ ->
            runOnServerThread { bedrockNavigator.goBack() }
        }
        .build()

    private fun runOnServerThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
