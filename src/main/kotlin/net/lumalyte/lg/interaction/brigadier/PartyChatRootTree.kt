package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for party chat functionality.
 * Handles toggling party chat mode and sending messages.
 * Supports aliases: pchat, partychat
 */
object PartyChatRootTree {

    /**
     * Registers the party chat command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("pc")
            .requires(PermissionGuard.requiresPlayer())

        // Main command: pc
        buildTree(root)
        dispatcher.register(root)

        // Alias: pchat
        val pchatAlias = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("pchat")
            .requires(PermissionGuard.requiresPlayer())
        buildTree(pchatAlias)
        dispatcher.register(pchatAlias)

        // Alias: partychat
        val partychatAlias = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("partychat")
            .requires(PermissionGuard.requiresPlayer())
        buildTree(partychatAlias)
        dispatcher.register(partychatAlias)
    }

    private fun buildTree(builder: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        builder
            .executes(PartyChatExecutors::toggle) // Default: toggle party chat
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("toggle")
                    .executes(PartyChatExecutors::toggle)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("on")
                    .executes(PartyChatExecutors::enable)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("off")
                    .executes(PartyChatExecutors::disable)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("msg")
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("message", greedyString())
                            .executes(PartyChatExecutors::sendMessage)
                    )
            )
    }
}
