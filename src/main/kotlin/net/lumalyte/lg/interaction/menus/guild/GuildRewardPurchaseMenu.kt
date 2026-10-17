package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildRewardPurchaseService
import net.lumalyte.lg.domain.rewards.RewardPurchaseRequest
import net.lumalyte.lg.domain.rewards.RewardPurchaseResult
import net.lumalyte.lg.infrastructure.i18n.*
import net.lumalyte.lg.interaction.menus.Menu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The same quote survives an uncertain result, so every retry has the same transaction ID. */
class GuildRewardPurchaseMenu(private val player: Player, private val quote: RewardPurchaseRequest,
    private val rewardName: String, private val back: () -> Unit,
    private val theme: net.lumalyte.lg.utils.GuiTheme = net.lumalyte.lg.utils.GuiTheme.DEFAULT,
    private val valid: () -> Boolean = { true }) : Menu, KoinComponent {
    private val purchases: GuildRewardPurchaseService by inject()
    private val lang: LangService by inject()

    internal fun createGui(): ChestGui {
        val gui = ChestGui(3, lang.guiTitle("chapter_two_rewards.purchase.title"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)
        val confirm = ItemStack.of(Material.LIME_DYE).also { it.editMeta { meta ->
            meta.displayName(lang.gui("chapter_two_rewards.purchase.confirm"))
            meta.lore(listOf(lang.gui("chapter_two_rewards.purchase.quote", "reward" to rewardName, "price" to quote.quotedPrice)))
        } }
        pane.addItem(GuiItem(confirm) { event ->
            event.isCancelled = true
            if (player.isOnline) {
                val result = purchases.confirm(player.uniqueId, quote)
                player.sendMessage(lang.purchaseFeedback(result))
                if (result !is RewardPurchaseResult.Failed) back()
            }
        }, 3, 1)
        val cancel = ItemStack.of(Material.RED_DYE).also { it.editMeta { meta ->
            meta.displayName(lang.gui("chapter_two_rewards.purchase.cancel"))
        } }
        pane.addItem(GuiItem(cancel) { it.isCancelled = true; back() }, 5, 1)
        gui.addPane(pane)
        return gui
    }

    private val dialog = net.lumalyte.lg.infrastructure.services.GuildMenuDialog()

    override fun open() {
        if (!player.isOnline || !valid()) return
        if (net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.supportsDialog(player)) {
            val confirm = lang.gui("chapter_two_rewards.purchase.confirm")
            val shown = dialog.show(player, net.lumalyte.lg.infrastructure.services.GuildDialogPage(
                lang.gui("chapter_two_rewards.purchase.title"),
                listOf(referenceItem("lg_reward", listOf(
                    lang.gui("chapter_two_rewards.purchase.quote", "reward" to rewardName, "price" to quote.quotedPrice),
                    net.kyori.adventure.text.Component.text(quote.transactionId.toString())))),
                listOf(referenceAction(confirm, "lg_confirm") {
                    val result = purchases.confirm(player.uniqueId, quote)
                    player.sendMessage(lang.purchaseFeedback(result))
                    if (result is RewardPurchaseResult.Failed) open() else back()
                }), referenceAction(lang.gui("chapter_two_rewards.purchase.cancel"), "lg_cancel", back), theme = theme,
            ), { player.isOnline && valid() }) { player.sendMessage(lang.msg("chapter_two_rewards.purchase.no_quote")) }
            if (shown) return
        }
        createGui().show(player)
    }
}
