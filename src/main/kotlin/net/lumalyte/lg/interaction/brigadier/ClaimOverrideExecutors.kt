package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.application.services.PlayerMetadataService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Execution handlers for claimoverride Brigadier commands.
 * Manages claim override mode for players.
 */
object ClaimOverrideExecutors : KoinComponent {

    private val messageService: MessageService by inject()
    private val playerMetadataService: PlayerMetadataService by inject()

    /**
     * Enables claim override for self (default behavior).
     */
    fun enable(context: CommandContext<CommandSourceStack>): Int {
        return enableSelf(context)
    }

    /**
     * Enables claim override for self.
     */
    fun enableSelf(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement claim override enable for self
        return LegacyCommandRouter.routeToLegacy(context, "claimoverride enable self")
    }

    /**
     * Enables claim override for others (admin functionality).
     */
    fun enableOthers(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement claim override enable for others
        return LegacyCommandRouter.routeToLegacy(context, "claimoverride enable others")
    }

    /**
     * Enables claim override with a reason.
     */
    fun enableWithReason(context: CommandContext<CommandSourceStack>): Int {
        val reason = StringArgumentType.getString(context, "reason")
        // TODO: Implement claim override enable with reason
        return LegacyCommandRouter.routeToLegacy(context, "claimoverride enable", arrayOf(reason))
    }

    /**
     * Disables claim override.
     */
    fun disable(context: CommandContext<CommandSourceStack>): Int {
        // TODO: Implement claim override disable
        return LegacyCommandRouter.routeToLegacy(context, "claimoverride disable")
    }
}
