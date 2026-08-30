package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.domain.gold.GuildGoldCapacity
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldPolicy
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class GuildGoldPersonalTransferTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var sqlRepository: GuildGoldRepositorySQL
    private lateinit var economy: FakePersonalEconomy
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val periodStart = 1_788_048_000_000L

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        sqlRepository = GuildGoldRepositorySQL(storage)
        economy = FakePersonalEconomy(available = true, startingBalance = 1_000)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @Test
    fun `unavailable economy rejects without changing either balance`() {
        economy.available = false
        val service = service(sqlRepository)

        val result = service.depositPersonal(request(amount = 100))

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.EXTERNAL_UNAVAILABLE), result)
        assertEquals(1_000, economy.currentBalance)
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `unauthorized deposit rejects before touching either balance`() {
        val service = service(sqlRepository, authorized = false)

        val result = service.depositPersonal(request(amount = 100))

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.UNAUTHORIZED), result)
        assertEquals(1_000, economy.balance(playerId))
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `personal deposit charges fee once and credits the requested guild amount`() {
        val service = service(sqlRepository)
        val transactionId = UUID.randomUUID()

        val result = service.depositPersonal(request(transactionId, amount = 100))

        assertEquals(GuildGoldResult.Applied(transactionId, 0, 100, 1), result)
        assertEquals(899, economy.balance(playerId))
        assertEquals(100, service.balance(guildId))

        val duplicate = service.depositPersonal(request(transactionId, amount = 100))
        assertEquals(result, duplicate)
        assertEquals(899, economy.balance(playerId))
        assertEquals(100, service.balance(guildId))
    }

    @Test
    fun `canonical credit failure refunds the exact player debit`() {
        val rejecting = RejectingApplyRepository(sqlRepository, GuildGoldRejection.CAPACITY_EXCEEDED)
        val service = service(rejecting)

        val result = service.depositPersonal(request(amount = 100))

        assertTrue(result is GuildGoldResult.Failed && result.compensationSucceeded)
        assertEquals(1_000, economy.balance(playerId))
        assertEquals(0, sqlRepository.getBalance(guildId))
    }

    @Test
    fun `personal withdrawal debits amount plus fee and pays requested amount`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        economy.currentBalance = 0
        val transactionId = UUID.randomUUID()

        val result = service.withdrawPersonal(request(transactionId, amount = 100))

        assertEquals(GuildGoldResult.Applied(transactionId, 500, 398, 2), result)
        assertEquals(100, economy.balance(playerId))
        assertEquals(398, service.balance(guildId))
        assertEquals(100, sqlRepository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `failed personal payout restores amount and fee to guild`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        economy.currentBalance = 0
        economy.failCredits = true

        val result = service.withdrawPersonal(request(amount = 100))

        assertTrue(result is GuildGoldResult.Failed && result.compensationSucceeded)
        assertEquals(0, economy.balance(playerId))
        assertEquals(500, service.balance(guildId))
        assertEquals(0, sqlRepository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `withdrawal above remaining daily allowance is rejected before payout`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 80_000, GuildGoldRoute.SYSTEM, "Seed")
        sqlRepository.apply(
            GuildGoldMutation(
                UUID.randomUUID(), guildId, playerId, GuildGoldRoute.SYSTEM,
                net.lumalyte.lg.domain.gold.GuildGoldDirection.DEBIT,
                amount = 45_000, fee = 0, description = "Earlier withdrawal"
            ),
            capacity = 100_000,
            periodStartEpochMs = periodStart
        )
        economy.currentBalance = 0

        val result = service.withdrawPersonal(request(amount = 5_001))

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.DAILY_LIMIT), result)
        assertEquals(0, economy.balance(playerId))
        assertEquals(35_000, service.balance(guildId))
    }

    private fun service(
        repository: GuildGoldRepository,
        authorized: Boolean = true
    ) = GuildGoldService(
        repository = repository,
        policyProvider = GuildGoldPolicyProvider { policy() },
        capacityProvider = GuildGoldCapacityProvider { GuildGoldCapacity(100_000, 0) },
        authorization = object : GuildGoldAuthorizationPort {
            override fun canDeposit(playerId: UUID, guildId: UUID) = authorized
            override fun canWithdraw(playerId: UUID, guildId: UUID) = authorized
        },
        personalEconomy = economy,
        periodStartProvider = { periodStart }
    )

    private fun request(
        transactionId: UUID = UUID.randomUUID(),
        amount: Long
    ) = PersonalGoldRequest(transactionId, guildId, playerId, amount, "Personal transfer")

    private fun policy() = GuildGoldPolicy(
        minDeposit = 1,
        maxDeposit = 100_000,
        withdrawalPercent = 0.5,
        dailyWithdrawalLimit = 50_000,
        depositFeePercent = 0.01,
        withdrawalFeePercent = 0.02,
        maxDepositFee = 128,
        maxWithdrawalFee = 15,
        globalCapacity = 100_000,
        suspiciousThreshold = 50_000,
        autoFreezeSuspicious = false
    )

    private class FakePersonalEconomy(
        var available: Boolean,
        startingBalance: Long
    ) : PersonalEconomyPort {
        var currentBalance = startingBalance
        var failCredits = false

        override fun isAvailable() = available
        override fun balance(playerId: UUID): Long? = if (available) currentBalance else null
        override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
            if (!available) return ExternalTransferResult.Unavailable
            if (currentBalance < amount) return ExternalTransferResult.Rejected("insufficient")
            currentBalance -= amount
            return ExternalTransferResult.Applied
        }

        override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
            if (!available) return ExternalTransferResult.Unavailable
            if (failCredits) return ExternalTransferResult.Failed("payout failed")
            currentBalance += amount
            return ExternalTransferResult.Applied
        }
    }

    private class RejectingApplyRepository(
        private val delegate: GuildGoldRepository,
        private val rejection: GuildGoldRejection
    ) : GuildGoldRepository by delegate {
        override fun apply(
            mutation: GuildGoldMutation,
            capacity: Long,
            periodStartEpochMs: Long?
        ): GuildGoldResult = GuildGoldResult.Rejected(rejection)
    }
}
