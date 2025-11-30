package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.ModalForm
import java.util.logging.Logger

/**
 * Enhanced Bedrock Edition confirmation menu using Cumulus ModalForm
 * Provides configurable title, message, localized buttons, and callback support
 */
class BedrockConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val title: String,
    message: String?,
    private val callback: () -> Unit,
    private val cancelCallback: (() -> Unit)?,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val message: String = message ?: bedrockLocalization.getBedrockString(player, "menu.confirmation.default_message")

    // Overloaded constructor for backward compatibility
    constructor(
        menuNavigator: MenuNavigator,
        player: Player,
        title: String,
        callback: () -> Unit,
        logger: Logger
    ) : this(menuNavigator, player, title, null, callback, null, logger)

    override fun getForm(): Form {
        return try {
            // Create Cumulus ModalForm with enhanced features
            ModalForm.builder()
                .title(title)
                .content(message)
                .button1(bedrockLocalization.getBedrockString(player, "menu.confirmation.item.yes.name"))
                .button2(bedrockLocalization.getBedrockString(player, "menu.confirmation.item.no.name"))
                .validResultHandler { response ->
                    // Handle response
                    if (response.clickedButtonId() == 0) { // Confirm button (Yes)
                        try {
                            callback()
                        } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                            logger.warning("Error executing confirmation callback: ${e.message}")
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "general.error"))
                        }
                    } else { // Cancel button (No)
                        try {
                            cancelCallback?.invoke()
                        } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                            logger.warning("Error executing cancel callback: ${e.message}")
                        }
                    }

                    // Navigate back
                    bedrockNavigator.goBack()
                }
                .closedOrInvalidResultHandler { result ->
                    // Handle form closure or invalid response as cancel
                    try {
                        cancelCallback?.invoke()
                    } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                        logger.warning("Error executing cancel callback on form close: ${e.message}")
                    }

                    // Show cancellation message
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "menu.confirmation.cancelled"))
                }
                .build()
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            logger.warning("Error creating Bedrock confirmation form: ${e.message}")
            throw RuntimeException("Failed to create confirmation form", e)
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is now done in the form builder's response handlers
        // This method is kept for interface compatibility but the actual handling
        // happens in the validResultHandler and closedOrInvalidResultHandler
    }

    override fun passData(data: Any?) {
        // Handle data passed from other menus
        if (data is Boolean) {
            // If data is a boolean, treat it as a direct response
            if (data) {
                try {
                    callback()
                } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                    logger.warning("Error executing confirmation callback from passData: ${e.message}")
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "general.error"))
                }
            } else {
                try {
                    cancelCallback?.invoke()
                } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                    logger.warning("Error executing cancel callback from passData: ${e.message}")
                }
            }

            bedrockNavigator.goBack()
        } else {
            super.passData(data)
        }
    }

}
