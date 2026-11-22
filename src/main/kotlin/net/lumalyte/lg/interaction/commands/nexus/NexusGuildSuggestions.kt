package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.interaction.commands.GuildCommandCompletions
import org.bukkit.entity.Player

/** Nexus `@Suggests` keys for the `/guild` family; guild-prefixed so the central provider map cannot collide. */
internal object NexusGuildSuggestionKeys {
    const val ALL_PLAYERS = "guild.allplayers"
    const val PLAYERS = "guild.players"
    const val GUILD_MEMBERS = "guild.guildmembers"
    const val GUILDS = "guild.guilds"
    const val PENDING_INVITES = "guild.pendinginvites"
    const val GUILDS_OR_PLAYERS = "guild.guildsorplayers"
    const val ALLY_GUILDS = "guild.allyguilds"
    const val GUILD_HOMES = "guild.guildhomes"
    const val GUILD_ACCESSIBLE_HOMES = "guild.guildaccessiblehomes"
    const val UNLOCKED_EMOJIS = "guild.unlockedemojis"
    const val ON_OFF = "guild.onoff"
    const val ON_OFF_TOGGLE = "guild.onofftoggle"
    const val MODES = "guild.modes"
}

/**
 * Builds every provider referenced by [NexusGuildCommands] from the shared [GuildCommandCompletions].
 * Values are filtered by the typed prefix case-insensitively, as ACF did; player-scoped sources
 * return nothing for non-player senders (ACF `context.player == null`). Route permission and
 * player-only filtering is applied by the Nexus argument node itself.
 */
internal fun nexusGuildSuggestionProviders(
    completions: GuildCommandCompletions,
): Map<String, SuggestionProvider<CommandSourceStack>> {
    fun any(values: (CommandSourceStack) -> List<String>) = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        val typed = builder.remaining.lowercase()
        values(ctx.source).filter { it.lowercase().startsWith(typed) }.forEach(builder::suggest)
        builder.buildFuture()
    }
    fun player(values: (Player) -> List<String>) = any { source ->
        (source.sender as? Player)?.let(values) ?: emptyList()
    }
    return with(NexusGuildSuggestionKeys) {
        mapOf(
            ALL_PLAYERS to any { completions.allPlayers() },
            PLAYERS to any { completions.visiblePlayers(it.sender) },
            GUILD_MEMBERS to player(completions::guildMembers),
            GUILDS to any { completions.guilds() },
            PENDING_INVITES to player(completions::pendingInvites),
            GUILDS_OR_PLAYERS to any { completions.guildsOrPlayers() },
            ALLY_GUILDS to player(completions::allyGuilds),
            GUILD_HOMES to player(completions::guildHomes),
            GUILD_ACCESSIBLE_HOMES to player { sender ->
                // The shared home literal can also be reached with only the home unlock permission.
                if (sender.hasPermission("lumaguilds.guild.home")) completions.accessibleHomes(sender) else emptyList()
            },
            UNLOCKED_EMOJIS to player(completions::unlockedEmojis),
            ON_OFF to any { GuildCommandCompletions.ON_OFF },
            ON_OFF_TOGGLE to any { GuildCommandCompletions.ON_OFF_TOGGLE },
            MODES to any { GuildCommandCompletions.GUILD_MODES },
        )
    }
}
