package net.lumalyte.lg.utils

/**
 * Validates guild tag input for unsafe MiniMessage content.
 *
 * Guild tags are plain display text and may carry color/formatting only. Interactive
 * MiniMessage tags (click/hover/insertion and other event actions) must never be stored,
 * otherwise a tag rendered next to a player's name could run commands, open URLs, or
 * inject text on click/hover. Color and decoration tags (e.g. <red>, <bold>, <gradient>)
 * remain allowed.
 */
object GuildTagValidator {

    /**
     * MiniMessage tags that attach an interactive event/action. Matched case-insensitively
     * against both opening (<click:...>) and closing (</click>) forms.
     */
    private val DISALLOWED_TAGS = listOf("click", "hover", "insertion")

    private val disallowedPattern = Regex(
        """<\s*/?\s*(${DISALLOWED_TAGS.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns a user-facing rejection reason if the tag contains a disallowed interactive
     * MiniMessage tag, or null if the tag is acceptable.
     */
    fun rejectionReason(tag: String): String? {
        val match = disallowedPattern.find(tag) ?: return null
        val tagName = match.groupValues[1].lowercase()
        return "Guild tags cannot contain interactive '$tagName' tags. Use colors and formatting only."
    }

    /**
     * Convenience check: true if the tag is free of disallowed interactive tags.
     */
    fun isAllowed(tag: String): Boolean = rejectionReason(tag) == null
}
