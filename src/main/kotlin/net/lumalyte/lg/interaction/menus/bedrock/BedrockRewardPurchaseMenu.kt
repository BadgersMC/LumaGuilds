package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildRewardPurchaseService
import net.lumalyte.lg.domain.rewards.RewardPurchaseRequest
import net.lumalyte.lg.domain.rewards.RewardPurchaseResult
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.infrastructure.i18n.purchaseFeedback
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import org.koin.core.component.inject
import java.util.logging.Logger

class BedrockRewardPurchaseMenu(navigator: MenuNavigator, player: Player,
    private val quote: RewardPurchaseRequest, private val rewardName: String,
    private val back: () -> Unit, logger: Logger) : BaseBedrockMenu(navigator, player, logger) {
    private val purchases: GuildRewardPurchaseService by inject()
    private val lang: LangService by inject()
    private val serverPlugin: Plugin by inject()

    override fun getForm(): Form = ModalForm.builder()
        .title(lang.bedrock("chapter_two_rewards.purchase.title"))
        .content(lang.bedrock("chapter_two_rewards.purchase.quote", "reward" to rewardName, "price" to quote.quotedPrice))
        .button1(lang.bedrock("chapter_two_rewards.purchase.confirm"))
        .button2(lang.bedrock("chapter_two_rewards.purchase.cancel"))
        .validResultHandler { response ->
            val confirm = response.clickedButtonId() == 0
            Bukkit.getScheduler().runTask(serverPlugin, Runnable { respond(confirm) })
        }
        .closedOrInvalidResultHandler { _, _ ->
            Bukkit.getScheduler().runTask(serverPlugin, Runnable { respond(false) })
        }.build()

    internal fun respond(confirm: Boolean) {
        onFormResponseReceived()
        if (!player.isOnline) return
        if (!confirm) { back(); return }
        val result = purchases.confirm(player.uniqueId, quote)
        player.sendMessage(lang.purchaseFeedback(result))
        if (result is RewardPurchaseResult.Failed) open() else back()
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}
