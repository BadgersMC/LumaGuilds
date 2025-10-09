package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.MessageService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Execution handlers for party chat Brigadier commands.
 * Manages party chat toggling and message sending.
 */
object PartyChatExecutors : KoinComponent {

    private val chatService: ChatService by inject()
    private val messageService: MessageService by inject()

    /**
     * Toggles party chat mode for the player.
     */
    fun toggle(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement party chat toggle
        return LegacyCommandRouter.routeToLegacy(context, "pc toggle")
    }

    /**
     * Enables party chat mode for the player.
     */
    fun enable(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement party chat enable
        return LegacyCommandRouter.routeToLegacy(context, "pc on")
    }

    /**
     * Disables party chat mode for the player.
     */
    fun disable(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement party chat disable
        return LegacyCommandRouter.routeToLegacy(context, "pc off")
    }

    /**
     * Sends a message to party chat without toggling the mode.
     */
    fun sendMessage(context: CommandContext<CommandSourceStack>): Int {
        val message = StringArgumentType.getString(context, "message")
        // TODO: Implement party chat message sending
        return LegacyCommandRouter.routeToLegacy(context, "pc msg", arrayOf(message))
    }
}
