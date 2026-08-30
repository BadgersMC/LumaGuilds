package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.gold.GuildGoldCapacity
import net.lumalyte.lg.domain.gold.GuildGoldPolicy
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class GuildGoldServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildGoldRepositorySQL
    private lateinit var service: GuildGoldService
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private var policy = policy()
    private var capacity = GuildGoldCapacity(tier = 1_000, permanent = 0)

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = GuildGoldRepositorySQL(storage)
        service = GuildGoldService(
            repository = repository,
            policyProvider = GuildGoldPolicyProvider { policy },
            capacityProvider = GuildGoldCapacityProvider { capacity }
        )
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @Test
    fun `system credit changes canonical balance and records its transaction`() {
        val transactionId = UUID.randomUUID()

        val result = service.creditSystem(
            transactionId,
            guildId,
            actorId,
            600,
            GuildGoldRoute.SYSTEM,
            "War refund"
        )

        assertEquals(GuildGoldResult.Applied(transactionId, 0, 600, 0), result)
        assertEquals(600, service.balance(guildId))
        assertEquals(transactionId, repository.findOperation(transactionId)?.mutation?.transactionId)
    }

    @Test
    fun `credit above effective capacity leaves canonical balance unchanged`() {
        val result = service.creditSystem(
            UUID.randomUUID(),
            guildId,
            actorId,
            1_001,
            GuildGoldRoute.ADMIN,
            "Admin credit"
        )

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED), result)
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `permanent capacity benefit is included under the global ceiling`() {
        capacity = GuildGoldCapacity(tier = 800, permanent = 300)

        val result = service.creditSystem(
            UUID.randomUUID(),
            guildId,
            actorId,
            1_000,
            GuildGoldRoute.SYSTEM,
            "Reward"
        )

        assertTrue(result is GuildGoldResult.Applied)
        assertEquals(1_100, service.capacity(guildId))
    }

    @Test
    fun `system debit destroys gold without charging a player fee`() {
        service.creditSystem(UUID.randomUUID(), guildId, actorId, 800, GuildGoldRoute.SYSTEM, "Seed")
        val transactionId = UUID.randomUUID()

        val result = service.debitSystem(transactionId, guildId, actorId, 250, "War declaration")

        assertEquals(GuildGoldResult.Applied(transactionId, 800, 550, 0), result)
        assertEquals(550, service.balance(guildId))
    }

    @Test
    fun `insufficient system debit leaves balance unchanged`() {
        val result = service.debitSystem(UUID.randomUUID(), guildId, actorId, 1, "Purchase")

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.INSUFFICIENT_FUNDS), result)
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `frozen bank rejects credits and debits`() {
        assertTrue(repository.setFrozen(guildId, true, actorId, "Security review"))

        val credit = service.creditSystem(
            UUID.randomUUID(), guildId, actorId, 100, GuildGoldRoute.SYSTEM, "Credit"
        )
        val debit = service.debitSystem(UUID.randomUUID(), guildId, actorId, 100, "Debit")

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.FROZEN), credit)
        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.FROZEN), debit)
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `suspicious credit freezes bank when auto freeze is enabled`() {
        policy = policy(autoFreeze = true)

        val result = service.creditSystem(
            UUID.randomUUID(),
            guildId,
            actorId,
            50_000,
            GuildGoldRoute.ADMIN,
            "Suspicious credit"
        )

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.SUSPICIOUS_FROZEN), result)
        assertTrue(repository.isFrozen(guildId))
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `suspicious threshold does not freeze bank when auto freeze is disabled`() {
        capacity = GuildGoldCapacity(tier = 100_000, permanent = 0)

        val result = service.creditSystem(
            UUID.randomUUID(),
            guildId,
            actorId,
            50_000,
            GuildGoldRoute.ADMIN,
            "Large credit"
        )

        assertTrue(result is GuildGoldResult.Applied)
        assertFalse(repository.isFrozen(guildId))
    }

    @Test
    fun `zero and negative amounts are rejected before persistence`() {
        assertEquals(
            GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT),
            service.creditSystem(UUID.randomUUID(), guildId, actorId, 0, GuildGoldRoute.SYSTEM, "Zero")
        )
        assertEquals(
            GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT),
            service.debitSystem(UUID.randomUUID(), guildId, actorId, -1, "Negative")
        )
        assertEquals(0, service.balance(guildId))
    }

    private fun policy(autoFreeze: Boolean = false) = GuildGoldPolicy(
        minDeposit = 1,
        maxDeposit = 100_000,
        withdrawalPercent = 0.5,
        dailyWithdrawalLimit = 50_000,
        depositFeePercent = 0.01,
        withdrawalFeePercent = 0.02,
        maxDepositFee = 128,
        maxWithdrawalFee = 15,
        globalCapacity = 1_000_000,
        suspiciousThreshold = 50_000,
        autoFreezeSuspicious = autoFreeze
    )
}
