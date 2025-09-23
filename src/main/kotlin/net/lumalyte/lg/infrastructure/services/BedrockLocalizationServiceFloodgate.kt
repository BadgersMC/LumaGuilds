package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.TextDirection
import net.lumalyte.lg.application.utilities.LocalizationProvider
import org.bukkit.entity.Player
import java.io.File
import java.text.MessageFormat
import java.util.*
import java.util.logging.Logger

/**
 * Implementation of BedrockLocalizationService using Floodgate for locale detection
 * and properties files for translations
 */
class BedrockLocalizationServiceFloodgate(
    private val dataFolder: File,
    private val localizationProvider: LocalizationProvider,
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

    // Bedrock-specific translation properties
    private val bedrockTranslations: MutableMap<String, Properties> = mutableMapOf()

    companion object {
        // Unicode direction markers
        const val LTR_MARKER = "\u200E" // LEFT-TO-RIGHT MARK
        const val RTL_MARKER = "\u200F" // RIGHT-TO-LEFT MARK
    }

    init {
        loadBedrockTranslations()
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
            localizationProvider.getConsole(key, *args)
        } catch (e: Exception) {
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

    override fun getSupportedLocales(): Set<Locale> {
        return bedrockTranslations.keys.mapNotNull { localeStr ->
            try {
                Locale.forLanguageTag(localeStr.replace('_', '-'))
            } catch (e: Exception) {
                logger.warning("Invalid locale format: $localeStr")
                null
            }
        }.toSet()
    }

    /**
     * Gets a Bedrock-specific translation
     */
    private fun getBedrockTranslation(locale: Locale, key: String, vararg args: Any?): String {
        // Try exact locale match first
        var properties = bedrockTranslations[locale.toString()]

        // Try language-only match
        if (properties == null) {
            properties = bedrockTranslations[locale.language]
        }

        // Try base locale
        if (properties == null) {
            properties = bedrockTranslations["en"] // English fallback
        }

        if (properties == null) {
            return key // Return key if no translations found
        }

        val pattern = properties.getProperty(key) ?: return key

        return try {
            if (args.isNotEmpty()) {
                MessageFormat.format(pattern, *args)
            } else {
                pattern
            }
        } catch (e: Exception) {
            logger.warning("Error formatting Bedrock translation for key '$key': ${e.message}")
            pattern
        }
    }

    /**
     * Loads Bedrock-specific translation files
     */
    private fun loadBedrockTranslations() {
        val langFolder = File(dataFolder, "lang")
        val bedrockFolder = File(langFolder, "bedrock")

        if (!bedrockFolder.exists()) {
            logger.info("Creating Bedrock translations folder: ${bedrockFolder.absolutePath}")
            bedrockFolder.mkdirs()

            // Create default English translations
            createDefaultEnglishTranslations(bedrockFolder)
        }

        // Load all .properties files in the bedrock folder
        bedrockFolder.listFiles { file -> file.isFile && file.extension == "properties" }?.forEach { file ->
            try {
                val properties = Properties()
                file.inputStream().use { properties.load(it) }

                // Extract locale from filename (e.g., "forms.properties" -> "en", "forms_ar.properties" -> "ar")
                val fileName = file.nameWithoutExtension
                val locale = if (fileName.contains("_")) {
                    // Handle files like "forms_ar.properties" -> "ar"
                    fileName.substringAfter("_")
                } else {
                    // Default to English for files like "forms.properties"
                    "en"
                }

                bedrockTranslations[locale] = properties
                logger.info("Loaded Bedrock translations for locale: $locale from file: ${file.name}")
            } catch (e: Exception) {
                logger.warning("Failed to load Bedrock translations from ${file.name}: ${e.message}")
            }
        }

        logger.info("Loaded ${bedrockTranslations.size} Bedrock translation files")
    }

    /**
     * Creates default English translations for common Bedrock form elements
     */
    private fun createDefaultEnglishTranslations(bedrockFolder: File) {
        // Copy the default forms.properties file from resources
        try {
            val formsResource = this::class.java.classLoader.getResource("lang/bedrock/forms.properties")
            if (formsResource != null) {
                val defaultTranslations = formsResource.readText()
                val defaultFile = File(bedrockFolder, "forms.properties")
                defaultFile.writeText(defaultTranslations)
                logger.info("Created default English Bedrock translations from resource file")
            } else {
                logger.warning("Could not find default forms.properties resource, creating basic translations")
                createBasicEnglishTranslations(bedrockFolder)
            }
        } catch (e: Exception) {
            logger.warning("Failed to create default English translations from resource: ${e.message}")
            createBasicEnglishTranslations(bedrockFolder)
        }

        // Create Arabic translations as an example of RTL support
        createArabicTranslations(bedrockFolder)
    }

    /**
     * Creates basic English translations if resource loading fails
     */
    private fun createBasicEnglishTranslations(bedrockFolder: File) {
        val basicTranslations = """
            # Basic Bedrock Form Translations
            form.title.guild.settings=Guild Settings
            form.title.guild.bank=Guild Bank
            form.title.confirmation=Confirmation
            form.button.save=Save
            form.button.cancel=Cancel
            validation.required=This field is required
        """.trimIndent()

        val defaultFile = File(bedrockFolder, "forms.properties")
        try {
            defaultFile.writeText(basicTranslations)
            logger.info("Created basic English Bedrock translations")
        } catch (e: Exception) {
            logger.warning("Failed to create basic English translations: ${e.message}")
        }
    }

    /**
     * Creates Arabic translations to demonstrate RTL support
     */
    private fun createArabicTranslations(bedrockFolder: File) {
        // Copy the Arabic forms file from resources
        try {
            val arabicResource = this::class.java.classLoader.getResource("lang/bedrock/forms_ar.properties")
            if (arabicResource != null) {
                val arabicTranslations = arabicResource.readText()
                val arabicFile = File(bedrockFolder, "forms_ar.properties")
                arabicFile.writeText(arabicTranslations)
                logger.info("Created Arabic Bedrock translations from resource file")
            } else {
                logger.warning("Could not find Arabic forms resource, creating basic Arabic translations")
                createBasicArabicTranslations(bedrockFolder)
            }
        } catch (e: Exception) {
            logger.warning("Failed to create Arabic translations from resource: ${e.message}")
            createBasicArabicTranslations(bedrockFolder)
        }
    }

    /**
     * Creates basic Arabic translations if resource loading fails
     */
    private fun createBasicArabicTranslations(bedrockFolder: File) {
        val basicArabicTranslations = """
            # Basic Arabic Bedrock Form Translations
            form.title.guild.settings=إعدادات النقابة
            form.title.guild.bank=بنك النقابة
            form.title.confirmation=تأكيد
            form.button.save=حفظ
            form.button.cancel=إلغاء
            validation.required=هذا الحقل مطلوب
        """.trimIndent()

        val arabicFile = File(bedrockFolder, "forms_ar.properties")
        try {
            arabicFile.writeText(basicArabicTranslations)
            logger.info("Created basic Arabic Bedrock translations")
        } catch (e: Exception) {
            logger.warning("Failed to create basic Arabic translations: ${e.message}")
        }
    }
}
