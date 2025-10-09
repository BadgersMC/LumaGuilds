package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command for listing claims with optional pagination.
 * Usage: /claimlist [page]
 */
object ClaimListCommand {

    /**
     * Registers the claimlist command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("claimlist")
                .requires(PermissionGuard.requires("lumaguilds.command.claimlist"))
                .executes(ClaimListExecutors::list) // Default: show first page
                .then(
                    com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, Int>("page", IntegerArgumentType.integer(1))
                        .executes(ClaimListExecutors::listPage)
                )
        )
    }
}
