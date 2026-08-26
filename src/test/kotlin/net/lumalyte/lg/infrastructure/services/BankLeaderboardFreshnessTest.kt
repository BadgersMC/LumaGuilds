package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class BankLeaderboardFreshnessTest {
    @AfterEach
    fun tearDown() = unmockkStatic(Bukkit::class)

    @Test
    fun `leaderboard reads the manager on every request`() {
        mockkStatic(Bukkit::class)
        val server = mockk<Server>()
        val pluginManager = mockk<PluginManager>()
        every { Bukkit.getServer() } returns server
        every { server.pluginManager } returns pluginManager
        every { pluginManager.getPlugin("Vault") } returns null

        val firstGuild = UUID.randomUUID()
        val secondGuild = UUID.randomUUID()
        val vaultManager = mockk<VaultInventoryManager>()
        every { vaultManager.getTopGoldBalances(any()) } returnsMany listOf(
            listOf(firstGuild to 1_000L, secondGuild to 500L),
            listOf(secondGuild to 1_500L, firstGuild to 1_000L)
        )
        val service = BankServiceBukkit(
            mockk<BankRepository>(relaxed = true),
            mockk<MemberRepository>(relaxed = true),
            mockk<RankRepository>(relaxed = true),
            mockk<ProgressionRepository>(relaxed = true),
            mockk<ProgressionConfigService>(relaxed = true),
            mockk<ConfigService>(relaxed = true),
            mockk<GuildRepository>(relaxed = true),
            mockk<GuildService>(relaxed = true),
            vaultManager
        )

        assertEquals(listOf(firstGuild to 1_000, secondGuild to 500), service.getTopBalances(2))
        assertEquals(listOf(secondGuild to 1_500, firstGuild to 1_000), service.getTopBalances(2))
    }
}
