package net.lumalyte.lg.utils

import net.lumalyte.lg.config.NameFilterConfig

/**
 * Validates guild tag input for unsafe MiniMessage content AND filters
 * inappropriate visible text content after stripping formatting.
 *
 * Guild tags are plain display text and may carry color/formatting only. Interactive
 * MiniMessage tags (click/hover/insertion and other event actions) must never be stored,
 * otherwise a tag rendered next to a player's name could run commands, open URLs, or
 * inject text on click/hover. Color and decoration tags (e.g. <red>, <bold>, <gradient>)
 * remain allowed.
 *
 * After structural validation, visible text content is checked through
 * [GuildNameFilter.checkTag] against the configured blocklist.
 */
object GuildTagValidator {

    sealed interface Failure {
        data class InteractiveTag(val tagName: String) : Failure
        data object InappropriateContent : Failure
    }

    /**
     * MiniMessage tags that attach an interactive event/action. Matched case-insensitively
     * against both opening (<click:...>) and closing (</click>) forms.
     */
    private val DISALLOWED_TAGS = listOf("click", "hover", "insertion")

    private val disallowedPattern = Regex(
        """<\s*/?\s*(${DISALLOWED_TAGS.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns a typed failure so each interaction adapter can localize its own feedback. */
    fun validationFailure(tag: String, nameFilterConfig: NameFilterConfig): Failure? {
        val match = disallowedPattern.find(tag)
        if (match != null) {
            val tagName = match.groupValues[1].lowercase()
            return Failure.InteractiveTag(tagName)
        }
        return if (GuildNameFilter.checkTag(tag, nameFilterConfig) == null) {
            null
        } else {
            Failure.InappropriateContent
        }
    }

    /**
     * Convenience check: true if the tag is free of disallowed interactive tags
     * AND inappropriate content.
     */
    fun isAllowed(tag: String, nameFilterConfig: NameFilterConfig): Boolean =
        validationFailure(tag, nameFilterConfig) == null
}
