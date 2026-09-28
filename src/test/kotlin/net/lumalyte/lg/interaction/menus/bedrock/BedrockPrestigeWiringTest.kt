package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockPrestigeWiringTest {
    @Test
    fun chapter2BedrockProgressionExposesPrestige() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildProgressionInfoMenu.kt"
        ).readText()
        assertTrue(source.contains("GuildPrestigeService"))
        assertTrue(source.contains("prestige.overview(guild.id)"))
        assertTrue(source.contains("BedrockPrestigeSelectionMenu"))
        assertFalse(source.contains("future update", ignoreCase = true))
    }

    @Test
    fun bedrockPrestigeSelectionQuotesThroughSharedService() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockPrestigeSelectionMenu.kt"
        ).readText()
        assertTrue(source.contains("prestige.overview(guild.id)"))
        assertTrue(source.contains("prestige.quote(player.uniqueId, guild.id, rewardId)"))
        assertTrue(source.contains("overview.currentLevel == 100"))
        assertTrue(source.contains("overview.prestigeCount < overview.maxPrestigeCount"))
        assertTrue(source.contains("BedrockPrestigeConfirmationMenu"))
    }

    @Test
    fun bedrockPrestigeConfirmationReusesSameQuote() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockPrestigeConfirmationMenu.kt"
        ).readText()
        assertTrue(source.contains("private val quote: PrestigeQuote"))
        assertTrue(source.contains("prestige.confirm(player.uniqueId, quote)"))
        assertTrue(source.contains("is PrestigeResult.Failed -> open()"))
        assertTrue(source.contains("Bukkit.getScheduler().runTask"))
    }
}