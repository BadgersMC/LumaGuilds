package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.word
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for claim-related commands.
 * Handles trust, flags, partitions, and other claim management operations.
 */
object ClaimRootTree {

    /**
     * Registers the claim command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("claim")
                .requires(PermissionGuard.requires("lumaguilds.command.claim"))
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("trust")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                                .suggests(SuggestionProviders.trustablePlayers())
                                .executes(ClaimExecutors::trust)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("untrust")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                                .suggests(SuggestionProviders.trustablePlayers())
                                .executes(ClaimExecutors::untrust)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("trustall")
                        .executes(ClaimExecutors::trustAll)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("untrustall")
                        .executes(ClaimExecutors::untrustAll)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("trustlist")
                        .executes(ClaimExecutors::trustList)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("addflag")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("flag", word())
                                .suggests(SuggestionProviders.flags())
                                .executes(ClaimExecutors::addFlag)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("removeflag")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("flag", word())
                                .suggests(SuggestionProviders.flags())
                                .executes(ClaimExecutors::removeFlag)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("description")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("text", greedyString())
                                .executes(ClaimExecutors::setDescription)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("info")
                        .executes(ClaimExecutors::info)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("rename")
                        .then(
                            com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", greedyString())
                                .executes(ClaimExecutors::rename)
                        )
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("remove")
                        .executes(ClaimExecutors::remove)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("partitions")
                        .then(
                            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("list")
                                .executes(ClaimExecutors::partitionsList)
                        )
                        .then(
                            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("add")
                                .then(
                                    com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                                        .executes(ClaimExecutors::partitionsAdd)
                                )
                        )
                        .then(
                            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("remove")
                                .then(
                                    com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                                        .suggests(SuggestionProviders.partitions())
                                        .executes(ClaimExecutors::partitionsRemove)
                                )
                        )
                )
        )
    }
}
