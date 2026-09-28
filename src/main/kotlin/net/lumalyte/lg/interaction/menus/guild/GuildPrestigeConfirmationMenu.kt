package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeRejection
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Confirms a previously quoted prestige. The same quote (and transaction ID) survives
 * uncertain failures, making a player retry idempotent instead of charging twice.
 */
class GuildPrestigeConfirmationMenu(
    private val player: Player,
    private val quote: PrestigeQuote,
    private val retainedRewardName: String,
    private val back: () -> Unit,
    private val success: () -> Unit,
) : Menu, KoinComponent {
    private val prestige: GuildPrestigeService by inject()
    private val lang: LangService by inject()

    internal fun createGui(): ChestGui {
        val gui = ChestGui(3, lang.guiTitle("menu.guild_progression.prestige.confirmation.title"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)

        val summary = NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            ItemStack.of(Material.NETHER_STAR)
        }.also { stack -> stack.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.prestige.confirmation.summary.name"))
            meta.lore(listOf(
                lang.gui(
                    "menu.guild_progression.prestige.confirmation.summary.reward",
                    "reward" to retainedRewardName,
                ),
                lang.gui(
                    "menu.guild_progression.prestige.confirmation.summary.fee",
                    "fee" to quote.quotedFee,
                ),
                lang.gui("menu.guild_progression.prestige.confirmation.summary.reset"),
                lang.gui("menu.guild_progression.prestige.confirmation.summary.permanent"),
            ))
        } }
        pane.addItem(GuiItem(summary) { it.isCancelled = true }, 4, 0)

        val confirm = NexoItemProvider.getItemStackOrFallback("lg_confirm") {
            ItemStack.of(Material.LIME_DYE)
        }.name(lang.gui("menu.guild_progression.prestige.confirmation.confirm"))
        pane.addItem(GuiItem(confirm) { event ->
            event.isCancelled = true
            if (!player.isOnline) return@GuiItem
            val result = prestige.confirm(player.uniqueId, quote)
            player.sendMessage(feedback(result))
            when (result) {
                is PrestigeResult.Applied -> success()
                is PrestigeResult.Rejected -> back()
                is PrestigeResult.Failed -> {
                    // Keep this exact quote live so retrying remains idempotent.
                    open()
                }
            }
        }, 3, 1)

        val cancel = NexoItemProvider.getItemStackOrFallback("lg_cancel") {
            ItemStack.of(Material.RED_DYE)
        }.name(lang.gui("menu.guild_progression.prestige.confirmation.cancel"))
        pane.addItem(GuiItem(cancel) { event ->
            event.isCancelled = true
            back()
        }, 5, 1)

        gui.addPane(pane)
        return gui
    }

    override fun open() {
        if (player.isOnline) createGui().show(player)
    }

    private fun feedback(result: PrestigeResult) = when (result) {
        is PrestigeResult.Applied -> lang.msg(
            "menu.guild_progression.prestige.feedback.applied",
            "reward" to retainedRewardName,
            "fee" to result.fee,
            "count" to result.prestigeCount,
        )
        is PrestigeResult.Failed ->
            lang.msg("menu.guild_progression.prestige.feedback.failed")
        is PrestigeResult.Rejected -> when (result.reason) {
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
    }
}
