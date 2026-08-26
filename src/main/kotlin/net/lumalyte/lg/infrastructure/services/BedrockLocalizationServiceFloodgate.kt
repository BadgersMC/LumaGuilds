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
    private val logger: Logger,
    private val floodgateLanguageCode: (UUID) -> String? = { playerId ->
        val api = org.geysermc.floodgate.api.FloodgateApi.getInstance()
        if (api.isFloodgatePlayer(playerId)) api.getPlayer(playerId)?.languageCode else null
    }
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
        val languageCode = try {
            floodgateLanguageCode(player.uniqueId)
        } catch (error: Exception) {
            logger.fine("Floodgate locale unavailable for ${player.name}: ${error.message}")
            null
        }
        if (!languageCode.isNullOrBlank()) {
            logger.fine("Resolved Bedrock locale for ${player.name}: $languageCode")
            val parsed = Locale.forLanguageTag(languageCode.replace('_', '-'))
            if (parsed.language.isNotBlank()) {
                return parsed
            }
        }

        return try {
            val minecraftLocale = player.locale()
            Locale.forLanguageTag(minecraftLocale.toString().replace('_', '-'))
        } catch (e: Exception) {
            logger.warning("Error detecting Bedrock locale for player ${player.name}: ${e.message}")
            Locale.ENGLISH
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
