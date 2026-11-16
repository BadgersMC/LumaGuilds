package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.HallThemeText
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** REQ-260: the Bedrock form for choosing the Hall theme a prestige earns. */
class BedrockPrestigeThemeMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val choices: List<HallTheme>,
    private val back: () -> Unit,
    private val chosen: (HallTheme) -> Unit,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        var builder = SimpleForm.builder()
            .title(lang.bedrock("hall.pick.title"))
            .content(lang.bedrock("hall.pick.bedrock_content"))
        choices.forEach { theme ->
            builder = builder.button(lang.bedrock("hall.pick.name", "theme" to theme.label) + "\n" + HallThemeText.bedrockCosmetics(lang, theme))
        }
        builder = builder.button(lang.bedrock("hall.pick.back"))
        return builder
            .validResultHandler { response ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (!player.isOnline) return@Runnable
                    choices.getOrNull(response.clickedButtonId())?.let(chosen) ?: back()
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFormResponseReceived()
                    if (player.isOnline) back()
                })
            }
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) = Unit
}
