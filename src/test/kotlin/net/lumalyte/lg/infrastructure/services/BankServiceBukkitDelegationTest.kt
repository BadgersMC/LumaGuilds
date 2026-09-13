package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankServiceBukkitDelegationTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var bank: BankServiceBukkit
    private lateinit var gold: GuildGoldService
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private var personalBalance = 1_000L
    private var dailyLimit = 1_000L
    private var withdrawalFee = 0.0

    @BeforeEach fun setup() {
        MockBukkit.mock()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        gold = GuildGoldService(
            GuildGoldRepositorySQL(storage),
            GuildGoldPolicyProvider { GuildGoldPolicy(1, 1_000, 1.0, dailyLimit, 0.0, withdrawalFee, 0, 15, 1_000, 1_000, false) },
            GuildGoldCapacityProvider { GuildGoldCapacity(500, 0) },
            personalEconomy = object : PersonalEconomyPort {
                override fun isAvailable() = true
                override fun balance(playerId: UUID) = personalBalance
                override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
                    if (amount > personalBalance) return ExternalTransferResult.Rejected("insufficient")
                    personalBalance -= amount
                    return ExternalTransferResult.Applied
                }
                override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
                    personalBalance += amount
                    return ExternalTransferResult.Applied
                }
            },
        )
        bank = BankServiceBukkit(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), goldService = gold)
    }

    @AfterEach fun cleanup() {
        storage.connection.close()
        MockBukkit.unmock()
    }

    @Test fun `bank reads canonical balance not a stale vault cache`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 300, GuildGoldRoute.SYSTEM, "seed")
        assertEquals(300, bank.getBalance(guildId))
        assertEquals(listOf(guildId to 300), bank.getTopBalances(1))
    }

    @Test fun `withdrawal preview uses canonical fee policy`() {
        withdrawalFee = 0.02
        assertEquals(2, bank.calculateWithdrawalFee(guildId, 100))
    }

    @Test fun `withdrawal maximum subtracts already used daily allowance`() {
        dailyLimit = 100
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        assertTrue(bank.withdrawOutcome(guildId, actorId, 80) is BankWithdrawalResult.Completed)
        assertEquals(20, bank.getMaxWithdrawalAmount(guildId, actorId))
    }

    @Test fun `personal transfers use canonical service without a legacy Vault provider`() {
        assertTrue(bank.deposit(guildId, actorId, 200, "deposit") != null)
        assertEquals(800, personalBalance)
        assertEquals(200, gold.balance(guildId))
        assertTrue(bank.withdrawOutcome(guildId, actorId, 100, "withdraw") is BankWithdrawalResult.Completed)
        assertEquals(900, personalBalance)
        assertEquals(100, gold.balance(guildId))
    }

    @Test fun `system credit enforces capacity and replays one transaction only`() {
        val transactionId = UUID.randomUUID()
        assertTrue(bank.creditToGuildBank(transactionId, guildId, 400, "credit"))
        assertTrue(bank.creditToGuildBank(transactionId, guildId, 400, "credit"))
        assertFalse(bank.creditToGuildBank(UUID.randomUUID(), guildId, 200, "overflow"))
        assertEquals(400, gold.balance(guildId))
    }

    @Test fun `system debit uses one fee free transaction`() {
        gold.creditSystem(UUID.randomUUID(), guildId, actorId, 400, GuildGoldRoute.SYSTEM, "seed")
        val transactionId = UUID.randomUUID()
        assertTrue(bank.deductFromGuildBank(transactionId, guildId, 100, "cost"))
        assertTrue(bank.deductFromGuildBank(transactionId, guildId, 100, "cost"))
        assertEquals(300, gold.balance(guildId))
    }
}
