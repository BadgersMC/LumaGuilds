package net.lumalyte.lg.application.services

/**
 * Provider-neutral reference to a Discord account.
 *
 * LumaGuilds treats this value as opaque. Discord providers may use a native snowflake,
 * a platform-issued token, or another bounded printable identifier. Provider-specific
 * parsing and identity lookup must stay outside the application layer.
 */
data class DiscordAccountReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Discord account reference must not be blank" }
        require(value.length <= MAX_LENGTH) { "Discord account reference is too long" }
        require(value.none(Char::isISOControl)) { "Discord account reference contains control characters" }
    }

    private companion object {
        const val MAX_LENGTH = 256
    }
}
