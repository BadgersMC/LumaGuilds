package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.entities.Guild

/**
 * Utility class for formatting guild display names with emojis.
 * Useful for tab lists, name tags, chat formatting, etc.
 */
object GuildDisplayUtils {
    
    /**
     * Gets the formatted display name for a guild including emoji if set.
     * This is useful for chat, tab list, name tags, etc.
     *
     * @param guild The guild to format.
     * @return The formatted display name with emoji prefix if available.
     */
    fun getFormattedGuildName(guild: Guild): String {
        return if (guild.emoji != null && isValidEmojiFormat(guild.emoji)) {
            "${guild.emoji} ${guild.name}"
        } else {
            guild.name
        }
    }
    
    /**
     * Gets just the emoji part for a guild, or empty string if not set.
     * Useful for cases where you want to display just the emoji separately.
     *
     * @param guild The guild to get emoji for.
     * @return The emoji placeholder string, or empty string.
     */
    fun getGuildEmoji(guild: Guild): String {
        return if (guild.emoji != null && isValidEmojiFormat(guild.emoji)) {
            guild.emoji
        } else {
            ""
        }
    }
    
    /**
     * Creates a guild tag format suitable for tab list or name tags.
     * Format: [EMOJI GuildName] or [GuildName] if no emoji.
     *
     * @param guild The guild to create tag for.
     * @param brackets Whether to include brackets around the tag.
     * @return The formatted guild tag.
     */
    fun createGuildTag(guild: Guild, brackets: Boolean = true): String {
        val displayName = getFormattedGuildName(guild)
        return if (brackets) {
            "[$displayName]"
        } else {
            displayName
        }
    }
    
    /**
     * Creates a compact guild display for situations with limited space.
     * Uses just emoji if available, otherwise abbreviated guild name.
     *
     * @param guild The guild to create compact display for.
     * @param maxLength Maximum length for the display.
     * @return The compact guild display.
     */
    fun createCompactGuildDisplay(guild: Guild, maxLength: Int = 10): String {
        val emoji = getGuildEmoji(guild)
        
        return if (emoji.isNotEmpty()) {
            // Use emoji if available
            emoji
        } else {
            // Abbreviate guild name if too long
            if (guild.name.length > maxLength) {
                guild.name.substring(0, maxLength - 1) + "…"
            } else {
                guild.name
            }
        }
    }
    
    /**
     * Formats a guild name for use in chat messages.
     * Includes color formatting and emoji support.
     *
     * @param guild The guild to format.
     * @param color Optional color code to apply (e.g., "&e" for yellow).
     * @return The formatted chat display.
     */
    fun formatForChat(guild: Guild, color: String = "&7"): String {
        val emoji = getGuildEmoji(guild)
        val displayName = if (emoji.isNotEmpty()) {
            "$emoji $color${guild.name}"
        } else {
            "$color${guild.name}"
        }
        return displayName
    }
    
    private fun isValidEmojiFormat(emoji: String): Boolean {
        return emoji.startsWith(":") && emoji.endsWith(":") && emoji.length > 2
    }
}
