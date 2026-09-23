package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SpawnBannerWiringTest {
    @Test
    fun adminCommandCreatesRankedCategorizedBannerItems() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt"
        ).readText()
        assertTrue(source.contains("\"spawnbanner\" -> handleSpawnBanner(sender, args)"))
        assertTrue(source.contains("spawnBannerService.createItem(rank, category)"))
        assertTrue(source.contains("SpawnBannerCategory.entries"))
        assertTrue(source.contains("\"refresh\" ->"))
        assertTrue(source.contains("\"list\" ->"))
    }

    @Test
    fun listenerRefreshesOnLeaderboardAndGuildBannerChanges() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/SpawnBannerListener.kt"
        ).readText()
        assertTrue(source.contains("GuildLeaderboardRankChangeEvent"))
        assertTrue(source.contains("service.refreshLeaderboard(event.leaderboardType, event.period)"))
        assertTrue(source.contains("GuildBannerSetEvent"))
        assertTrue(source.contains("service.refreshGuild(event.guildId)"))
    }

    @Test
    fun rendererPreservesBannerOrientationAndFallsBackToWhite() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/SpawnBannerServiceBukkit.kt"
        ).readText()
        assertTrue(source.contains("previousData is Rotatable && newData is Rotatable"))
        assertTrue(source.contains("previousData is Directional && newData is Directional"))
        assertTrue(source.contains("ItemStack(Material.WHITE_BANNER)"))
        assertTrue(source.contains("REFRESH_TICKS = 20L * 60L"))
    }

    @Test
    fun pluginStartsAndStopsDynamicBannerReconciliation() {
        val source = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        assertTrue(source.contains("SpawnBannerListener"))
        assertTrue(source.contains("SpawnBannerServiceBukkit>().start()"))
        assertTrue(source.contains("SpawnBannerServiceBukkit>()?.stop()"))
    }
}
