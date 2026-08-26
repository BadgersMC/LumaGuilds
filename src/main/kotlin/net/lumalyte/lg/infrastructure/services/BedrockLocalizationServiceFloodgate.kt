package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.TextDirection
import org.bukkit.entity.Player
import java.util.*
import java.util.logging.Logger

/**
 * Implementation of BedrockLocalizationService using Floodgate for locale detection.
 */
class BedrockLocalizationServiceFloodgate(
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
                val floodgatePlayer = floodgateApi.getPlayer(player.uniqueId)
                if (floodgatePlayer != null) {
                    val languageCode = floodgatePlayer.languageCode
                    if (languageCode != null && languageCode.isNotBlank()) {
                        // Floodgate returns BCP 47 codes like "en_US", "fr_FR"
                        logger.fine("Resolved Bedrock locale for ${player.name}: $languageCode")
                        val parsed = Locale.forLanguageTag(languageCode.replace('_', '-'))
                        if (parsed != null && parsed.language.isNotBlank()) {
                            return parsed
                        }
                    }
                }
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
