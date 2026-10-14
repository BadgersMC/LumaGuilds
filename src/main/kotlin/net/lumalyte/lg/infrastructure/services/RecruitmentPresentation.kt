package net.lumalyte.lg.infrastructure.services

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
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

    fun input(player: Player, fields: RecruitmentTextInput, actions: RecruitmentInputActions) {
        val options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()

        fun action(callback: (String?) -> Unit): DialogAction {
            return DialogAction.customClick({ response, audience ->
                if (audience is Player && audience.uniqueId == player.uniqueId) {
                    Bukkit.getScheduler().runTask(
                        net.lumalyte.lg.common.PluginKeys.getPlugin(),
                        Runnable {
                            if (player.isOnline && actions.valid()) callback(response.getText("answer"))
                        },
                    )
                }
            }, options)
        }
        player.showDialog(
            Dialog.create { factory ->
                factory.empty().base(
                    DialogBase.builder(Component.text(fields.title)).canCloseWithEscape(true)
                        .inputs(
                            listOf(
                                DialogInput.text("answer", Component.text(fields.title)).initial(fields.initial)
                                    .maxLength(MAX_INPUT_LENGTH)
                                    .build(),
                            ),
                        )
                        .build(),
                )
                    .type(
                        DialogType.confirmation(
                            ActionButton.builder(
                                Component.text(fields.submitLabel),
                            )
                                .action(action { actions.accept(it ?: "") })
                                .build(),
                            ActionButton.builder(
                                Component.text(fields.cancelLabel),
                            )
                                .action(action { actions.cancel() })
                                .build(),
                        ),
                    )
            },
        )
    }

    fun book(player: Player, content: RecruitmentBookContent, valid: () -> Boolean, back: () -> Unit) {
        if (PLATFORMS.isBedrockPlayer(player)) {
            bedrockBook(player, content, valid, back)
        } else {
            javaBook(player, content, valid, back)
        }
    }

    private fun bedrockBook(player: Player, content: RecruitmentBookContent, valid: () -> Boolean, back: () -> Unit) {
        val form =
            org.geysermc.cumulus.form.SimpleForm.builder().title(content.title)
                .content(content.lines.joinToString("\n"))
                .button(content.returnLabel)
                .validResultHandler { _ -> returnToMenu(player, valid, back) }
                .build()
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.uniqueId, form)
    }

    private fun javaBook(player: Player, content: RecruitmentBookContent, valid: () -> Boolean, back: () -> Unit) {
        val options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()
        val returnLink =
            Component.text(content.returnLabel).clickEvent(
                ClickEvent.callback({ audience ->
                    if (audience is Player && audience.uniqueId == player.uniqueId) returnToMenu(player, valid, back)
                }, options),
            )
        val pages = bookPages(content.lines, returnLink)
        player.closeInventory()
        player.openBook(Book.book(Component.text(content.title), Component.text("LumaGuilds"), pages))
    }

    private fun returnToMenu(player: Player, valid: () -> Boolean, back: () -> Unit) {
        Bukkit.getScheduler().runTask(
            net.lumalyte.lg.common.PluginKeys.getPlugin(),
            Runnable { if (player.isOnline && valid()) back() },
        )
    }

    /** Retains complete validated text and wraps at Unicode code-point boundaries. */
    internal fun bookPages(lines: List<String>, returnLink: Component): List<Component> {
        val rows =
            lines.flatMap { value ->
                value.lines().flatMap { line ->
                    line.codePoints().toArray().toList().chunked(BOOK_COLUMNS).map { points ->
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
