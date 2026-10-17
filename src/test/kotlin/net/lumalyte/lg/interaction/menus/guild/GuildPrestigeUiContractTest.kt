package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildPrestigeUiContractTest {
    private val progressionSource = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildProgressionMenu.kt"
    ).toFile().readText()

    private val confirmationSource = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildPrestigeConfirmationMenu.kt"
    ).toFile().readText()

    @Test
    fun `prestige is a live level 100 flow rather than future update copy`() {
        assertTrue(progressionSource.contains("private val prestigeService: GuildPrestigeService by inject()"))
        assertTrue(progressionSource.contains("prestigeService.overview(guild.id)"))
        assertTrue(progressionSource.contains("prestigeService.quote(player.uniqueId, guild.id, reward.id)"))
        assertTrue(progressionSource.contains("fresh.currentLevel == 100"))
        assertFalse(progressionSource.contains("prestige.coming_soon"))
    }

    @Test
    fun `confirmation retries the same quote and uses the prestige service`() {
        assertTrue(confirmationSource.contains("private val quote: PrestigeQuote"))
        assertTrue(confirmationSource.contains("prestige.confirm(player.uniqueId, quote)"))
        assertTrue(confirmationSource.contains("is PrestigeResult.Failed"))
        assertTrue(confirmationSource.contains("open()"))
    }
}
