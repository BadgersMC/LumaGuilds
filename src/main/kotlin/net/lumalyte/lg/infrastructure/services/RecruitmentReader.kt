package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration

/** Native shells keep their client appearance; text and inventory pages retain guild context. */
internal class RecruitmentReader(
    private val player: Player,
    private val content: RecruitmentBookContent,
    valid: () -> Boolean,
    private val back: () -> Unit,
) : KoinComponent {
    private val lang: LangService by inject()
    private val state = RecruitmentReaderState { player.isOnline && valid() }
    private val pages = recruitmentReaderPages(content.lines)
    private var bedrock = false
    private var dialog = false

    fun open(isBedrock: Boolean, supportsDialog: Boolean) {
        bedrock = isBedrock
        dialog = supportsDialog
        show(0)
    }

    private fun show(page: Int) {
        val revision = state.beginPage()
        val actions = RecruitmentReaderActions(
            previousLabel = lang.raw("recruitment.previous"),
            nextLabel = lang.raw("recruitment.next"),
            previous = if (page > 0) ({ dispatch(revision) { show(page - 1) } }) else null,
            next = if (page + 1 < pages.size) ({ dispatch(revision) { show(page + 1) } }) else null,
            back = { dispatch(revision) { state.close(); back() } },
            dismissed = {
                dispatch(revision) {
                    state.close()
                    val top: org.bukkit.inventory.Inventory? = player.openInventory.topInventory
                    if (top == null || top.type == org.bukkit.event.inventory.InventoryType.CRAFTING) back()
                }
            },
        )
        when {
            bedrock -> showBedrock(page, actions)
            dialog -> showDialog(page, actions)
            else -> RecruitmentReaderInventory.show(
                player, content, RecruitmentReaderPage(pages[page], page + 1, pages.size), actions,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught") // UI boundary: adapter failures must not reveal data or reopen menus.
    private fun dispatch(revision: Int, action: () -> Unit) {
        Bukkit.getScheduler().runTask(
            net.lumalyte.lg.common.PluginKeys.getPlugin(),
            Runnable {
                try {
                    if (state.consume(revision)) action()
                } catch (error: Exception) {
                    state.close()
                    player.closeInventory()
                    org.slf4j.LoggerFactory.getLogger(javaClass)
                        .warn("Recruitment reader failed for {}", player.uniqueId, error)
                    player.sendMessage(lang.msg("recruitment.feedback.failed"))
                }
            },
        )
    }

    private fun heading(page: Int): Component = Component.text(
        "${content.title} ${page + 1}/${pages.size}",
        content.accent(),
    )

    private fun showDialog(page: Int, actions: RecruitmentReaderActions) {
        fun button(label: String, action: () -> Unit): ActionButton = ActionButton.builder(Component.text(label))
            .action(
                DialogAction.customClick({ _, audience ->
                    if (audience is Player && audience.uniqueId == player.uniqueId) action()
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()),
            ).build()
        val buttons = buildList {
            actions.previous?.let { add(button(actions.previousLabel, it)) }
            actions.next?.let { add(button(actions.nextLabel, it)) }
        }
        val exit = button(content.returnLabel, actions.back)
        player.showDialog(
            Dialog.create { factory ->
                factory.empty().base(
                    DialogBase.builder(heading(page)).canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(dialogBody(page)).build(),
                ).type(if (buttons.isEmpty()) DialogType.notice(exit) else DialogType.multiAction(buttons, exit, 1))
            },
        )
    }

    private fun dialogBody(page: Int): List<DialogBody> = buildList {
        content.guild?.let {
            val banner = net.lumalyte.lg.interaction.menus.GuildBannerItemResolver.resolveForDisplay(it)
            add(DialogBody.item(banner).description(DialogBody.plainMessage(Component.text(it.name))).build())
        }
        if (content.context.isNotBlank()) {
            add(DialogBody.plainMessage(Component.text(content.context).color(content.accent())))
        }
        add(DialogBody.plainMessage(Component.text(pages[page])))
    }

    private fun showBedrock(page: Int, actions: RecruitmentReaderActions) {
        val callbacks = mutableListOf(actions.back)
        val text = listOfNotNull(content.guild?.name, content.context.takeIf(String::isNotBlank), pages[page])
            .joinToString("\n")
        val form = org.geysermc.cumulus.form.SimpleForm.builder()
            .title("${content.title} ${page + 1}/${pages.size}")
            .content(text).button(content.returnLabel)
        actions.previous?.let { form.button(actions.previousLabel); callbacks += it }
        actions.next?.let { form.button(actions.nextLabel); callbacks += it }
        form.validResultHandler { response -> (callbacks.getOrNull(response.clickedButtonId()) ?: actions.back)() }
            .closedOrInvalidResultHandler { _, _ -> actions.back() }
        val sent = org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.uniqueId, form.build())
        if (!sent) actions.back()
    }
}

internal data class RecruitmentReaderActions(
    val previousLabel: String,
    val nextLabel: String,
    val previous: (() -> Unit)?,
    val next: (() -> Unit)?,
    val back: () -> Unit,
    val dismissed: () -> Unit,
)

internal data class RecruitmentReaderPage(val text: String, val number: Int, val count: Int)
