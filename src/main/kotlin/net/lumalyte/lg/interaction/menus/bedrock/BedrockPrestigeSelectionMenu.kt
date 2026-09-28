package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.PrestigeOverview
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

class BedrockPrestigeSelectionMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val prestige: GuildPrestigeService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        val overview = prestige.overview(guild.id)
        if (!isReady(overview)) {
            return statusForm(prestigeState(overview))
        }
        val ready = requireNotNull(overview)
        var builder = SimpleForm.builder()
            .title(lang.bedrock("menu.guild_progression.prestige.selection.title"))
            .content(prestigeState(ready))
        val actions = mutableListOf<() -> Unit>()
        ready.choices.forEach { reward ->
            builder = builder.button(
                listOf(
                    lang.bedrock(
                        "menu.guild_progression.prestige.selection.reward.name",
                        "reward" to reward.name,
                    ),
                    lang.bedrock(
                        "menu.guild_progression.prestige.selection.reward.level",
                        "level" to reward.level,
                    ),
                    lang.bedrock("menu.guild_progression.prestige.selection.reward.retain"),
                    lang.bedrock(
                        "menu.guild_progression.prestige.selection.reward.fee",
                        "fee" to (ready.nextFee ?: 0L),
                    ),
                    lang.bedrock("menu.guild_progression.prestige.selection.reward.action"),
                ).joinToString("\n")
            )
            actions += { quote(reward.id, reward.name) }
        }
        builder = builder.button(lang.bedrock("menu.guild_progression.prestige.selection.back"))
        actions += { bedrockNavigator.goBack() }

        return builder
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) actions.getOrNull(response.clickedButtonId())?.invoke()
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

    private fun quote(rewardId: String, rewardName: String) {
        val quote = prestige.quote(player.uniqueId, guild.id, rewardId)
        if (quote == null) {
            player.sendMessage(lang.msg("menu.guild_progression.prestige.feedback.quote_failed"))
            open()
            return
        }
        BedrockPrestigeConfirmationMenu(
            menuNavigator = menuNavigator,
            player = player,
            quote = quote,
            retainedRewardName = rewardName,
            back = ::open,
            success = { bedrockNavigator.goBack() },
            logger = logger,
        ).open()
    }

    private fun statusForm(message: String): Form = SimpleForm.builder()
        .title(lang.bedrock("menu.guild_progression.prestige.selection.title"))
        .content(message)
        .button(lang.bedrock("menu.guild_progression.prestige.selection.back"))
        .validResultHandler {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                onFormResponseReceived()
                if (player.isOnline) bedrockNavigator.goBack()
            })
        }
        .closedOrInvalidResultHandler { _, _ ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                onFormResponseReceived()
                if (player.isOnline) bedrockNavigator.goBack()
            })
        }
        .build()

    private fun isReady(overview: PrestigeOverview?): Boolean =
        overview != null && overview.enabled && overview.currentLevel == 100 &&
            overview.prestigeCount < overview.maxPrestigeCount && overview.choices.isNotEmpty()

    private fun prestigeState(overview: PrestigeOverview?): String = when {
        overview == null ->
            lang.bedrock("menu.guild_progression.prestige.state.unavailable")
        !overview.enabled ->
            lang.bedrock("menu.guild_progression.prestige.state.disabled")
        overview.prestigeCount >= overview.maxPrestigeCount ->
            lang.bedrock(
                "menu.guild_progression.prestige.state.maximum",
                "count" to overview.prestigeCount,
                "max" to overview.maxPrestigeCount,
            )
        overview.currentLevel < 100 ->
            lang.bedrock(
                "menu.guild_progression.prestige.state.level",
                "level" to overview.currentLevel,
            )
        overview.choices.isEmpty() ->
            lang.bedrock("menu.guild_progression.prestige.state.no_choices")
        else ->
            lang.bedrock(
                "menu.guild_progression.prestige.state.ready",
                "count" to overview.prestigeCount,
                "max" to overview.maxPrestigeCount,
                "fee" to (overview.nextFee ?: 0L),
                "choices" to overview.choices.size,
            )
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}