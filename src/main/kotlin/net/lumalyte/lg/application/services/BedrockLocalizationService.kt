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
     * Formats text for RTL languages (adds appropriate direction markers)
     */
    fun formatForRTL(text: String, locale: Locale): String

    /**
     * Gets the text direction marker for a locale
     */
    fun getTextDirection(locale: Locale): TextDirection

}

enum class TextDirection {
    LTR, RTL
}
