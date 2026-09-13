package net.lumalyte.lg.infrastructure.vault

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** Committed balance changes must remain observable even when ancillary audit logging fails. */
internal class VaultGoldAuditFailureTest {
    /** The payout caller must receive the successful debit, not an exception after funds moved. */
    @Test
    fun auditFailureDoesNotHideDebit() {
        val repository = mockk<GuildVaultRepository>(relaxed = true)
        every { repository.getVaultInventory(any()) } returns emptyMap()
        every { repository.getGoldBalance(any()) } returns INITIAL_BALANCE
        val audit = mockk<VaultTransactionLogger>(relaxed = true)
        every { audit.logGoldTransaction(any(), any(), any(), any()) } throws IllegalStateException("Audit unavailable")
        val manager = VaultInventoryManager(repository, audit, VaultConfig())
        val guild = UUID.randomUUID()
        manager.getOrLoadVault(guild).setGold(2_000L)
        assertEquals(INITIAL_BALANCE, manager.getGoldBalance(guild))
    }

    @Test
    fun inventoryManagerCannotMutateCurrency() {
        val forbidden = setOf("depositGold", "withdrawGold", "setGoldBalance", "setGoldBalanceWithBroadcast")
        kotlin.test.assertTrue(VaultInventoryManager::class.java.methods.none { it.name in forbidden })
    }

    private companion object {
        const val INITIAL_BALANCE = 1_000L
    }
}
