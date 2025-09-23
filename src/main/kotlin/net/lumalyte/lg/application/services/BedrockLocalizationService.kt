package net.lumalyte.lg.application.services

import org.bukkit.entity.Player
import java.util.*

/**
 * Service for handling localization specifically for Bedrock Edition forms
 * Provides locale detection, RTL support, and Bedrock-specific translations
 */
interface BedrockLocalizationService {

    /**
     * Gets the Bedrock player's locale, preferring Floodgate's locale over Minecraft's
     */
    fun getBedrockLocale(player: Player): Locale

    /**
     * Checks if the given locale requires right-to-left text direction
     */
    fun isRTLLocale(locale: Locale): Boolean

    /**
     * Gets a localized string for Bedrock forms with RTL-aware formatting
     */
    fun getBedrockString(player: Player, key: String, vararg args: Any?): String

    /**
     * Gets a localized string for a specific locale with RTL-aware formatting
     */
    fun getBedrockString(locale: Locale, key: String, vararg args: Any?): String

    /**
     * Formats text for RTL languages (adds appropriate direction markers)
     */
    fun formatForRTL(text: String, locale: Locale): String

    /**
     * Gets the text direction marker for a locale
     */
    fun getTextDirection(locale: Locale): TextDirection

    /**
     * Gets all supported locales for Bedrock forms
     */
    fun getSupportedLocales(): Set<Locale>
}

enum class TextDirection {
    LTR, RTL
}
