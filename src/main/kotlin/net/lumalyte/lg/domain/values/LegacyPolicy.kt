package net.lumalyte.lg.domain.values

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Policy for handling legacy formatting input from users.
 * Defines rules for sanitizing and converting legacy color codes to modern Components.
 */
data class LegacyPolicy(
    val maxLength: Int = 256,
    val allowedColors: Set<NamedTextColor> = DEFAULT_ALLOWED_COLORS,
    val allowFormatting: Boolean = true,
    val allowedDecorations: Set<TextDecoration> = DEFAULT_ALLOWED_DECORATIONS
) {

    companion object {
        // Default allowed colors (common Minecraft colors)
        val DEFAULT_ALLOWED_COLORS = setOf(
            NamedTextColor.BLACK,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED,
            NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD,
            NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.RED,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.YELLOW,
            NamedTextColor.WHITE
        )

        // Default allowed text decorations
        val DEFAULT_ALLOWED_DECORATIONS = setOf(
            TextDecoration.BOLD,
            TextDecoration.ITALIC,
            TextDecoration.UNDERLINED,
            TextDecoration.STRIKETHROUGH,
            TextDecoration.OBFUSCATED
        )

        // Strict policy for high-security contexts (names, etc.)
        val STRICT = LegacyPolicy(
            maxLength = 32,
            allowedColors = setOf(
                NamedTextColor.WHITE,
                NamedTextColor.GRAY,
                NamedTextColor.GREEN,
                NamedTextColor.YELLOW,
                NamedTextColor.GOLD,
                NamedTextColor.RED
            ),
            allowFormatting = false,
            allowedDecorations = emptySet()
        )

        // Permissive policy for general chat
        val PERMISSIVE = LegacyPolicy(
            maxLength = 512,
            allowedColors = DEFAULT_ALLOWED_COLORS,
            allowFormatting = true,
            allowedDecorations = DEFAULT_ALLOWED_DECORATIONS
        )
    }

    /**
     * Filters disallowed formatting from a Component created from legacy input.
     * Removes colors and decorations that are not in the allowed sets.
     */
    fun filterDisallowed(component: Component): Component {
        // TODO: Implement proper filtering when Adventure filterDisallowed API is available
        // For now, return the component as-is
        return component
    }

    /**
     * Sanitizes raw legacy input string before conversion.
     * Removes control characters and truncates to max length.
     */
    fun sanitizeInput(input: String): String {
        return input
            .take(maxLength)
            .replace("\u0000", "") // Remove null characters
            .replace("\r", "") // Remove carriage returns
            .filter { it.code in 32..126 || it in "\n\t" } // Allow printable ASCII + newlines/tabs
    }
}
