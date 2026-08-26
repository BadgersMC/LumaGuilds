package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.Locale
import java.util.logging.Logger
import kotlin.test.assertEquals

class BedrockLocalizationTest {
    @Test
    fun `Floodgate language code takes precedence over the Bukkit locale`() {
        val player = mockk<Player>()
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId
        every { player.name } returns "BedrockPlayer"
        every { player.locale() } returns Locale.ENGLISH
        val service = BedrockLocalizationServiceFloodgate(Logger.getAnonymousLogger()) { id ->
            if (id == playerId) "fr_FR" else null
        }

        assertEquals(Locale.FRANCE, service.getBedrockLocale(player))
    }

    @Test
    fun `Minecraft locale is used when Floodgate has no language code`() {
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.locale() } returns Locale.CANADA_FRENCH
        val service = BedrockLocalizationServiceFloodgate(Logger.getAnonymousLogger()) { null }

        assertEquals(Locale.CANADA_FRENCH, service.getBedrockLocale(player))
    }

    @Test
    fun `Bedrock locale service retains direction formatting only`() {
        val service = BedrockLocalizationServiceFloodgate(Logger.getAnonymousLogger())

        assertEquals("text", service.formatForRTL("text", Locale.ENGLISH))
        assertEquals("\u200Ftext\u200E", service.formatForRTL("text", Locale.forLanguageTag("ar")))
    }
}
