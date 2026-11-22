package net.badgersmc.nexus.i18n

/**
 * Locale selection bean. Consumer plugins register an instance of this value
 * type (typically from their own config) so [LangService] knows which locale
 * file to load. Falls back to the [LangFile.defaultLocale] when blank.
 */
data class Locale(val id: String) {
    init {
        require(!id.contains('/') && !id.contains('\\')) { "Locale id may not contain path separators" }
    }
}
