package net.lumalyte.lg.application.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
// import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.lumalyte.lg.domain.values.LegacyPolicy
import net.lumalyte.lg.application.utilities.LocalizationProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Service for rendering messages using Adventure Components with MiniMessage as primary format.
 * Provides unified messaging pipeline with legacy input compatibility.
 */
class MessageService : KoinComponent {

    private val logger: Logger by inject()
    private val localizationProvider: LocalizationProvider by inject()

    // MiniMessage instance for primary message rendering
    private val miniMessage = MiniMessage.miniMessage()

    // Serializers for different output formats
    private val plainSerializer = PlainTextComponentSerializer.plainText()
    // private val ansiSerializer = ANSIComponentSerializer.ansi() // TODO: Enable when ANSI serializer is available
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    /**
     * Renders a localized message using MiniMessage format for a specific player.
     * Primary method for rendering static/localized texts.
     *
     * @param playerId The player's UUID for locale-specific rendering
     * @param key The localization key
     * @param placeholders Map of placeholder names to values
     * @return Component ready for sending to players
     */
    fun render(playerId: java.util.UUID, key: String, placeholders: Map<String, Any> = emptyMap()): Component {
        return try {
            val template = localizationProvider.get(playerId, key)
            val tagResolver = createTagResolver(placeholders)
            miniMessage.deserialize(template, tagResolver)
        } catch (e: Exception) {
            logger.warning("Failed to render message for key '$key': ${e.message}")
            // Fallback to a basic error message
            Component.text("Error: Could not load message '$key'")
                .color(NamedTextColor.RED)
        }
    }

    /**
     * Renders a localized message using MiniMessage format for console/server messages.
     * Uses server default locale.
     *
     * @param key The localization key
     * @param placeholders Map of placeholder names to values
     * @return Component ready for console output
     */
    fun renderConsole(key: String, placeholders: Map<String, Any> = emptyMap()): Component {
        return try {
            val template = localizationProvider.getConsole(key)
            val tagResolver = createTagResolver(placeholders)
            miniMessage.deserialize(template, tagResolver)
        } catch (e: Exception) {
            logger.warning("Failed to render console message for key '$key': ${e.message}")
            // Fallback to a basic error message
            Component.text("Error: Could not load message '$key'")
                .color(NamedTextColor.RED)
        }
    }

    /**
     * Renders a system message that doesn't require player-specific localization.
     * Uses console/server locale for system notifications and error messages.
     *
     * @param key The localization key
     * @param placeholders Map of placeholder names to values
     * @return Component ready for sending to players or console
     */
    fun renderSystem(key: String, placeholders: Map<String, Any> = emptyMap()): Component {
        return renderConsole(key, placeholders)
    }

    /**
     * Renders user-provided input with legacy formatting support.
     * Sanitizes and converts legacy color codes to modern Components.
     *
     * @param input Raw user input with legacy formatting
     * @param policy Policy defining allowed formatting and length limits
     * @return Component with sanitized formatting
     */
    fun renderLegacyUserInput(input: String, policy: LegacyPolicy = LegacyPolicy.PERMISSIVE): Component {
        return try {
            val sanitized = policy.sanitizeInput(input)
            val component = legacySerializer.deserialize(sanitized)
            policy.filterDisallowed(component)
        } catch (e: Exception) {
            logger.warning("Failed to render legacy input: ${e.message}")
            // Fallback to plain text
            Component.text(policy.sanitizeInput(input))
        }
    }

    /**
     * Converts a Component to plain text for logging.
     * Strips all formatting for console/server logs.
     */
    fun toPlainText(component: Component): String {
        return plainSerializer.serialize(component)
    }

    /**
     * Converts a Component to ANSI-colored text for console output.
     * Useful for colored console logging.
     */
    fun toAnsiText(component: Component): String {
        // TODO: Use ANSI serializer when available
        // For now, return plain text
        return toPlainText(component)
    }

    /**
     * Converts a Component back to legacy format.
     * Useful for compatibility with older systems.
     */
    fun toLegacyText(component: Component): String {
        return legacySerializer.serialize(component)
    }

    /**
     * Renders a console message and immediately converts it to plain text.
     * Convenience method for logging localized messages.
     */
    fun renderConsoleToPlainText(key: String, placeholders: Map<String, Any> = emptyMap()): String {
        return toPlainText(renderConsole(key, placeholders))
    }

    /**
     * Creates a TagResolver from a map of placeholders.
     * Converts various value types to strings for MiniMessage placeholders.
     */
    private fun createTagResolver(placeholders: Map<String, Any>): TagResolver {
        // TODO: Use proper TagResolver.builder() when available
        // For now, create individual resolvers and combine them
        val resolvers = placeholders.entries.map { (key, value) ->
            val stringValue = when (value) {
                is Component -> miniMessage.serialize(value)
                is Number -> value.toString()
                is Boolean -> if (value) "true" else "false"
                null -> ""
                else -> value.toString()
            }
            Placeholder.parsed(key, stringValue)
        }

        return if (resolvers.isEmpty()) {
            TagResolver.empty()
        } else {
            // Combine all resolvers
            TagResolver.resolver(*resolvers.toTypedArray())
        }
    }

    /**
     * Validates if a MiniMessage template is well-formed.
     * Useful for development-time validation of localization keys.
     */
    fun validateTemplate(template: String): Boolean {
        return try {
            miniMessage.deserialize(template)
            true
        } catch (e: Exception) {
            logger.warning("Invalid MiniMessage template: ${e.message}")
            false
        }
    }
}
