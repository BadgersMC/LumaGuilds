package net.lumalyte.lg

import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Test class for Bedrock localization functionality
 */
class BedrockLocalizationTest {

    @Mock
    private lateinit var mockPlayer: Player

    @Mock
    private lateinit var mockLocalizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider

    private lateinit var bedrockLocalization: BedrockLocalizationService

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Create a temporary directory for testing
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bedrock_localization_test")
        tempDir.mkdirs()

        bedrockLocalization = BedrockLocalizationServiceFloodgate(
            tempDir,
            mockLocalizationProvider,
            java.util.logging.Logger.getLogger("TestLogger")
        )
    }

    @Test
    fun `test RTL locale detection`() {
        // Test Arabic (RTL)
        assertTrue(bedrockLocalization.isRTLLocale(Locale("ar")))
        assertTrue(bedrockLocalization.isRTLLocale(Locale("ar", "SA")))

        // Test Hebrew (RTL)
        assertTrue(bedrockLocalization.isRTLLocale(Locale("he")))
        assertTrue(bedrockLocalization.isRTLLocale(Locale("he", "IL")))

        // Test Persian (RTL)
        assertTrue(bedrockLocalization.isRTLLocale(Locale("fa")))
        assertTrue(bedrockLocalization.isRTLLocale(Locale("fa", "IR")))

        // Test Urdu (RTL)
        assertTrue(bedrockLocalization.isRTLLocale(Locale("ur")))
        assertTrue(bedrockLocalization.isRTLLocale(Locale("ur", "PK")))

        // Test Yiddish (RTL)
        assertTrue(bedrockLocalization.isRTLLocale(Locale("yi")))
        assertTrue(bedrockLocalization.isRTLLocale(Locale("ji")))

        // Test LTR languages
        assertFalse(bedrockLocalization.isRTLLocale(Locale.ENGLISH))
        assertFalse(bedrockLocalization.isRTLLocale(Locale.FRENCH))
        assertFalse(bedrockLocalization.isRTLLocale(Locale.GERMAN))
        assertFalse(bedrockLocalization.isRTLLocale(Locale("es")))
        assertFalse(bedrockLocalization.isRTLLocale(Locale("zh")))
        assertFalse(bedrockLocalization.isRTLLocale(Locale("ja")))
    }

    @Test
    fun `test text direction detection`() {
        // Test RTL locale
        val rtlLocale = Locale("ar")
        assertEquals(BedrockLocalizationService.TextDirection.RTL,
                    bedrockLocalization.getTextDirection(rtlLocale))

        // Test LTR locale
        val ltrLocale = Locale.ENGLISH
        assertEquals(BedrockLocalizationService.TextDirection.LTR,
                    bedrockLocalization.getTextDirection(ltrLocale))
    }

    @Test
    fun `test RTL text formatting`() {
        val rtlLocale = Locale("ar")
        val ltrLocale = Locale.ENGLISH
        val testText = "Hello World"

        // RTL text should have markers
        val rtlFormatted = bedrockLocalization.formatForRTL(testText, rtlLocale)
        assertTrue(rtlFormatted.contains("\u200F")) // RTL marker
        assertTrue(rtlFormatted.contains("\u200E")) // LTR marker

        // LTR text should be unchanged
        val ltrFormatted = bedrockLocalization.formatForRTL(testText, ltrLocale)
        assertEquals(testText, ltrFormatted)
    }

    @Test
    fun `test supported locales includes created locales`() {
        val supportedLocales = bedrockLocalization.getSupportedLocales()

        // Should include English by default (forms.properties)
        assertTrue(supportedLocales.any { it.language == "en" })

        // Should include Arabic if the file was created
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bedrock_localization_test")
        val arabicFile = File(tempDir, "lang/bedrock/forms_ar.properties")
        if (arabicFile.exists()) {
            assertTrue(supportedLocales.any { it.language == "ar" })
        }
    }

    @Test
    fun `test fallback to English when locale not found`() {
        // Mock player locale
        whenever(mockPlayer.locale()).thenReturn(Locale.JAPANESE)

        // Mock the fallback localization provider
        whenever(mockLocalizationProvider.getConsole("test.key", *arrayOf<Any>()))
            .thenReturn("English Fallback")

        val result = bedrockLocalization.getBedrockString(mockPlayer, "test.key")

        // Should fall back to the regular localization
        assertEquals("English Fallback", result)
    }
}
