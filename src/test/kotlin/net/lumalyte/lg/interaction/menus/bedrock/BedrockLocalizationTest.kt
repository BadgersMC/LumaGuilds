package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.logging.Logger
import kotlin.test.assertEquals

class BedrockLocalizationTest {
    @Test
    fun `Bedrock locale service retains direction formatting only`() {
        val service = BedrockLocalizationServiceFloodgate(Logger.getAnonymousLogger())

        assertEquals("text", service.formatForRTL("text", Locale.ENGLISH))
        assertEquals("\u200Ftext\u200E", service.formatForRTL("text", Locale.forLanguageTag("ar")))
    }
}
