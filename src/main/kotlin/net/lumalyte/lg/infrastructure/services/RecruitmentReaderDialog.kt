package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import java.time.Duration

/** Java-native reader shell; callbacks retain the guarded navigation session. */
internal class RecruitmentReaderDialog(
    private val player: Player,
    private val content: RecruitmentBookContent,
    private val page: RecruitmentReaderPage,
    private val actions: RecruitmentReaderActions,
) {
    private fun heading(): Component {
        return Component.text(
            "${content.title} ${page.number}/${page.count}",
            content.accent(),
        )
    }

    fun show() {
        fun button(label: String, action: () -> Unit): ActionButton {
            return ActionButton.builder(Component.text(label)).action(
                DialogAction.customClick({ _, audience ->
                    if (audience is Player && audience.uniqueId == player.uniqueId) action()
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()),
            )
                .build()
        }
        val buttons =
            buildList {
                actions.previous?.let { add(button(actions.previousLabel, it)) }
                actions.next?.let { add(button(actions.nextLabel, it)) }
            }
        val exit = button(content.returnLabel, actions.back)
        player.showDialog(
            Dialog.create { factory ->
                factory.empty().base(
                    dialogBase(),
                ).type(if (buttons.isEmpty()) DialogType.notice(exit) else DialogType.multiAction(buttons, exit, 1))
            },
        )
    }

    private fun dialogBase(): DialogBase {
        return DialogBase.builder(heading()).canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(dialogBody())
            .build()
    }

    private fun dialogBody(): List<DialogBody> {
        return buildList {
            content.guild?.let {
                val banner = net.lumalyte.lg.interaction.menus.GuildBannerItemResolver.resolveForDisplay(it)
                add(DialogBody.item(banner).description(DialogBody.plainMessage(Component.text(it.name))).build())
            }
            if (content.context.isNotBlank()) {
                add(DialogBody.plainMessage(Component.text(content.context).color(content.accent())))
            }
            add(DialogBody.plainMessage(Component.text(page.text)))
        }
    }

}
