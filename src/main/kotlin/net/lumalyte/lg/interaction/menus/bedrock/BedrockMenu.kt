package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.Menu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Interface for Bedrock Edition menus using Cumulus forms
 */
interface BedrockMenu : Menu {
    /**
     * Get the built form for this menu
     * This will be implemented by concrete menu classes
     */
    fun getForm(): Form

    /**
     * Handle the form response from a Bedrock player
     * @param player The player who submitted the response
     * @param response The form response data
     */
    fun handleResponse(player: Player, response: Any?)
}
