package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

internal class GuildShopXpStartupTest {
    @Test
    fun startupPublishesShopXp() {
        // Published by the single runtime's service phase (PaperGuildRuntime.publishServices).
        val source = File("src/main/kotlin/net/lumalyte/lg/di/runtime/PaperGuildRuntime.kt").readText()
        val publish = source.substringAfter("internal fun publishGuildServices(")
        assertTrue(publish.contains("registerGuildShopXpApi("), "startup must publish Market's XP service")
        assertTrue(source.contains("Bukkit.getServicesManager().unregisterAll(plugin)"), "Plugin disable must remove its providers")
    }
}
