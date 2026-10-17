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
    private val skin = GuildDialogTheme().resolve(content.theme)

    private fun heading(): Component {
        return Component.text(
            "${content.title} ${page.number}/${page.count}",
            content.accent(),
        )
    }

    fun show() {
        val epoch = GuildMenuLifecycle.generation(player)
        val token = epoch.get()
        var presentation = -1L
        fun button(
            label: String,
            action: () -> Unit,
        ): ActionButton {
            val text = Component.text(label)
            return ActionButton.builder(skin?.label(text) ?: text).action(
                DialogAction.customClick({ _, audience ->
                    if (audience is Player && audience.uniqueId == player.uniqueId && actions.valid()) action()
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()),
            )
                .build()
        }
        val buttons =
            buildList {
                actions.previous?.let { add(button(actions.previousLabel, it)) }
                actions.next?.let { add(button(actions.nextLabel, it)) }
            }
        val back = button(content.returnLabel, actions.back)
        val exit = ActionButton.builder(Component.translatable("gui.close")).action(
            DialogAction.customClick({ _, audience ->
                if (audience is Player && audience.uniqueId == player.uniqueId) {
                    org.bukkit.Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
                        if (epoch.get() == token && presentation == GuildDialogTransitions.revision(player) && actions.valid())
                            GuildMenuLifecycle.dismiss(player)
                    })
                }
            }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofHours(1)).build()),
        ).build()
        GuildDialogTransitions.present(player, { epoch.get() == token && actions.valid() }, {
            player.showDialog(
                Dialog.create { factory ->
                    factory.empty().base(
                        dialogBase(),
                    ).type(DialogType.multiAction(buttons + back, exit, 2))
                },
            )
            GuildDialogTransitions.shown(player)
            presentation = GuildDialogTransitions.revision(player)
            actions.shown(presentation)
            true
        }, { GuildMenuLifecycle.dismiss(player) })
    }

    private fun dialogBase(): DialogBase {
        val body = dialogBody()
        val surface = GuildDialogSurface.resolve(content.theme)?.heading(heading(), Component.empty(), body)
        return DialogBase.builder(if (surface != null) Component.empty() else heading()).canCloseWithEscape(true)
            .externalTitle(heading())
            .apply { GuildDialogTransitions.configure(this) }
            .body(surface?.let { listOf(DialogBody.plainMessage(it, GuildDialogSurface.WIDTH)) + body }
                ?: skin?.body(body) ?: body)
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
