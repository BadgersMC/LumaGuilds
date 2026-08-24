package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildControlPanelMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild disband confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildDisbandConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        return SimpleForm.builder()
            .title(lang.raw("bedrock.disband_confirmation.title"))
            .content(lang.legacy("bedrock.disband_confirmation.content", "guild" to guild.name, "player" to player.name))
            .button(lang.raw("bedrock.disband_confirmation.button.disband"))
            .button(lang.raw("bedrock.disband_confirmation.button.keep"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> disbandGuild()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(lang.msg("bedrock.disband_confirmation.feedback.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.disband_confirmation.feedback.cancelled"))
            })
            .build()
    }

    private fun disbandGuild() {
        // Attempt to disband the guild
        val success = guildService.disbandGuild(guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(lang.msg("bedrock.disband_confirmation.feedback.disbanded", "guild" to guild.name))
            player.sendMessage(lang.msg("bedrock.disband_confirmation.feedback.details"))

            // Clear menu stack - player no longer has a guild so close all menus
            clearMenuStack()
        } else {
            player.sendMessage(lang.msg("bedrock.disband_confirmation.feedback.failed"))
            bedrockNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}

