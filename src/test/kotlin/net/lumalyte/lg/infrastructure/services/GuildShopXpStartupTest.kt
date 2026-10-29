package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

internal class GuildShopXpStartupTest {
    @Test
    fun startupPublishesShopXp() {
        val source = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val enable = source.substringAfter("override fun onEnable()").substringBefore("override fun onDisable()")
        assertTrue(enable.contains("registerGuildShopXpApi("), "onEnable must publish Market's XP service")
        assertTrue(source.contains("unregisterAll(this)"), "Plugin disable must remove its providers")
    }
}
