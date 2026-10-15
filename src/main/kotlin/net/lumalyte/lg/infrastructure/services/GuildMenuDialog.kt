package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.common.PluginKeys
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration

internal data class GuildDialogItem(val item: ItemStack, val text: Component)

internal data class GuildDialogAction(val label: Component, val tooltip: Component, val action: () -> Unit)

internal data class GuildDialogPage(
    val title: Component,
    val items: List<GuildDialogItem>,
    val actions: List<GuildDialogAction>,
    val back: GuildDialogAction,
    val columns: Int = 2,
)

/** Uses public Paper dialog APIs; Nexo item models remain owned by the private pack. */
internal fun interface GuildDialogPresenter {
    fun show(player: Player, page: GuildDialogPage, valid: () -> Boolean, failure: () -> Unit): Boolean
}

internal class GuildMenuDialog : GuildDialogPresenter {
    private var session: GuildDialogSession? = null

    @Suppress("TooGenericExceptionCaught") // Rendering failure retains the existing inventory fallback.
    override fun show(player: Player, page: GuildDialogPage, valid: () -> Boolean, failure: () -> Unit): Boolean {
        session?.close()
        var inventory: org.bukkit.inventory.Inventory? = null
        val current = GuildDialogSession(player.uniqueId, LIFETIME.toMillis()) {
            player.isOnline && inventory != null && player.openInventory.topInventory === inventory && valid()
        }
        session = current
        val revision = current.beginPage(System.currentTimeMillis())
        return try {
            player.showDialog(Dialog.create { factory ->
                factory.empty().base(
                    DialogBase.builder(page.title.color(NamedTextColor.GOLD))
                        .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(page.items.map {
                            DialogBody.item(it.item.clone()).width(24).height(32)
                                .description(DialogBody.plainMessage(it.text, 280)).build()
                        }).build(),
                ).type(
                    DialogType.multiAction(
                        page.actions.map { button(player, it, current, revision, failure) },
                        button(player, page.back, current, revision, failure),
                        page.columns,
                    ),
                )
            })
            inventory = player.openInventory.topInventory
            true
        } catch (error: Exception) {
            current.close()
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Guild dialog rendering failed", error)
            false
        }
    }

    private fun button(
        player: Player,
        action: GuildDialogAction,
        current: GuildDialogSession,
        revision: Int,
        failure: () -> Unit,
    ): ActionButton {
        return ActionButton.builder(action.label).width(180).tooltip(action.tooltip).action(
            DialogAction.customClick({ _, audience ->
                if (audience is Player && audience.uniqueId == player.uniqueId) {
                    Bukkit.getScheduler().runTask(PluginKeys.getPlugin(), Runnable {
                        dispatch(audience, current, revision, action.action, failure)
                    })
                }
            }, ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build()),
        ).build()
    }

    @Suppress("TooGenericExceptionCaught") // Callback boundary fails closed instead of exposing private data.
    private fun dispatch(
        actor: Player,
        current: GuildDialogSession,
        revision: Int,
        action: () -> Unit,
        failure: () -> Unit,
    ) {
        try {
            if (current.consume(actor.uniqueId, revision, System.currentTimeMillis())) action()
        } catch (error: Exception) {
            current.close()
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Guild dialog action failed for {}", actor.uniqueId, error)
            failure()
        }
    }

    private companion object {
        val LIFETIME: Duration = Duration.ofMinutes(2)
    }
}
