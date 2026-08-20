package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.TextDirection
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import java.util.*
import java.util.logging.Logger

/**
 * Implementation of BedrockLocalizationService using Floodgate for locale detection.
 */
class BedrockLocalizationServiceFloodgate(
    private val dataFolder: File,
    private val lang: LangService,
    private val logger: Logger
) : BedrockLocalizationService {

    // RTL language codes (ISO 639-1)
    private val rtlLanguages = setOf(
        "ar", // Arabic
        "he", // Hebrew
        "fa", // Persian/Farsi
        "ur", // Urdu
        "yi", // Yiddish
        "ji"  // Yiddish (alternative)
    )

    companion object {
        // Unicode direction markers
        const val LTR_MARKER = "\u200E" // LEFT-TO-RIGHT MARK
        const val RTL_MARKER = "\u200F" // RIGHT-TO-LEFT MARK
    }

    override fun getBedrockLocale(player: Player): Locale {
        return try {
            // Try to get Floodgate-specific locale first
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            if (floodgateApi != null && floodgateApi.isFloodgatePlayer(player.uniqueId)) {
                // Floodgate stores locale information, but we need to access it differently
                // For now, fall back to player's Minecraft locale
                // TODO: Implement proper Floodgate locale detection when API allows
                logger.fine("Using Minecraft locale for Bedrock player ${player.name}")
            }

            // Use player's Minecraft locale as fallback
            val minecraftLocale = player.locale()
            Locale.forLanguageTag(minecraftLocale.toString().replace('_', '-'))

        } catch (e: Exception) {
            // Floodgate integration - catching all exceptions for compatibility
            logger.warning("Error detecting Bedrock locale for player ${player.name}: ${e.message}")
            Locale.ENGLISH // Default fallback
        }
    }

    override fun isRTLLocale(locale: Locale): Boolean {
        return rtlLanguages.contains(locale.language.lowercase())
    }

    override fun getBedrockString(player: Player, key: String, vararg args: Any?): String {
        val locale = getBedrockLocale(player)
        return getBedrockString(locale, key, *args)
    }

    override fun getBedrockString(locale: Locale, key: String, vararg args: Any?): String {
        // Try the exact key first (without "bedrock." prefix)
        var translation = getBedrockTranslation(locale, key, *args)
        if (translation != key) {
            return formatForRTL(translation, locale)
        }

        // Try Bedrock-specific translation with "bedrock." prefix
        val bedrockKey = "bedrock.$key"
        translation = getBedrockTranslation(locale, bedrockKey, *args)
        if (translation != bedrockKey) {
            return formatForRTL(translation, locale)
        }

        // Fall back to regular localization
        val regularTranslation = try {
            lang.legacy(key)
        } catch (e: Exception) {
            // Floodgate integration - catching all exceptions for compatibility
            logger.warning("Error getting regular localization for key '$key': ${e.message}")
            key
        }

        return formatForRTL(regularTranslation, locale)
    }

    override fun formatForRTL(text: String, locale: Locale): String {
        return if (isRTLLocale(locale)) {
            "${RTL_MARKER}$text${LTR_MARKER}"
        } else {
            text
        }
    }

    override fun getTextDirection(locale: Locale): TextDirection {
        return if (isRTLLocale(locale)) TextDirection.RTL else TextDirection.LTR
    }

}
