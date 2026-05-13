package net.lumalyte.lg.utils

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Centralized guild lookup used by commands, completions, and any caller that
 * accepts user-supplied strings. Guild names are validated as plain ASCII
 * (see GuildCommand.onCreate), but this resolver still strips MiniMessage and
 * legacy color codes defensively in case legacy/imported data contains them.
 *
 * Resolution priority for [resolve]:
 *   1. exact (case-insensitive) guild name match
 *   2. normalized guild name match (formatting + non-alphanumerics stripped)
 *   3. online player name -> that player's first guild
 *   4. offline player name -> that player's first guild
 */
object GuildResolver {

    /**
     * Strip MiniMessage tags, legacy & codes, section codes, and lowercase.
     * Used both for storage-side index and query-side input.
     */
    fun normalize(input: String): String {
        val stripped = ColorCodeUtils.stripAllFormatting(input)
        return stripped.lowercase().filter { it.isLetterOrDigit() }
    }

    /** Plain-text display name for a stored guild (best effort strip). */
    fun displayName(guild: Guild): String =
        ColorCodeUtils.stripAllFormatting(guild.name).trim().ifEmpty { guild.name }

    /** Lookup by guild name only (exact, then normalized). */
    fun resolveGuildByName(input: String, guildService: GuildService): Guild? {
        if (input.isBlank()) return null
        guildService.getGuildByName(input)?.let { return it }
        val needle = normalize(input)
        if (needle.isEmpty()) return null
        return guildService.getAllGuilds().firstOrNull { normalize(it.name) == needle }
    }

    /**
     * Lookup by player name (online first, then cached offline).
     *
     * Uses `getOfflinePlayerIfCached` to avoid the synchronous Mojang API lookup
     * `getOfflinePlayer(String)` performs on uncached names — that would block the
     * main thread when called from a command handler.
     */
    fun resolveGuildByPlayerName(input: String, guildService: GuildService): Guild? {
        if (input.isBlank()) return null
        val online: Player? = Bukkit.getPlayerExact(input)
        val playerId = online?.uniqueId
            ?: Bukkit.getOfflinePlayerIfCached(input)?.uniqueId
            ?: return null
        return guildService.getPlayerGuilds(playerId).firstOrNull()
    }

    /** Guild-name first, then player-name. */
    fun resolve(input: String, guildService: GuildService): Guild? {
        return resolveGuildByName(input, guildService)
            ?: resolveGuildByPlayerName(input, guildService)
    }

    /** Plain-text suggestion list (no MiniMessage tags) for tab completion. */
    fun suggestions(guildService: GuildService, filter: (Guild) -> Boolean = { true }): List<String> {
        return guildService.getAllGuilds()
            .asSequence()
            .filter(filter)
            .map { displayName(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }
}
