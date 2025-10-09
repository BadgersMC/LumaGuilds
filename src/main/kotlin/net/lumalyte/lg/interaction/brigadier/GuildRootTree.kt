package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.word
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for guild functionality.
 * Handles all guild management operations.
 * Supports alias: g
 */
object GuildRootTree {

    /**
     * Registers the guild command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("guild")
            .requires(PermissionGuard.requiresPlayer())

        buildTree(root)
        dispatcher.register(root)

        // Alias: g
        val gAlias = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("g")
            .requires(PermissionGuard.requiresPlayer())
        buildTree(gAlias)
        dispatcher.register(gAlias)
    }

    private fun buildTree(builder: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        builder
            // === Core Guild Management Commands ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("create")
                    .requires(PermissionGuard.requires("lumaguilds.guild.create"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                            .executes(GuildExecutors::createGuild)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("disband")
                    .requires(PermissionGuard.requires("lumaguilds.guild.disband"))
                    .executes(GuildExecutors::disbandGuild)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("join")
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("guild", word())
                            .suggests(SuggestionProviders.guilds())
                            .executes(GuildExecutors::joinGuild)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("leave")
                    .executes(GuildExecutors::leaveGuild)
            )
            
            // === Member Management Commands ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("invite")
                    .requires(PermissionGuard.requires("lumaguilds.guild.invite"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                            .suggests(SuggestionProviders.players())
                            .executes(GuildExecutors::invitePlayer)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("kick")
                    .requires(PermissionGuard.requires("lumaguilds.guild.kick"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                            .suggests(SuggestionProviders.guildMembers())
                            .executes(GuildExecutors::kickPlayer)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("promote")
                    .requires(PermissionGuard.requires("lumaguilds.guild.promote"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                            .suggests(SuggestionProviders.guildMembers())
                            .executes(GuildExecutors::promotePlayer)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("demote")
                    .requires(PermissionGuard.requires("lumaguilds.guild.demote"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", word())
                            .suggests(SuggestionProviders.guildMembers())
                            .executes(GuildExecutors::demotePlayer)
                    )
            )
            
            // === Guild Configuration Commands ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("home")
                    .requires(PermissionGuard.requires("lumaguilds.guild.home"))
                    .executes(GuildExecutors::teleportHome)
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                            .suggests(SuggestionProviders.guildHomes())
                            .executes(GuildExecutors::teleportHome)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("sethome")
                    .requires(PermissionGuard.requires("lumaguilds.guild.sethome"))
                    .executes(GuildExecutors::setHome)
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                            .executes(GuildExecutors::setHome)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("description")
                    .requires(PermissionGuard.requires("lumaguilds.guild.description"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("text", greedyString())
                            .executes(GuildExecutors::setDescription)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("tag")
                    .requires(PermissionGuard.requires("lumaguilds.guild.tag"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("tag", word())
                            .executes(GuildExecutors::setTag)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("banner")
                    .requires(PermissionGuard.requires("lumaguilds.guild.banner"))
                    .executes(GuildExecutors::openBannerMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("mode")
                    .requires(PermissionGuard.requires("lumaguilds.guild.mode"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("mode", word())
                            .suggests(SuggestionProviders.guildModes())
                            .executes(GuildExecutors::setMode)
                    )
            )
            
            // === Advanced Feature Commands ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("war")
                    .requires(PermissionGuard.requires("lumaguilds.guild.war"))
                    .executes(GuildExecutors::openWarMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("relations")
                    .requires(PermissionGuard.requires("lumaguilds.guild.relations"))
                    .executes(GuildExecutors::openRelationsMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("bank")
                    .requires(PermissionGuard.requires("lumaguilds.guild.bank"))
                    .executes(GuildExecutors::openBankMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("stats")
                    .executes(GuildExecutors::showStats)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("members")
                    .requires(PermissionGuard.requires("lumaguilds.guild.members"))
                    .executes(GuildExecutors::openMembersMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("ranks")
                    .requires(PermissionGuard.requires("lumaguilds.guild.ranks"))
                    .executes(GuildExecutors::openRanksMenu)
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("settings")
                    .requires(PermissionGuard.requires("lumaguilds.guild.settings"))
                    .executes(GuildExecutors::openSettingsMenu)
            )
            
            // === Information Commands ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("info")
                    .executes(GuildExecutors::showStats)
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("guild", word())
                            .suggests(SuggestionProviders.guilds())
                            .executes(GuildExecutors::showStats)
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("menu")
                    .requires(PermissionGuard.requires("lumaguilds.guild.menu"))
                    .executes(GuildExecutors::openSettingsMenu)
            )
            
            // === Legacy Commands (for backward compatibility) ===
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("rename")
                    .requires(PermissionGuard.requires("lumaguilds.guild.rename"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("name", word())
                            .executes(GuildExecutors::create) // Legacy compatibility
                    )
            )
            .then(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("emoji")
                    .requires(PermissionGuard.requires("lumaguilds.guild.emoji"))
                    .then(
                        com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("emoji", word())
                            .executes(GuildExecutors::openSettingsMenu) // Legacy compatibility - opens settings menu
                    )
            )
    }
}
