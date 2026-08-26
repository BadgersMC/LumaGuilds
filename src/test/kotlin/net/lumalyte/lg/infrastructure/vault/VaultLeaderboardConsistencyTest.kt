package net.lumalyte.lg.infrastructure.vault

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.config.VaultConfig
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class VaultLeaderboardConsistencyTest {
    @Test
    fun `leaderboard overlays buffered in-memory balances before ranking`() {
        val changedGuild = UUID.randomUUID()
        val persistedLeader = UUID.randomUUID()
        val repository = mockk<GuildVaultRepository>(relaxed = true)
        every { repository.getVaultInventory(any()) } returns emptyMap()
        every { repository.getGoldBalance(changedGuild) } returns 100L
        every { repository.getTopGoldBalances(any()) } returns listOf(
            persistedLeader to 500L,
            changedGuild to 100L
        )
        val manager = VaultInventoryManager(repository, vaultConfig = mockk<VaultConfig>(relaxed = true))

        manager.setGoldBalance(changedGuild, 1_000L)

        assertEquals(
            listOf(changedGuild to 1_000L, persistedLeader to 500L),
            manager.getTopGoldBalances(10)
        )
    }

    @Test
    fun `leaderboard backfills persisted candidates when a loaded balance falls`() {
        val changedGuild = UUID.randomUUID()
        val secondGuild = UUID.randomUUID()
        val thirdGuild = UUID.randomUUID()
        val persisted = listOf(
            changedGuild to 1_000L,
            secondGuild to 900L,
            thirdGuild to 800L
        )
        val repository = mockk<GuildVaultRepository>(relaxed = true)
        every { repository.getVaultInventory(any()) } returns emptyMap()
        every { repository.getGoldBalance(changedGuild) } returns 1_000L
        every { repository.getTopGoldBalances(any()) } answers { persisted.take(firstArg()) }
        val manager = VaultInventoryManager(repository, vaultConfig = mockk<VaultConfig>(relaxed = true))

        manager.setGoldBalance(changedGuild, 100L)

        assertEquals(
            listOf(secondGuild to 900L, thirdGuild to 800L),
            manager.getTopGoldBalances(2)
        )
    }
}
