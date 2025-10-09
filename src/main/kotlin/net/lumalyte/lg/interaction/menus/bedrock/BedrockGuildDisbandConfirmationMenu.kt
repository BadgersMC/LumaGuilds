package net.lumalyte.lg.interaction.menus.bedrock

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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bedrock Edition guild disband confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildDisbandConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    messageService: MessageService
) : BaseBedrockMenu(menuNavigator, player, logger, messageService) {

    private val guildService: GuildService by inject()

    override fun getForm(): Form {
        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.disband.confirm.title"))
            .content(bedrockLocalization.getBedrockString(player, "guild.disband.confirm.message", guild.name, player.name))
            .button(bedrockLocalization.getBedrockString(player, "guild.disband.confirm.button.disband"))
            .button(bedrockLocalization.getBedrockString(player, "guild.disband.confirm.button.keep"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> disbandGuild()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.disband.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.disband.cancelled"))
            })
            .build()
    }

    private fun disbandGuild() {
        // Attempt to disband the guild
        val success = guildService.disbandGuild(guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.disband.success", guild.name))
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.disband.success.details"))

            // TODO: Navigate to main menu or guild selection after disband
            // For now, just close the menu
            clearMenuStack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.disband.failed"))
            bedrockNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild, messageService))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}


