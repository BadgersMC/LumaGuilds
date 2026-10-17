package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
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

/** Bedrock form for the same inbound ally-home whitelist used by Java. */
class BedrockAllyHomeAccessMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            return statusForm(lang.bedrock("menu.ally_home_access.permission_denied"))
        }
        val current = guildService.getGuild(guild.id)
            ?: return statusForm(lang.bedrock("bedrock.ally_home_access.missing_guild"))
        val allies = activeAllies()
        val actions = mutableListOf<() -> Unit>()
        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.ally_home_access.title"))
            .content(
                if (allies.isEmpty()) {
                    lang.bedrock("bedrock.ally_home_access.no_allies")
                } else {
                    lang.bedrock("menu.ally_home_access.info.description") + "\n" +
                        lang.bedrock("menu.ally_home_access.info.instructions")
                }
            )

        allies.forEach { (allyId, allyName) ->
            val allowed = allyId in current.allyHomeAllowedGuilds
            val label = if (allowed) {
                lang.bedrock("menu.ally_home_access.ally.allowed", "guild" to allyName)
            } else {
                lang.bedrock("menu.ally_home_access.ally.denied", "guild" to allyName)
            }
            builder = builder.button(label)
            actions += { toggleAlly(allyId) }
        }
        builder = builder.button(lang.bedrock("menu.ally_home_access.back"))
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

    private fun activeAllies(): List<Pair<UUID, String>> =
        relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .mapNotNull { relation ->
                val otherId = relation.getOtherGuild(guild.id)
                guildService.getGuild(otherId)?.let { otherId to it.name }
            }
            .sortedBy { it.second.lowercase() }

    private fun toggleAlly(allyId: UUID) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.ally_home_access.permission_denied"))
            bedrockNavigator.goBack()
            return
        }
        if (activeAllies().none { it.first == allyId }) {
            open()
            return
        }
        val current = guildService.getGuild(guild.id) ?: return
        val allowed = current.allyHomeAllowedGuilds.toMutableSet()
        if (!allowed.add(allyId)) allowed.remove(allyId)
        guildService.setAllyHomeAllowedGuilds(guild.id, allowed.toSet(), player.uniqueId)
        open()
    }

    private fun statusForm(message: String): Form = SimpleForm.builder()
        .title(lang.bedrock("menu.ally_home_access.title"))
        .content(message)
        .button(lang.bedrock("menu.ally_home_access.back"))
        .validResultHandler { runOnServerThread { bedrockNavigator.goBack() } }
        .closedOrInvalidResultHandler { _, _ -> runOnServerThread { bedrockNavigator.goBack() } }
        .build()

    private fun runOnServerThread(action: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit
}
