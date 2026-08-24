package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild banner menu using Cumulus SimpleForm
 * Note: Bedrock players need to use their inventory to set banners, this menu provides info and clear option
 */
class BedrockGuildBannerMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val hasBanner = guild.banner != null

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.banner.title", "guild" to guild.name))
            .content(buildBannerContent(hasBanner))
            .apply {
                if (hasBanner) {
                    button(lang.bedrock("bedrock.banner.button.clear"))
                }
                button(lang.bedrock("bedrock.banner.button.instructions"))
            }
            .validResultHandler { response ->
                val buttonId = response.clickedButtonId()
                handleButtonClick(buttonId, hasBanner)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun buildBannerContent(hasBanner: Boolean): String {
        return if (hasBanner) {
            lang.bedrock("bedrock.banner.content.set")
        } else {
            lang.bedrock("bedrock.banner.content.unset")
        }
    }

    private fun handleButtonClick(buttonId: Int, hasBanner: Boolean) {
        when (buttonId) {
            0 -> {
                if (hasBanner) {
                    // Clear banner
                    clearBanner()
                } else {
                    // Show instructions
                    showInstructions()
                }
            }
            1 -> {
                // Show instructions (only appears if hasBanner)
                if (hasBanner) {
                    showInstructions()
                }
            }
        }
    }

    private fun clearBanner() {
        val success = guildService.setBanner(guild.id, null, player.uniqueId)
        if (success) {
            player.sendMessage(lang.msg("bedrock.banner.feedback.cleared"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.banner.feedback.clear_failed"))
        }
    }

    private fun showInstructions() {
        player.sendMessage(lang.msg("bedrock.banner.detailed_instructions"))
        bedrockNavigator.goBack()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
