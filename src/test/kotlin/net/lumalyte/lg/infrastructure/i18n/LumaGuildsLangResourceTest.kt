package net.lumalyte.lg.infrastructure.i18n

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class LumaGuildsLangResourceTest {

    private fun loadLocale(): YamlConfiguration {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("lang/en_US.yml"))
        return stream.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }
    }

    @Test
    fun `claim menus and guild conversion messages coexist in one locale`() {
        val locale = loadLocale()

        assertNotNull(locale.getString("menu.management.title"))
        assertEquals(
            "<green>✅ Claim converted to guild ownership.",
            locale.getString("bedrock.claim_management.feedback.converted")
        )
    }

    @Test
    fun `rank no permission key remains a string key`() {
        val locale = loadLocale()

        assertEquals(
            "<red>[ERROR] You do not have permission to manage ranks.",
            locale.getString("bedrock.rank_management.error.no_permission")
        )
    }

    @Test
    fun `confirmation yes and no remain string keys`() {
        val locale = loadLocale()

        assertNotNull(locale.getConfigurationSection("menu.confirmation.item"))
        assertEquals("No", locale.getString("menu.confirmation.item.no.name"))
        assertEquals("Yes", locale.getString("menu.confirmation.item.yes.name"))
    }
}
