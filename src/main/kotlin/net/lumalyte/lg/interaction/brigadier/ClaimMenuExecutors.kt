package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.CommandSafeExecutor
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Execution handlers for claimmenu Brigadier command.
 * Opens the claim management GUI.
 */
object ClaimMenuExecutors : KoinComponent {

    private val messageService: MessageService by inject()
    private val menuFactory: MenuFactory by inject()
    private val menuNavigator: MenuNavigator by inject()

    /**
     * Opens the claim management menu for the player.
     */
    fun openMenu(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender

        val success = CommandSafeExecutor.execute(sender, "claimmenu") {
            if (sender !is Player) {
                messageService.renderSystem("command.player_only").let { sender.sendMessage(it) }
                return@execute
            }

            // Get the player's current claim location
            val location = sender.location
            val world = location.world

            // TODO: Get claim at player's location
            // For now, we'll show a message that no claim is found
            messageService.renderSystem("claim.menu.no_claim_at_location").let { sender.sendMessage(it) }

            // When claim system is implemented, uncomment:
            /*
            val claim = claimService.getClaimAtPosition(world, location.blockX, location.blockY, location.blockZ)
            if (claim == null) {
                messageService.renderSystem("claim.menu.no_claim_at_location").let { sender.sendMessage(it) }
                return@execute
            }

            // Check if player has permission to manage this claim
            if (!claimService.canPlayerManageClaim(sender.uniqueId, claim)) {
                messageService.renderSystem("claim.menu.no_permission").let { sender.sendMessage(it) }
                return@execute
            }

            // Open the claim management menu
            val menu = menuFactory.createClaimManagementMenu(menuNavigator, sender, claim)
            menuNavigator.openMenu(menu)
            */
        }

        return if (success) 1 else 0
    }
}
