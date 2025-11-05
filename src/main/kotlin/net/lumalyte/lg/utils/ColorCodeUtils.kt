package net.lumalyte.lg.utils

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Utility functions for handling color codes and format conversion.
 * Supports both legacy (&-style) and MiniMessage formats.
 */
object ColorCodeUtils {

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
            // Fallback: remove all formatting codes manually
            input
                .replace(Regex("<[^>]*>"), "")  // Remove MiniMessage tags
                .replace(Regex("&[0-9a-fk-or]"), "")  // Remove & codes
                .replace(Regex("§[0-9a-fk-or]"), "")  // Remove § codes
        }
    }
}
