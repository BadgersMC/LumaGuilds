package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim icon menu using Cumulus CustomForm
 * Allows changing the claim's display icon
 */
class BedrockClaimIconMenu(
    player: Player,
    menuNavigator: MenuNavigator,
    private val claim: Claim?,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val claimRepository: ClaimRepository by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        if (claim == null) {
            return CustomForm.builder()
                .title(bedrockLocalization.getBedrockString(player, "claim.icon.title"))
                .label(bedrockLocalization.getBedrockString(player, "claim.icon.error"))
                .validResultHandler { _ -> bedrockNavigator.goBack() }
                .build()
        }

        val currentIcon = claim.icon
        val commonIcons = listOf("GRASS_BLOCK", "OAK_LOG", "STONE", "DIAMOND_BLOCK", "GOLD_BLOCK", "EMERALD_BLOCK")

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.icon.title")} - ${claim.name}")
            .label(bedrockLocalization.getBedrockString(player, "claim.icon.current", currentIcon))
            .dropdown(
                bedrockLocalization.getBedrockString(player, "claim.icon.select"),
                commonIcons,
                commonIcons.indexOfFirst { it == currentIcon }.coerceAtLeast(0)
            )
            .input(
                bedrockLocalization.getBedrockString(player, "claim.icon.custom"),
                bedrockLocalization.getBedrockString(player, "claim.icon.custom.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val selectedIndex = response.asDropdown(2)
                val customInput = response.asInput(3)?.trim()?.uppercase() ?: ""

                val newIcon = if (customInput.isNotEmpty()) {
                    // Validate custom icon
                    try {
                        Material.valueOf(customInput)
                        customInput
                    } catch (e: IllegalArgumentException) {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.icon.invalid"))
                        bedrockNavigator.goBack()
                        return@validResultHandler
                    }
                } else {
                    commonIcons.getOrNull(selectedIndex) ?: currentIcon
                }

                if (newIcon != currentIcon) {
                    claimRepository.update(claim.copy(icon = newIcon))
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.icon.updated", newIcon))
                }
                bedrockNavigator.goBack()
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
