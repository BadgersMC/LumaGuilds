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

/** Native presentation only; all input and private reads are reauthorized by the caller. */
internal object RecruitmentPresentation : org.koin.core.component.KoinComponent {
    private val platforms: net.lumalyte.lg.application.services.PlatformDetectionService by inject()

    fun supportsDialog(player: Player): Boolean = Bukkit.getPluginManager().isPluginEnabled("packetevents") &&
        PacketEvents.getAPI().playerManager.getUser(player)?.clientVersion
            ?.isNewerThanOrEquals(ClientVersion.V_1_21_6) == true

    fun input(
        player: Player,
        title: String,
        initial: String,
        submitLabel: String,
        cancelLabel: String,
        valid: () -> Boolean,
        accept: (String) -> Unit,
        cancel: () -> Unit,
    ) {
        val options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()

        fun action(callback: (String?) -> Unit) = DialogAction.customClick({ response, audience ->
            if (audience is Player && audience.uniqueId == player.uniqueId) {
                Bukkit.getScheduler().runTask(
                    net.lumalyte.lg.common.PluginKeys.getPlugin(),
                    Runnable {
                        if (player.isOnline && valid()) callback(response.getText("answer"))
                    },
                )
            }
        }, options)
        player.showDialog(
            Dialog.create { factory ->
                factory.empty().base(
                    DialogBase.builder(Component.text(title)).canCloseWithEscape(true)
                        .inputs(
                            listOf(
                                DialogInput.text("answer", Component.text(title)).initial(initial)
                                    .maxLength(2000).build(),
                            ),
                        ).build(),
                )
                    .type(
                        DialogType.confirmation(
                            ActionButton.builder(
                                Component.text(submitLabel),
                            ).action(action { accept(it ?: "") }).build(),
                            ActionButton.builder(Component.text(cancelLabel)).action(action { cancel() }).build(),
                        ),
                    )
            },
        )
    }

    fun book(
        player: Player,
        title: String,
        lines: List<String>,
        backLabel: String,
        valid: () -> Boolean,
        back: () -> Unit,
    ) {
        if (platforms.isBedrockPlayer(player)) {
            org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(
                player.uniqueId,
                org.geysermc.cumulus.form.SimpleForm.builder().title(title).content(lines.joinToString("\n"))
                    .button(backLabel).validResultHandler { _ ->
                        Bukkit.getScheduler().runTask(
                            net.lumalyte.lg.common.PluginKeys.getPlugin(),
                            Runnable {
                                if (player.isOnline && valid()) back()
                            },
                        )
                    }.build(),
            )
            return
        }
        val options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build()
        val returnLink =
            Component.text(backLabel).clickEvent(
                ClickEvent.callback({ audience ->
                    if (audience is Player && audience.uniqueId == player.uniqueId) {
                        Bukkit.getScheduler().runTask(
                            net.lumalyte.lg.common.PluginKeys.getPlugin(),
                            Runnable {
                                if (player.isOnline && valid()) back()
                            },
                        )
                    }
                }, options),
            )
        val pages =
            lines.flatMap { it.chunked(24) }.chunked(10).take(30).map { page ->
                Component.text(page.joinToString("\n")).append(Component.newline()).append(returnLink)
            }.ifEmpty { listOf(returnLink) }
        player.closeInventory()
        player.openBook(Book.book(Component.text(title), Component.text("LumaGuilds"), pages))
    }
}
