package net.lumalyte.lg.infrastructure.services

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.inject
import java.time.Duration

private const val MAX_INPUT_LENGTH = 2_000

/** Native presentation only; all input and private reads are reauthorized by the caller. */
internal object RecruitmentPresentation : org.koin.core.component.KoinComponent {
    private const val BOOK_COLUMNS = 12
    private const val BOOK_ROWS = 12
    private const val MAX_BOOK_PAGES = 100
    private val PLATFORMS: net.lumalyte.lg.application.services.PlatformDetectionService by inject()

    fun supportsDialog(player: Player): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled("packetevents") &&
            PacketEvents.getAPI().playerManager.getUser(player)?.clientVersion
                ?.isNewerThanOrEquals(ClientVersion.V_1_21_6) == true
    }

    fun input(
        player: Player,
        fields: RecruitmentTextInput,
        actions: RecruitmentInputActions,
    ) {
        val epoch = GuildMenuLifecycle.generation(player)
        val token = epoch.get()
        val skin = GuildDialogTheme().resolve(fields.theme)

        var consumed = false
        var presentation = -1L
        fun action(lifetime: Duration = Duration.ofMinutes(2), callback: (String?) -> Unit): DialogAction {
            return DialogAction.customClick({ response, audience ->
                if (audience is Player && audience.uniqueId == player.uniqueId) {
                    Bukkit.getScheduler().runTask(
                        net.lumalyte.lg.common.PluginKeys.getPlugin(),
                        Runnable {
                            if (!consumed && player.isOnline && epoch.get() == token &&
                                presentation == GuildDialogTransitions.revision(player) && actions.valid()) {
                                consumed = true
                                GuildDialogTransitions.dispatch(player) { callback(response.getText("answer")) }
                            }
                        },
                    )
                }
            }, ClickCallback.Options.builder().uses(1).lifetime(lifetime).build())
        }
        GuildDialogTransitions.present(player, { epoch.get() == token && actions.valid() }, {
            player.showDialog(
                Dialog.create { factory ->
                    factory.empty().base(inputBase(fields, skin))
                        .type(
                            DialogType.multiAction(listOf(
                                ActionButton.builder(
                                    skin?.label(Component.text(fields.submitLabel)) ?: Component.text(fields.submitLabel),
                                )
                                    .action(action { actions.accept(it ?: "") })
                                    .build(),
                                ActionButton.builder(
                                    skin?.label(Component.text(fields.cancelLabel)) ?: Component.text(fields.cancelLabel),
                                )
                                    .action(action { actions.cancel() })
                                    .build()),
                                ActionButton.builder(Component.translatable("gui.close"))
                                    .action(action(Duration.ofHours(1)) { GuildMenuLifecycle.dismiss(player) }).build(), 2,
                            ),
                        )
                },
            )
            GuildDialogTransitions.shown(player)
            presentation = GuildDialogTransitions.revision(player)
            true
        }, { GuildMenuLifecycle.dismiss(player) })
    }

    private fun inputBase(
        fields: RecruitmentTextInput,
        skin: GuildDialogSkin?,
    ): DialogBase =
        DialogBase.builder(Component.text(fields.title, guildDialogAccent(fields.theme)))
            .canCloseWithEscape(true)
            .apply { GuildDialogTransitions.configure(this) }
            .body(skin?.body(emptyList()) ?: emptyList())
            .inputs(
                listOf(
                    DialogInput.text("answer", Component.text(fields.title)).initial(fields.initial)
                        .maxLength(MAX_INPUT_LENGTH)
                        .build(),
                ),
            )
            .build()

    fun book(
        player: Player,
        content: RecruitmentBookContent,
        valid: () -> Boolean,
        back: () -> Unit,
    ) {
        RecruitmentReader(player, content, valid, back).open(
            PLATFORMS.isBedrockPlayer(player),
            supportsDialog(player),
        )
    }

    /** Retains complete validated text and wraps at Unicode code-point boundaries. */
    internal fun bookPages(
        lines: List<String>,
        returnLink: Component,
        columns: Int = BOOK_COLUMNS,
    ): List<Component> {
        val rows =
            lines.flatMap { value ->
                value.lines().flatMap { line ->
                    line.codePoints().toArray().toList().chunked(columns).map { points ->
                        buildString { points.forEach { appendCodePoint(it) } }
                    }
                }
            }
        val pages = rows.chunked(BOOK_ROWS)
        require(pages.size <= MAX_BOOK_PAGES) { "Book content exceeds the supported page count" }
        return pages.map { page ->
            Component.text(page.joinToString("\n")).append(Component.newline()).append(returnLink)
        }
            .ifEmpty { listOf(returnLink) }
    }
}
