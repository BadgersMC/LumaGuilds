package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeRejection
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Keeps one immutable prestige quote alive across uncertain retries. */
class BedrockPrestigeConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val quote: PrestigeQuote,
    private val retainedRewardName: String,
    private val back: () -> Unit,
    private val success: () -> Unit,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val prestige: GuildPrestigeService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form = ModalForm.builder()
        .title(lang.bedrock("menu.guild_progression.prestige.confirmation.title"))
        .content(
            listOf(
                lang.bedrock(
                    "menu.guild_progression.prestige.confirmation.summary.reward",
                    "reward" to retainedRewardName,
                ),
                lang.bedrock(
                    "menu.guild_progression.prestige.confirmation.summary.fee",
                    "fee" to quote.quotedFee,
                ),
                lang.bedrock("menu.guild_progression.prestige.confirmation.summary.reset"),
                lang.bedrock("menu.guild_progression.prestige.confirmation.summary.permanent"),
            ).joinToString("\n")
        )
        .button1(lang.bedrock("menu.guild_progression.prestige.confirmation.confirm"))
        .button2(lang.bedrock("menu.guild_progression.prestige.confirmation.cancel"))
        .validResultHandler { response ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                respond(response.clickedButtonId() == 0)
            })
        }
        .closedOrInvalidResultHandler { _, _ ->
            Bukkit.getScheduler().runTask(plugin, Runnable { respond(false) })
        }
        .build()
    internal fun respond(confirm: Boolean) {
        onFormResponseReceived()
        if (!player.isOnline) return
        if (!confirm) {
            back()
            return
        }

        val result = prestige.confirm(player.uniqueId, quote)
        player.sendMessage(feedback(result))
        when (result) {
            is PrestigeResult.Applied -> success()
            is PrestigeResult.Rejected -> back()
            is PrestigeResult.Failed -> open()
        }
    }

    private fun feedback(result: PrestigeResult): Component = when (result) {
        is PrestigeResult.Applied -> lang.msg(
            "menu.guild_progression.prestige.feedback.applied",
            "reward" to retainedRewardName,
            "fee" to result.fee,
            "count" to result.prestigeCount,
        )
        is PrestigeResult.Failed ->
            lang.msg("menu.guild_progression.prestige.feedback.failed")
        is PrestigeResult.Rejected -> rejectedFeedback(result.reason)
    }

    private fun rejectedFeedback(reason: PrestigeRejection): Component = when (reason) {
        PrestigeRejection.UNAVAILABLE ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.unavailable")
        PrestigeRejection.UNAUTHORIZED ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.unauthorized")
        PrestigeRejection.UNINITIALIZED ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.uninitialized")
        PrestigeRejection.NOT_LEVEL_100 ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.not_level_100")
        PrestigeRejection.MAX_PRESTIGE ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.max_prestige")
        PrestigeRejection.INVALID_SELECTION ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.invalid_selection")
        PrestigeRejection.STALE_STATE ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.stale_state")
        PrestigeRejection.FEE_CHANGED ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.fee_changed")
        PrestigeRejection.ACTIVE_WAR ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.active_war")
        PrestigeRejection.INSUFFICIENT_FUNDS ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.insufficient_funds")
        PrestigeRejection.POST_PRESTIGE_CAPACITY ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.post_prestige_capacity")
        PrestigeRejection.PENDING_GOLD ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.pending_gold")
        PrestigeRejection.FROZEN ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.frozen")
        PrestigeRejection.ID_CONFLICT ->
            lang.msg("menu.guild_progression.prestige.feedback.rejected.id_conflict")
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}
