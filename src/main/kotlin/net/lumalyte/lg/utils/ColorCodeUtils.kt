package net.lumalyte.lg.utils

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Utility functions for handling color codes and format conversion.
 * Supports both legacy (&-style) and MiniMessage formats.
 */
object ColorCodeUtils {

    /** Safe glyph id shape — rejects MiniMessage control characters (e.g. `x><reset>`). */
    private val VALID_GLYPH_ID = Regex("^[a-zA-Z0-9_-]+$")

    /**
     * Converts a guild emoji (stored in Discord format, e.g. `:catsmileysmile:`) into
     * the Nexo glyph MiniMessage tag (`<glyph:catsmileysmile>`).
     *
     * This is the proxy-safe counterpart to `%nexo_<name>%`: the PAPI placeholder only
     * resolves where Nexo's expansion runs (backend Paper), while the `<glyph:name>` tag
     * renders in MiniMessage formatters that have glyph support — e.g. Velocitab on the
     * proxy via NexoProxy, and backend chat/scoreboards via Nexo's formatting engine.
     *
     * Non-`:name:` values pass through unchanged; null or blank return `""`. Emoji names
     * containing MiniMessage control characters (anything outside `[a-zA-Z0-9_-]`) pass
     * through unchanged so they can never inject tags into the generated output.
     */
    fun emojiToGlyphTag(emoji: String?): String {
        if (emoji.isNullOrBlank()) return ""
        if (emoji.startsWith(":") && emoji.endsWith(":") && emoji.length > 2) {
            val name = emoji.substring(1, emoji.length - 1)
            if (!VALID_GLYPH_ID.matches(name)) return emoji
            return "<glyph:$name>"
        }
        return emoji
    }

    /**
     * Converts legacy color codes (&c, &6, etc.) to MiniMessage format.
     * If the input is already MiniMessage, returns it unchanged.
     *
     * Examples:
     * - "&cRed Text" -> "<red>Red Text</red>"
     * - "&6&lGold Bold" -> "<gold><bold>Gold Bold</bold></gold>"
     * - "<gradient:#FF0000:#00FF00>Text</gradient>" -> unchanged (already MiniMessage)
     */
    fun convertLegacyToMiniMessage(input: String): String {
        // If input contains MiniMessage tags, assume it's already MiniMessage format
        if (input.contains(Regex("<[^>]+>"))) {
            return input
        }

        // Parse legacy codes and convert to MiniMessage
        return try {
            val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
            val miniMessage = MiniMessage.miniMessage()

            // Parse the legacy format
            val component = legacySerializer.deserialize(input)

            // Serialize back to MiniMessage format
            miniMessage.serialize(component)
        } catch (e: Exception) {
    // Color code parsing - catching format errors
            // If conversion fails, return original input
            input
        }
    }

    /**
     * Normalizes any supported tag format to canonical MiniMessage syntax.
     *
     * Accepts MiniMessage tags (kept, round-tripped to canonical form), legacy
     * `&` codes, legacy `§` codes, or plain text. This is the read-side
     * counterpart to [convertLegacyToMiniMessage], for consumers whose formatter
     * only parses MiniMessage (e.g. Velocitab's MINIMESSAGE formatter on the proxy).
     *
     * Examples (canonical MiniMessage — redundant closing tags are dropped,
     * hex is uppercase):
     * - "&cRed" -> "<red>Red"
     * - "§6&lGold" -> "<bold><gold>Gold"
     * - "<red>Red</red>" -> "<red>Red"
     * - "Plain" -> "Plain"
     */
    fun toMiniMessage(input: String): String {
        return try {
            val normalized = input.replace('§', '&')
            val miniMessage = MiniMessage.miniMessage()
            if (normalized.contains(Regex("<[^>]+>"))) {
                miniMessage.serialize(miniMessage.deserialize(normalized))
            } else {
                miniMessage.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(normalized))
            }
        } catch (e: Exception) {
    // Color code parsing - catching format errors
            // If conversion fails, return original input
            input
        }
    }

    /**
     * Renders a tag with proper formatting for display in messages.
     * Accepts both legacy (&-style) and MiniMessage formats.
     * Returns legacy §-style format for Bukkit message display.
     *
     * Examples:
     * - "&cRed" -> "§cRed"
     * - "<red>Red</red>" -> "§cRed"
     */
    fun renderTagForDisplay(tag: String): String {
        return try {
            // First check if it contains legacy & codes
            if (tag.contains('&') && !tag.contains(Regex("<[^>]+>"))) {
                // It's legacy format - convert & to §
                val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
                val sectionSerializer = LegacyComponentSerializer.legacySection()

                val component = legacySerializer.deserialize(tag)
                sectionSerializer.serialize(component)
            } else {
                // It's MiniMessage format (or plain text) - parse and convert to legacy §
                val miniMessage = MiniMessage.miniMessage()
                val legacySerializer = LegacyComponentSerializer.legacySection()

                val component = miniMessage.deserialize(tag)
                legacySerializer.serialize(component)
            }
        } catch (e: Exception) {
    // Color code parsing - catching format errors
            // Fallback - just return the original
            tag
        }
    }

    /**
     * Strips all color codes and formatting from a string to get plain text.
     * Works with both legacy and MiniMessage formats.
     */
    fun stripAllFormatting(input: String): String {
        return try {
            // Try parsing as MiniMessage first
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(input)

            // Get plain text
            val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            plain.serialize(component)
        } catch (e: Exception) {
    // Color code parsing - catching format errors
            // Fallback: remove all formatting codes manually
            input
                .replace(Regex("<[^>]*>"), "")  // Remove MiniMessage tags
                .replace(Regex("&[0-9a-fk-or]"), "")  // Remove & codes
                .replace(Regex("§[0-9a-fk-or]"), "")  // Remove § codes
        }
    }
}
