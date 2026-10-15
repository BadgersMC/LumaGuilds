package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
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
    val onClick: GuildDialogAction? = null,
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

    private fun renderFailed(
        current: GuildDialogSession,
        error: Exception,
    ): Boolean {
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
            factory.empty().base(dialogBase(page, callbacks, skin)).type(
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
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): DialogBase {
        val bodies = page.items.map { itemBody(it, callbacks, skin) }
        return DialogBase.builder(page.title.color(guildDialogAccent(page.theme)))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(skin?.body(bodies) ?: bodies)
            .inputs(page.search?.inputs() ?: emptyList())
            .build()
    }

    private fun itemBody(
        item: GuildDialogItem,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): DialogBody {
        val description = item.cardLines?.let { skin?.card(it) } ?: item.text
        val linked =
            clickableGuildDialogCard(description, item.onClick) { action ->
                ClickEvent.callback({ audience -> callbacks.submit(audience, action.action) }, callbackOptions())
            }
        return DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
            .showDecorations(false)
            .showTooltip(false)
            .description(DialogBody.plainMessage(linked, TEXT_WIDTH))
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
                    callbacks.submit(audience, action.action)
                }, callbackOptions()),
            )
            .build()
    }

    private fun searchButton(
        search: GuildDialogSearch,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): ActionButton {
        return ActionButton.builder(skin?.label(search.submitLabel) ?: search.submitLabel)
            .width(BOARD_CONTROL_WIDTH)
            .action(searchAction(search, callbacks))
            .build()
    }

    private fun searchAction(
        search: GuildDialogSearch,
        callbacks: GuildDialogCallbacks,
    ): DialogAction {
        return DialogAction.customClick({ response, audience ->
            if (audience is Player && audience.uniqueId == callbacks.player.uniqueId) {
                val query = response.getText(BOARD_QUERY_KEY)
                val category = response.getText(BOARD_CATEGORY_KEY)
                callbacks.submit(audience) {
                    if (!search.accept(query, category)) callbacks.failure()
                }
            }
        }, callbackOptions())
    }

    private fun callbackOptions(): ClickCallback.Options =
        ClickCallback.Options.builder().uses(
            1,
        ).lifetime(LIFETIME).build()

    private companion object {
        val LIFETIME: Duration = Duration.ofMinutes(2)
    }
}
