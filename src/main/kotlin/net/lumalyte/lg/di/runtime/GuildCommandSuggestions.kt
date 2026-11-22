package net.lumalyte.lg.di.runtime

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.interaction.commands.GuildCommandCompletions
import net.lumalyte.lg.interaction.commands.PartyChatHandler
import net.lumalyte.lg.interaction.commands.nexus.NexusPartyChatSuggestions
import net.lumalyte.lg.interaction.commands.nexus.claimCommandSuggestionProviders
import net.lumalyte.lg.interaction.commands.nexus.nexusGuildSuggestionProviders
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

internal fun runtimeCommandSuggestions(
    guild: GuildCommandCompletions,
    party: PartyChatHandler,
    claimsEnabled: Boolean,
    serverThread: Executor,
): Map<String, SuggestionProvider<CommandSourceStack>> {
    val providers = buildMap {
        putAll(nexusGuildSuggestionProviders(guild))
        put(NexusPartyChatSuggestions.KEY, NexusPartyChatSuggestions.provider { party })
        if (claimsEnabled) putAll(claimCommandSuggestionProviders())
    }
    // Providers read Bukkit players, visibility, and membership caches on the server thread.
    return providers.mapValues { (_, provider) ->
        SuggestionProvider { context, builder ->
            if (Bukkit.isPrimaryThread()) provider.getSuggestions(context, builder)
            else CompletableFuture.supplyAsync({ provider.getSuggestions(context, builder) }, serverThread)
                .thenCompose { it }
        }
    }
}
