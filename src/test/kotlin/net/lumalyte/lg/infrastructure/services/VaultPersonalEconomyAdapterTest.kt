package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.ExternalTransferResult
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class VaultPersonalEconomyAdapterTest {
    private val playerId = UUID.randomUUID()
    private val player = mockk<OfflinePlayer>()

    @Test
    fun `missing provider reports unavailable`() {
        val adapter = VaultPersonalEconomyAdapter({ null }, { player })

        assertEquals(false, adapter.isAvailable())
        assertNull(adapter.balance(playerId))
        assertEquals(ExternalTransferResult.Unavailable, adapter.debit(playerId, 10))
        assertEquals(ExternalTransferResult.Unavailable, adapter.credit(playerId, 10))
    }

    @Test
    fun `integral provider balance converts without rounding`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returns 42.0
        val adapter = VaultPersonalEconomyAdapter({ economy }, { player })

        assertEquals(42, adapter.balance(playerId))
    }

    @Test
    fun `fractional provider balance is rejected`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returns 42.5
        val adapter = VaultPersonalEconomyAdapter({ economy }, { player })

        assertNull(adapter.balance(playerId))
    }

    @Test
    fun `successful Vault debit maps to applied`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returns 100.0
        every { economy.withdrawPlayer(player, 25.0) } returns EconomyResponse(
            25.0,
            75.0,
            EconomyResponse.ResponseType.SUCCESS,
            null
        )
        val adapter = VaultPersonalEconomyAdapter({ economy }, { player })

        assertEquals(ExternalTransferResult.Applied, adapter.debit(playerId, 25))
    }

    @Test
    fun `failed Vault credit preserves provider error`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returns 0.0
        every { economy.depositPlayer(player, 25.0) } returns EconomyResponse(
            0.0,
            0.0,
            EconomyResponse.ResponseType.FAILURE,
            "account locked"
        )
        val adapter = VaultPersonalEconomyAdapter({ economy }, { player })

        assertEquals(
            ExternalTransferResult.Rejected("account locked"),
            adapter.credit(playerId, 25)
        )
    }

    @Test
    fun `payout exception with changed balance is ambiguous`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returnsMany listOf(0.0, 25.0)
        every { economy.depositPlayer(player, 25.0) } throws IllegalStateException("after credit")
        val result = VaultPersonalEconomyAdapter({ economy }, { player }).credit(playerId, 25)
        org.junit.jupiter.api.Assertions.assertTrue(result is ExternalTransferResult.Failed)
    }

    @Test
    fun `payout exception with unchanged balance is definitive rejection`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } returns 0.0
        every { economy.depositPlayer(player, 25.0) } throws IllegalStateException("before credit")
        val result = VaultPersonalEconomyAdapter({ economy }, { player }).credit(playerId, 25)
        org.junit.jupiter.api.Assertions.assertTrue(result is ExternalTransferResult.Rejected)
    }

    @Test
    fun `balance lookup exception prevents payout`() {
        val economy = mockk<Economy>()
        every { economy.getBalance(player) } throws UnsupportedOperationException("balance unavailable")
        val result = VaultPersonalEconomyAdapter({ economy }, { player }).credit(playerId, 25)
        org.junit.jupiter.api.Assertions.assertTrue(result is ExternalTransferResult.Rejected)
        io.mockk.verify(exactly = 0) { economy.depositPlayer(player, any<Double>()) }
    }

    @Test
    fun `amount that cannot round trip through Vault double is rejected`() {
        val economy = mockk<Economy>(relaxed = true)
        val adapter = VaultPersonalEconomyAdapter({ economy }, { player })

        assertEquals(
            ExternalTransferResult.Rejected("Amount cannot be represented exactly by Vault Economy"),
            adapter.debit(playerId, 9_007_199_254_740_993L)
        )
    }
}
