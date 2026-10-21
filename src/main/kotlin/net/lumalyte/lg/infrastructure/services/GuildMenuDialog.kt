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

private const val ITEM_WIDTH = 16
private const val ITEM_HEIGHT = 16
private const val TEXT_WIDTH = 280
private const val ACTION_WIDTH = 180
// FocusableTextWidget subtracts 4px per side from description.maxWidth.
// Preserve the intended text area after that inset so 257px card advances fit.
internal const val GUILD_DIALOG_ITEM_DESCRIPTION_WIDTH = TEXT_WIDTH - ITEM_WIDTH - 4 + 8
internal val GUILD_DIALOG_CLOSE_LABEL = Component.translatable("gui.close").fallback("Close")

internal data class GuildDialogItem(
    val item: ItemStack,
    val text: Component,
    val cardLines: List<Component>? = null,
    val onClick: GuildDialogAction? = null,
    val rightLines: List<Component>? = null,
    val section: Component? = null,
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
    val subtitle: Component = Component.empty(),
    val bodyActions: List<GuildDialogAction> = emptyList(),
    val showCategoryInput: Boolean = true,
    val framedHeading: Boolean = false,
    val diagram: List<List<GuildDialogAction>> = emptyList(),
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
    private val epoch = GuildMenuLifecycle.generation(player)
    private val token = epoch.get()
    private var presentation = -1L
    val session =
        GuildDialogSession(player.uniqueId, Duration.ofMinutes(2).toMillis()) {
            player.isOnline && epoch.get() == token && presentation == GuildDialogTransitions.revision(player) &&
                inventory != null && player.openInventory.topInventory === inventory && valid()
        }

    fun captureInventory() {
        inventory = player.openInventory.topInventory
        presentation = GuildDialogTransitions.revision(player)
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
        return GuildDialogTransitions.present(
            player,
            { session === current && valid() },
            {
                try {
                    val skin = GuildDialogTheme().resolve(page.theme)
                    player.showDialog(createDialog(page, GuildDialogCallbacks(player, current, revision, failure), skin))
                    GuildDialogTransitions.shown(player)
                    audience.captureInventory()
                    true
                } catch (error: Exception) {
                    renderFailed(current, error)
                }
            },
            failure,
        )
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
                    listOfNotNull(page.search?.let { searchButton(it, callbacks, skin, page.showCategoryInput) }) +
                        page.actions.map { button(it, callbacks, skin, page.search != null) } + button(page.back, callbacks, skin),
                    closeButton(callbacks),
                    page.columns,
                ),
            )
        }
    }

    private fun closeButton(callbacks: GuildDialogCallbacks): ActionButton =
        ActionButton.builder(GUILD_DIALOG_CLOSE_LABEL).width(ACTION_WIDTH)
            .action(DialogAction.customClick({ _, audience -> callbacks.dismiss(audience) },
                ClickCallback.Options.builder().uses(1).lifetime(Duration.ofHours(1)).build()))
            .build()

    private fun dialogBase(
        page: GuildDialogPage,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): DialogBase {
        val links =
            if (page.bodyActions.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    DialogBody.plainMessage(
                        Component.join(
                            net.kyori.adventure.text.JoinConfiguration.separator(Component.text("  ")),
                            page.bodyActions.map { action ->
                                clickableGuildDialogCard(action.label, action) {
                                    ClickEvent.callback(
                                        { audience -> callbacks.submit(audience, it.action) },
                                        callbackOptions(),
                                    )
                                }
                            },
                        ),
                        TEXT_WIDTH,
                    ),
                )
            }
        val diagram = page.diagram.map { row ->
            val connected = row.map { action -> clickableGuildDialogCard(action.label, action) {
                ClickEvent.callback({ audience -> callbacks.submit(audience, it.action) }, callbackOptions())
            } }
            DialogBody.plainMessage(Component.join(
                net.kyori.adventure.text.JoinConfiguration.separator(Component.text("  ──  ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)),
                connected), TEXT_WIDTH)
        }
        val bodies = links + diagram + page.items.flatMap { itemBodies(it, callbacks, skin) }
        val frame = if (page.framedHeading) skin?.referenceFrame(page.title, page.subtitle) else null
        val surface = GuildDialogSurface.resolve(page.theme)?.heading(page.title, page.subtitle, bodies)
        return DialogBase.builder(if (surface != null || frame != null) Component.empty() else page.title.color(guildDialogAccent(page.theme)))
            .externalTitle(page.title)
            .canCloseWithEscape(true)
            .apply { GuildDialogTransitions.configure(this) }
            .body(
                surface?.let { listOf(DialogBody.plainMessage(it, GuildDialogSurface.WIDTH)) + bodies }
                    ?: frame?.let { listOf(DialogBody.plainMessage(it, TEXT_WIDTH)) + bodies } ?: skin?.referenceBody(page.title, page.subtitle, bodies) ?: (
                    if (page.subtitle == Component.empty()) {
                        bodies
                    } else {
                        listOf(DialogBody.plainMessage(page.subtitle, TEXT_WIDTH)) + bodies
                    }
                ),
            )
            .inputs(page.search?.inputs(page.showCategoryInput) ?: emptyList())
            .build()
    }

    private fun itemBodies(
        item: GuildDialogItem,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
    ): List<DialogBody> {
        fun linked(content: Component): Component =
            clickableGuildDialogCard(content, item.onClick) { action ->
                ClickEvent.callback({ audience -> callbacks.submit(audience, action.action) }, callbackOptions())
            }
        val lines = item.cardLines
        if (!lines.isNullOrEmpty() && item.rightLines != null) {
            val panel = skin?.columns(lines, item.rightLines)
                ?: Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines + item.rightLines)
            val row = DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
                .showDecorations(false).showTooltip(false)
                .description(DialogBody.plainMessage(linked(panel), GUILD_DIALOG_ITEM_DESCRIPTION_WIDTH)).build()
            return listOfNotNull(item.section?.let { DialogBody.plainMessage(it, TEXT_WIDTH) }, row)
        }
        if (skin != null && !lines.isNullOrEmpty()) {
            val heading =
                DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
                    .showDecorations(false).showTooltip(false)
                    .description(DialogBody.plainMessage(linked(lines.first()), GUILD_DIALOG_ITEM_DESCRIPTION_WIDTH))
                    .build()
            val content = lines.drop(1)
            if (content.isEmpty()) return listOf(heading)
            val panel =
                skin.card(content)
                    ?: Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), content)
            return listOf(heading, DialogBody.plainMessage(linked(panel), TEXT_WIDTH))
        }
        return listOf(
            DialogBody.item(item.item.clone()).width(ITEM_WIDTH).height(ITEM_HEIGHT)
                .showDecorations(false).showTooltip(false)
                .description(DialogBody.plainMessage(linked(item.text), GUILD_DIALOG_ITEM_DESCRIPTION_WIDTH))
                .build(),
        )
    }

    private fun button(
        action: GuildDialogAction,
        callbacks: GuildDialogCallbacks,
        skin: GuildDialogSkin?,
        board: Boolean = false,
    ): ActionButton {
        val width = if (board) BOARD_CONTROL_WIDTH else ACTION_WIDTH
        val fullLabel = skin?.label(action.label) ?: action.label
        return ActionButton.builder(GuildDialogText.buttonLabel(fullLabel, (width - 16) * 2))
            .width(if (board) BOARD_CONTROL_WIDTH else ACTION_WIDTH)
            .tooltip(action.label.append(Component.newline()).append(action.tooltip))
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
        showCategory: Boolean,
    ): ActionButton {
        return ActionButton.builder(skin?.label(search.submitLabel) ?: search.submitLabel)
            .width(BOARD_CONTROL_WIDTH)
            .action(searchAction(search, callbacks, showCategory))
            .build()
    }

    private fun searchAction(
        search: GuildDialogSearch,
        callbacks: GuildDialogCallbacks,
        showCategory: Boolean,
    ): DialogAction {
        return DialogAction.customClick({ response, audience ->
            if (audience is Player && audience.uniqueId == callbacks.player.uniqueId) {
                val query = response.getText(BOARD_QUERY_KEY)
                val category = if (showCategory) response.getText(BOARD_CATEGORY_KEY) else search.category
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
