package net.lumalyte.lg.di.runtime

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.accessibleHomeNames
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.services.GuildInvitationManager
import net.lumalyte.lg.utils.ColorCodeUtils
import net.lumalyte.lg.utils.GuildResolver
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Named Brigadier suggestion providers replacing the custom ACF completions formerly registered
 * in LumaGuilds.kt. Nexus commands reference them with `@Suggests("<id>")`.
 *
 * Suggestions are filtered by the typed prefix, case-insensitively, as ACF did. Providers that
 * ACF ran synchronously (current membership/rank caches) are computed on the server thread via
 * [serverThread] when Paper requests suggestions from another thread.
 */
internal fun guildCommandSuggestions(
    graph: GuildRuntimeGraph,
    offlineName: (UUID) -> String?,
    onlinePlayerNames: () -> List<String>,
    serverThread: Executor? = null,
    isServerThread: () -> Boolean = { true },
): Map<String, SuggestionProvider<CommandSourceStack>> {
    fun provider(compute: (Player?) -> List<String>) = SuggestionProvider<CommandSourceStack> { context, builder ->
        complete(builder, compute(context.player()))
    }

    fun serverThreadProvider(compute: (Player?) -> List<String>) = SuggestionProvider<CommandSourceStack> { context, builder ->
        val player = context.player()
        if (serverThread == null || isServerThread()) {
            complete(builder, compute(player))
        } else {
            CompletableFuture.supplyAsync({ compute(player) }, serverThread).thenCompose { complete(builder, it) }
        }
    }

    fun firstGuild(player: Player) = graph.guildService.getPlayerGuilds(player.uniqueId).firstOrNull()

    return mapOf(
        "allplayers" to provider { onlinePlayerNames() },
        "guildmembers" to provider { player ->
            val guild = player?.let(::firstGuild) ?: return@provider emptyList()
            graph.memberService.getGuildMembers(guild.id)
                .filter { it.playerId != player.uniqueId }
                .mapNotNull { offlineName(it.playerId) }
        },
        "guilds" to provider { GuildResolver.suggestions(graph.guildService) },
        "pendinginvites" to provider { player ->
            if (player == null) return@provider emptyList()
            GuildInvitationManager.getInvites(player.uniqueId)
                .map { (_, guildName) -> ColorCodeUtils.stripAllFormatting(guildName).trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        },
        "guildsorplayers" to provider {
            (GuildResolver.suggestions(graph.guildService) + onlinePlayerNames()).distinct().sorted()
        },
        // Own guild's ally-home (when usable) plus every ally with a usable ally-home.
        "allyguilds" to provider { player ->
            val source = player?.let(::firstGuild) ?: return@provider emptyList()
            val guildService = graph.guildService
            val results = mutableListOf<String>()
            if (source.allyHome != null && guildService.canUseOwnAllyHome(player.uniqueId, source.id)) results += source.name
            graph.relationService.getGuildRelationsByType(source.id, RelationType.ALLY).mapNotNullTo(results) { relation ->
                val other = guildService.getGuild(relation.getOtherGuild(source.id)) ?: return@mapNotNullTo null
                other.name.takeIf { other.allyHome != null && guildService.canUseAllyHome(player.uniqueId, source.id, other.id) }
            }
            results.distinct().sorted()
        },
        "guildhomes" to provider { player ->
            val guild = player?.let(::firstGuild) ?: return@provider emptyList()
            graph.guildService.getHomes(guild.id).homeNames.toList()
        },
        "guildaccessiblehomes" to serverThreadProvider { player ->
            val guild = player?.let(::firstGuild) ?: return@serverThreadProvider emptyList()
            graph.guildService.accessibleHomeNames(player.uniqueId, guild.id)
        },
        "unlockedemojis" to provider { player ->
            if (player == null) return@provider emptyList()
            graph.nexoEmojiService.getPlayerUnlockedEmojis(player, firstGuild(player)?.id).map { ":$it:" }
        },
        "parties" to provider { player ->
            if (player == null) return@provider listOf("GLOBAL")
            val guildIds = graph.guildService.getPlayerGuilds(player.uniqueId).map { it.id }.toSet()
            if (guildIds.isEmpty()) return@provider listOf("GLOBAL")
            val names = guildIds.flatMap { graph.partyService.getActivePartiesForGuild(it) }.toSet()
                .filter { !it.isPlayerBanned(player.uniqueId) }
                .mapNotNull { it.name }
                .sorted()
            listOf("GLOBAL") + names
        },
    )
}

private fun CommandContext<CommandSourceStack>.player(): Player? =
    source.executor as? Player ?: source.sender as? Player

private fun complete(builder: SuggestionsBuilder, values: List<String>): CompletableFuture<Suggestions> {
    val prefix = builder.remainingLowerCase
    values.filter { it.lowercase().startsWith(prefix) }.forEach(builder::suggest)
    return builder.buildFuture()
}
