package net.lumalyte.lg.interaction.help

/**
 * Stable identifier for one command documented by a [HelpTopic]. Player-facing
 * syntax, copy, formatting, and interaction events live in the locale resource.
 */
data class HelpCommandEntry(
    val id: String,
) {
    init {
        require(ID_REGEX.matches(id)) { "Invalid command identifier: $id" }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    }
}
