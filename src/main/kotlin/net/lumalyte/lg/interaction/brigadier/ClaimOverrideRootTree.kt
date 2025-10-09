package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for claim override functionality.
 * Allows enabling/disabling claim override mode with scope and reason options.
 */
object ClaimOverrideRootTree {

    /**
     * Registers the claimoverride command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("claimoverride")
                .requires(PermissionGuard.requires("lumaguilds.command.claimoverride"))
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("enable")
                        .executes(ClaimOverrideExecutors::enable) // Default: enable for self
                        .then(
                            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("self")
                                .executes(ClaimOverrideExecutors::enableSelf)
                        )
                        .then(
                            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("others")
                                .requires(PermissionGuard.requires("lumaguilds.command.claimoverride.others"))
                                .executes(ClaimOverrideExecutors::enableOthers)
                        )
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("reason", greedyString())
                                .executes(ClaimOverrideExecutors::enableWithReason)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("disable")
                        .executes(ClaimOverrideExecutors::disable)
                )
        )
    }
}
