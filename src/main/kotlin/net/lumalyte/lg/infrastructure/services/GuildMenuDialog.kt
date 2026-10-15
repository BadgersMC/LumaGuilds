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

internal data class GuildDialogItem(
    val item: ItemStack,
    val text: Component,
    val cardLines: List<Component>? = null,
)

internal data class GuildDialogAction(val label: Component, val tooltip: Component, val action: () -> Unit)

internal data class GuildDialogPage(
    val title: Component,
    val items: List<GuildDialogItem>,
    val actions: List<GuildDialogAction>,
    val back: GuildDialogAction,
    val columns: Int = 1,
    val theme: net.lumalyte.lg.utils.GuiTheme = net.lumalyte.lg.utils.GuiTheme.DEFAULT,
    val search: GuildDialogSearch? = null,
)

/** Uses public Paper dialog APIs; Nexo item models remain owned by the private pack. */
internal fun interface GuildDialogPresenter {
    fun show(
        player: Player,
        page: GuildDialogPage,
        valid: () -> Boolean,
        failure: () -> Unit,
    ): Boolean
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
        val current = audience.session.also { session = it }
        val revision = current.beginPage(System.currentTimeMillis())
        return try {
            val skin = GuildDialogTheme().resolve(page.theme)
            player.showDialog(createDialog(page, GuildDialogCallbacks(player, current, revision, failure), skin))
            audience.captureInventory()
            true
        } catch (error: Exception) {
            renderFailed(current, error)
        }
    }

    private fun renderFailed(current: GuildDialogSession, error: Exception): Boolean {
        current.close()
        org.slf4j.LoggerFactory.getLogger(javaClass).warn("Guild dialog rendering failed", error)
        return false
    }

    private fun createDialog(
        page: GuildDialogPage,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): Dialog {
        return Dialog.create { factory ->
            factory.empty().base(dialogBase(page, skin)).type(
                DialogType.multiAction(
                    listOfNotNull(page.search?.let { searchButton(it, callbacks, skin) }) +
                        page.actions.map { button(it, callbacks, skin, page.search != null) },
                    button(page.back, callbacks, skin),
                    page.columns,
                ),
            )
        }
    }

    private fun dialogBase(
        page: GuildDialogPage,
        skin: GuildDialogSkin?,
    ): DialogBase {
        val bodies = page.items.map { itemBody(it, skin) }
        return DialogBase.builder(page.title.color(guildDialogAccent(page.theme)))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .body(skin?.body(bodies) ?: bodies)
            .inputs(page.search?.inputs() ?: emptyList())
            .build()
    }

    private fun itemBody(
        item: GuildDialogItem,
        skin: GuildDialogSkin?,
    ): DialogBody {
        val description = item.cardLines?.let { skin?.card(it) } ?: item.text
        return DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
            .showDecorations(false)
            .showTooltip(false)
            .description(DialogBody.plainMessage(description, TEXT_WIDTH))
            .build()
    }

    private fun button(
        action: GuildDialogAction,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
        board: Boolean = false,
    ): ActionButton {
        return ActionButton.builder(skin?.label(action.label) ?: action.label)
            .width(if (board) BOARD_CONTROL_WIDTH else ACTION_WIDTH)
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

    private fun searchButton(
        search: GuildDialogSearch,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): ActionButton =
        ActionButton.builder(skin?.label(search.submitLabel) ?: search.submitLabel)
            .width(BOARD_CONTROL_WIDTH)
            .action(
                DialogAction.customClick({ response, audience ->
                    if (audience is Player && audience.uniqueId == callbacks.player.uniqueId) {
                        val query = response.getText(BOARD_QUERY_KEY)
                        val category = response.getText(BOARD_CATEGORY_KEY)
                        Bukkit.getScheduler().runTask(
                            PluginKeys.getPlugin(),
                            Runnable {
                                dispatch(audience, callbacks) {
                                    if (!search.accept(query, category)) callbacks.failure()
                                }
                            },
                        )
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build()),
            )
            .build()

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
