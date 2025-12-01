package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val hasBanner = guild.banner != null

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.banner.title")} - ${guild.name}")
            .content(buildBannerContent(hasBanner))
            .apply {
                if (hasBanner) {
                    button(bedrockLocalization.getBedrockString(player, "guild.banner.clear"))
                }
                button(bedrockLocalization.getBedrockString(player, "guild.banner.instructions"))
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
            """
            |${bedrockLocalization.getBedrockString(player, "guild.banner.current.set")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.banner.bedrock.note")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.banner.change.instructions")}:
            |1. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.1")}
            |2. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.2")}
            |3. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.3")}
            """.trimMargin()
        } else {
            """
            |${bedrockLocalization.getBedrockString(player, "guild.banner.none")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.banner.bedrock.note")}
            |
            |${bedrockLocalization.getBedrockString(player, "guild.banner.set.instructions")}:
            |1. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.1")}
            |2. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.2")}
            |3. ${bedrockLocalization.getBedrockString(player, "guild.banner.step.3")}
            """.trimMargin()
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
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.banner.cleared"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.banner.clear.failed"))
        }
    }

    private fun showInstructions() {
        player.sendMessage(
            """
            |${bedrockLocalization.getBedrockString(player, "guild.banner.detailed.instructions")}
            |
            |1. ${bedrockLocalization.getBedrockString(player, "guild.banner.detail.1")}
            |2. ${bedrockLocalization.getBedrockString(player, "guild.banner.detail.2")}
            |3. ${bedrockLocalization.getBedrockString(player, "guild.banner.detail.3")}
            |4. ${bedrockLocalization.getBedrockString(player, "guild.banner.detail.4")}
            """.trimMargin()
        )
        bedrockNavigator.goBack()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
