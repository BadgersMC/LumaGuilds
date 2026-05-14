package net.lumalyte.lg.interaction.help

/**
 * A help topic surfaced by `/g help` and mirrored to a wiki page under
 * `wiki/docs/players/<slug>.md`. The [slug] is the stable identifier and
 * must match the wiki page's `topic:` front-matter field.
 */
data class HelpTopic(
    val slug: String,
    val displayName: String,
    val summary: String,
    val commands: List<HelpCommandEntry>,
) {
    init {
        require(SLUG_REGEX.matches(slug)) { "Invalid slug: $slug (must be lowercase-hyphenated)" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(summary.isNotBlank()) { "summary must not be blank" }
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    }
}
