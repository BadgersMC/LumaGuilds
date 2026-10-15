package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.lumalyte.lg.common.PluginKeys
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration

private const val ITEM_WIDTH = 24
private const val ITEM_HEIGHT = 32
private const val TEXT_WIDTH = 280
private const val ACTION_WIDTH = 180

internal data class GuildDialogItem(val item: ItemStack, val text: Component)

internal data class GuildDialogAction(val label: Component, val tooltip: Component, val action: () -> Unit)

internal data class GuildDialogPage(
    val title: Component,
    val items: List<GuildDialogItem>,
    val actions: List<GuildDialogAction>,
    val back: GuildDialogAction,
    val columns: Int = 1,
    val theme: net.lumalyte.lg.utils.GuiTheme = net.lumalyte.lg.utils.GuiTheme.DEFAULT,
)

/** Uses public Paper dialog APIs; Nexo item models remain owned by the private pack. */
internal fun interface GuildDialogPresenter {
    fun show(player: Player, page: GuildDialogPage, valid: () -> Boolean, failure: () -> Unit): Boolean
}

private data class GuildDialogCallbacks(
    val player: Player,
    val session: GuildDialogSession,
    val revision: Int,
    val failure: () -> Unit,
)

private class GuildDialogAudience(private val player: Player, valid: () -> Boolean) {
    private var inventory: org.bukkit.inventory.Inventory? = null
    val session =
        GuildDialogSession(player.uniqueId, Duration.ofMinutes(2).toMillis()) {
            player.isOnline && inventory != null && player.openInventory.topInventory === inventory && valid()
        }

    fun captureInventory() {
        inventory = player.openInventory.topInventory
    }
}

internal class GuildMenuDialog : GuildDialogPresenter {
    private var session: GuildDialogSession? = null

    @Suppress("TooGenericExceptionCaught") // Rendering failure retains the existing inventory fallback.
    override fun show(player: Player, page: GuildDialogPage, valid: () -> Boolean, failure: () -> Unit): Boolean {
        session?.close()
        val audience = GuildDialogAudience(player, valid)
        val current = audience.session
        session = current
        val revision = current.beginPage(System.currentTimeMillis())
        return try {
            val skin = GuildDialogTheme().resolve(page.theme, player.hasResourcePack())
            player.showDialog(createDialog(page, GuildDialogCallbacks(player, current, revision, failure), skin))
            audience.captureInventory()
            true
        } catch (error: Exception) {
            current.close()
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Guild dialog rendering failed", error)
            false
        }
    }

    private fun createDialog(page: GuildDialogPage, callbacks: GuildDialogCallbacks, skin: GuildDialogSkin?): Dialog {
        return Dialog.create { factory ->
            factory.empty().base(dialogBase(page, skin)).type(
                DialogType.multiAction(
                    page.actions.map { button(it, callbacks, skin) },
                    button(page.back, callbacks, skin),
                    page.columns,
                ),
            )
        }
    }

    private fun dialogBase(page: GuildDialogPage, skin: GuildDialogSkin?): DialogBase {
        val bodies = page.items.map(::itemBody)
        return DialogBase.builder(page.title.color(guildDialogAccent(page.theme)))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .body(skin?.body(bodies) ?: bodies)
            .build()
    }

    private fun itemBody(item: GuildDialogItem): DialogBody {
        return DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
            .showDecorations(false)
            .showTooltip(false)
            .description(DialogBody.plainMessage(item.text, TEXT_WIDTH))
            .build()
    }

    private fun button(
        action: GuildDialogAction,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): ActionButton {
        return ActionButton.builder(skin?.label(action.label) ?: action.label)
            .width(ACTION_WIDTH)
            .tooltip(action.tooltip)
            .action(
                DialogAction.customClick({ _, audience ->
                    if (audience is Player && audience.uniqueId == callbacks.player.uniqueId) {
                        Bukkit.getScheduler().runTask(
                            PluginKeys.getPlugin(),
                            Runnable {
                                dispatch(audience, callbacks, action.action)
                            },
                        )
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build()),
            )
            .build()
    }

    @Suppress("TooGenericExceptionCaught") // Callback boundary fails closed instead of exposing private data.
    private fun dispatch(
        actor: Player,
        callbacks: GuildDialogCallbacks,
        action: () -> Unit,
    ) {
        try {
            if (callbacks.session.consume(actor.uniqueId, callbacks.revision, System.currentTimeMillis())) action()
        } catch (error: Exception) {
            callbacks.session.close()
            actor.closeDialog()
            org.slf4j.LoggerFactory.getLogger(javaClass).warn(
                "Guild dialog action failed for {}",
                actor.uniqueId,
                error,
            )
            callbacks.failure()
        }
    }

    private companion object {
        val LIFETIME: Duration = Duration.ofMinutes(2)
    }
}
