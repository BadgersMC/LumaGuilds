package net.lumalyte.lg.utils

import net.lumalyte.lg.config.NameFilterConfig
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Validates guild names and tags for inappropriate content.
 *
 * Applies configurable normalization (leet-speak mapping, repeat collapsing)
 * before checking against configurable regex blocklist patterns.
 *
 * Guild tags have MiniMessage formatting stripped before content checking.
 * Structural validation (interactive tags, length, emptiness) remains in
 * [GuildTagValidator].
 */
object GuildNameFilter {

    private val LEET_MAP = mapOf(
        '@' to 'a', '4' to 'a',
        '3' to 'e',
        '1' to 'i', '!' to 'i',
        '0' to 'o',
        '$' to 's', '5' to 's',
        '7' to 't',
        '2' to 'z',
        '8' to 'b',
        '6' to 'g',
        '9' to 'g',
    )

    /**
     * Checks plain text (guild name) against the filter.
     * Returns a rejection reason string if blocked, null if acceptable.
     */
    fun checkName(name: String, config: NameFilterConfig): String? {
        if (!config.enabled || config.blockedPatterns.isEmpty()) return null
        val normalized = normalize(name, config)
        return matchBlocked(normalized, config.blockedPatterns)
    }

    /**
     * Checks a MiniMessage-formatted guild tag against the filter.
     * Strips formatting first, then checks the visible text content.
     * Returns a rejection reason if blocked, null if acceptable.
     */
    fun checkTag(tag: String, config: NameFilterConfig): String? {
        if (!config.enabled || config.blockedPatterns.isEmpty()) return null
        val plainText = stripMiniMessage(tag)
        val normalized = normalize(plainText, config)
        return matchBlocked(normalized, config.blockedPatterns)
    }

    private fun normalize(text: String, config: NameFilterConfig): String {
        var result = text.lowercase()

        if (config.normalization.leetMap) {
            result = result.map { LEET_MAP[it] ?: it }.joinToString("")
        }

        if (config.normalization.collapseRepeats) {
            result = result.replace(Regex("(.)\\1{2,}"), "$1$1")
        }

        return result
    }

    private fun matchBlocked(text: String, patterns: List<String>): String? {
        for (pattern in patterns) {
            try {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    return "Name contains inappropriate content."
                }
            } catch (e: Exception) {
                // Malformed regex in config — skip it, don't crash
                continue
            }
        }
        return null
    }

    private fun stripMiniMessage(tag: String): String {
        return try {
            val component = MiniMessage.miniMessage().deserialize(tag)
            PlainTextComponentSerializer.plainText().serialize(component)
        } catch (e: Exception) {
            // Unparseable MiniMessage — strip tags with regex as fallback
            tag.replace(Regex("<[^>]*>"), "")
        }
    }
}
