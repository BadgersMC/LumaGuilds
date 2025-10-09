package net.lumalyte.lg.interaction.brigadier

/* TEMPORARILY DISABLED - REFERENCES STUB CODE
package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Brigadier command tree for Bedrock cache statistics functionality.
 * Provides cache management and statistics for Bedrock players.
 */
object BedrockCacheStatsRootTree {

    /**
     * Registers the bedrockcachestats command tree with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("bedrockcachestats")
                .requires(PermissionGuard.requires("lumalyte.bedrock.cache"))
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("stats")
                        .requires(PermissionGuard.requires("lumalyte.bedrock.cache.stats"))
                        .executes(LumaGuildsAdminExecutors::bedrockStats)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("clear")
                        .requires(PermissionGuard.requires("lumalyte.bedrock.cache.clear"))
                        .executes(LumaGuildsAdminExecutors::bedrockClear)
                )
                .then(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<CommandSourceStack>("help")
                        .executes(LumaGuildsAdminExecutors::help)
                )
        )
    }
}

*/