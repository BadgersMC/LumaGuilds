package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.RecruitmentController
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Native Bedrock recruitment screens use the identical use cases and authorization as Java. */
internal class BedrockGuildRecruitmentMenu(
    navigator: MenuNavigator,
    player: Player,
    logger: Logger,
) : BaseBedrockMenu(navigator, player, logger) {
    private val controller = RecruitmentController(player, ::open)
    private val plugin: Plugin by inject()
    private val lang: LangService by inject()

    /** Forms are user-specific snapshots and must never enter the shared form cache. */
    override fun shouldCacheForm(): Boolean = false

    /** Builds a native text form or a paginated board from current authorized state. */
    override fun getForm(): Form {
        val input = controller.input
        if (input != null) {
            return CustomForm.builder().title(input.label).input(input.label, "", input.value)
                .validResultHandler { response -> onServer { controller.acceptInput(response.asInput(0) ?: "") } }
                .closedOrInvalidResultHandler { _, _ -> onServer { controller.cancelInput() } }.build()
        }
        val view = controller.view()
        val pages = maxOf(1, (view.items.size + 7) / 8)
        controller.page = controller.page.coerceIn(0, pages - 1)
        val actions = mutableListOf<() -> Unit>()
        val builder = SimpleForm.builder().title(view.title)
        (view.items.drop(controller.page * 8).take(8) + view.navigation).forEach { button ->
            builder.button((listOf(button.label) + button.details).filter(String::isNotBlank).joinToString("\n"))
            actions += button.action
        }
        if (controller.page > 0) {
            builder.button(lang.bedrock("recruitment.previous"))
            actions += {
                controller.page--
                open()
            }
        }
        if (controller.page + 1 < pages) {
            builder.button(lang.bedrock("recruitment.next"))
            actions += {
                controller.page++
                open()
            }
        }
        return builder.validResultHandler {
                response ->
            onServer { actions.getOrNull(response.clickedButtonId())?.invoke() }
        }
            .closedOrInvalidResultHandler { _, _ -> onServer { bedrockNavigator.goBack() } }.build()
    }

    private fun onServer(action: () -> Unit) {
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                if (player.isOnline) {
                    onFormResponseReceived()
                    controller.run(action)
                }
            },
        )
    }

    /** Responses are processed by the typed Cumulus handlers. */
    override fun handleResponse(
        player: Player,
        response: Any?,
    ) = Unit
}
