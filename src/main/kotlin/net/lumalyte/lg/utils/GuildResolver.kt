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
 *   2. unambiguous normalized guild name match (formatting + non-alphanumerics
 *      stripped). Ambiguous normalized matches return null so destructive
 *      commands never act on the wrong guild.
 *   3. online player name -> that player's first guild
 *   4. cached offline player name -> that player's first guild
 */
object GuildResolver {

    // Legacy colour-code regex used as a safety net for inputs MiniMessage
    // parses as plain text (it doesn't strip raw `&c`-style codes).
    private val LEGACY_CODES = Regex("[&§][0-9a-fk-orA-FK-OR]")

    /**
     * Strip MiniMessage tags, legacy `&` codes, section `§` codes, lowercase,
     * and drop everything that isn't a letter or digit. Used both for the
     * storage-side index and for query-side input so they match.
     *
     * The legacy-code regex runs both before and after [ColorCodeUtils.stripAllFormatting]
     * because that helper's happy path goes through MiniMessage, which treats
     * `&c` as plain text rather than a formatting code.
     */
    fun normalize(input: String): String {
        val preStripped = LEGACY_CODES.replace(input, "")
        val mmStripped = ColorCodeUtils.stripAllFormatting(preStripped)
        val postStripped = LEGACY_CODES.replace(mmStripped, "")
        return postStripped.lowercase().filter { it.isLetterOrDigit() }
    }

    /**
     * Plain-text display name for a stored guild — used in tab-completion
     * suggestions and user-facing messages. Falls back to the raw stored name
     * if stripping somehow produces an empty string.
     */
    fun displayName(guild: Guild): String =
        ColorCodeUtils.stripAllFormatting(guild.name).trim().ifEmpty { guild.name }

    /**
     * Lookup by guild name only: exact (case-insensitive) match first, then a
     * normalized fallback over all guilds.
     *
     * Returns null when the normalized form matches more than one guild —
     * ambiguity is treated as "no match" so destructive commands such as
     * disband or removevault never operate on the wrong guild.
     */
    fun resolveGuildByName(input: String, guildService: GuildService): Guild? {
        if (input.isBlank()) return null
        guildService.getGuildByName(input)?.let { return it }
        val needle = normalize(input)
        if (needle.isEmpty()) return null
        val matches = guildService.getAllGuilds().filter { normalize(it.name) == needle }
        return matches.singleOrNull()
    }

    /**
     * Lookup by player name (online first, then cached offline) and return
     * that player's first guild.
     *
     * Uses [Bukkit.getOfflinePlayerIfCached] to avoid the synchronous Mojang
     * API call [Bukkit.getOfflinePlayer] performs for uncached names — that
     * would block the main thread when invoked from a command handler.
     */
    fun resolveGuildByPlayerName(input: String, guildService: GuildService): Guild? {
        if (input.isBlank()) return null
        val online: Player? = Bukkit.getPlayerExact(input)
        val playerId = online?.uniqueId
            ?: Bukkit.getOfflinePlayerIfCached(input)?.uniqueId
            ?: return null
        return guildService.getPlayerGuilds(playerId).firstOrNull()
    }

    /**
     * Resolve an arbitrary user-supplied string to a guild — guild name first,
     * then player name. Returns null if neither path produces an unambiguous
     * match.
     */
    fun resolve(input: String, guildService: GuildService): Guild? {
        return resolveGuildByName(input, guildService)
            ?: resolveGuildByPlayerName(input, guildService)
    }

    /**
     * Plain-text suggestion list (no MiniMessage tags, sorted, de-duplicated)
     * for tab completion. The optional [filter] lets callers scope suggestions
     * — for example to a player's own guilds — without duplicating the
     * stripping or sorting logic.
     */
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
