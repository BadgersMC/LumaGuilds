package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command for opening the claim management GUI.
 * Usage: /claimmenu
 */
object ClaimMenuCommand {

    /**
     * Registers the claimmenu command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("claimmenu")
                .requires(PermissionGuard.requires("lumaguilds.command.claimmenu"))
                .executes(ClaimMenuExecutors::openMenu)
        )
    }
}
