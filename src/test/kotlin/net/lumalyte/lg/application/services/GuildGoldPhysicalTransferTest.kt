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

class GuildGoldPhysicalTransferTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var sqlRepository: GuildGoldRepositorySQL
    private lateinit var physical: FakePhysicalGold
    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val periodStart = 1_788_048_000_000L

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        sqlRepository = GuildGoldRepositorySQL(storage)
        physical = FakePhysicalGold(availableValue = 1_000)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @Test
    fun `unknown commit retains unfinished credit and never restores or repeats reservation`() {
        physical.commitResult = PhysicalCommitResult.Unknown
        val subject = service(sqlRepository)
        val original = request(amount = 100)
        assertEquals(GuildGoldResult.Failed(original.transactionId, false), subject.depositPhysical(original))
        assertEquals(net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.BALANCE_APPLIED,
            sqlRepository.findOperation(original.transactionId)?.status)
        assertEquals(GuildGoldResult.Failed(original.transactionId, false), subject.depositPhysical(original))
        assertEquals(100, subject.balance(guildId))
        assertEquals(1, physical.reservedValues.size)
        assertEquals(0, physical.restoreCount)
    }

    @Test
    fun `proven nonconsumption reverses credit before restoring items`() {
        physical.commitResult = PhysicalCommitResult.NotConsumed
        physical.beforeRestore = { assertEquals(0, sqlRepository.getBalance(guildId)) }
        val subject = service(sqlRepository)
        val original = request(amount = 100)
        assertEquals(GuildGoldResult.Failed(original.transactionId, true), subject.depositPhysical(original))
        assertEquals(0, subject.balance(guildId))
        assertEquals(1, physical.restoreCount)
        subject.depositPhysical(original)
        assertEquals(1, physical.restoreCount)
    }

    @Test
    fun `physical deposit reserves amount plus fee and commits after canonical credit`() {
        val service = service(sqlRepository)
        val transactionId = UUID.randomUUID()

        val result = service.depositPhysical(request(transactionId, 100))

        assertEquals(GuildGoldResult.Applied(transactionId, 0, 100, 1), result)
        assertEquals(listOf(101L), physical.reservedValues)
        assertEquals(1, physical.commitCount)
        assertEquals(0, physical.restoreCount)
        assertEquals(100, service.balance(guildId))
    }

    @Test
    fun `capacity rejection happens before physical reservation`() {
        val service = service(sqlRepository, capacity = 50)

        val result = service.depositPhysical(request(amount = 100))

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED), result)
        assertTrue(physical.reservedValues.isEmpty())
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `failed canonical credit restores reserved physical items`() {
        val repository = RejectingApplyRepository(sqlRepository, GuildGoldRejection.CAPACITY_EXCEEDED)
        val service = service(repository)

        val result = service.depositPhysical(request(amount = 100))

        assertTrue(result is GuildGoldResult.Failed && result.compensationSucceeded)
        assertEquals(1, physical.restoreCount)
        assertEquals(0, physical.commitCount)
        assertEquals(0, service.balance(guildId))
    }

    @Test
    fun `duplicate physical deposit does not reserve items twice`() {
        val service = service(sqlRepository)
        val transactionId = UUID.randomUUID()
        val request = request(transactionId, 100)

        val first = service.depositPhysical(request)
        val second = service.depositPhysical(request)

        assertEquals(first, second)
        assertEquals(listOf(101L), physical.reservedValues)
        assertEquals(1, physical.commitCount)
    }

    @Test
    fun `physical withdrawal debits amount plus fee before delivering items`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        val transactionId = UUID.randomUUID()

        val result = service.withdrawPhysical(request(transactionId, 100))

        assertEquals(GuildGoldResult.Applied(transactionId, 500, 398, 2), result)
        assertEquals(listOf(100L), physical.deliveredValues)
        assertEquals(398, service.balance(guildId))
        assertEquals(100, sqlRepository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `duplicate completed physical withdrawal does not deliver twice`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        val transactionId = UUID.randomUUID()
        val request = request(transactionId, 100)

        val first = service.withdrawPhysical(request)
        val second = service.withdrawPhysical(request)

        assertEquals(first, second)
        assertEquals(listOf(100L), physical.deliveredValues)
        assertEquals(398, service.balance(guildId))
    }

    @Test
    fun `ambiguous balance-applied withdrawal fails closed without redelivery`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        val transactionId = UUID.randomUUID()
        val mutation = GuildGoldMutation(
            transactionId, guildId, playerId, GuildGoldRoute.PHYSICAL_ITEM,
            net.lumalyte.lg.domain.gold.GuildGoldDirection.DEBIT,
            amount = 100, fee = 2, description = "Physical transfer"
        )
        sqlRepository.prepare(mutation)
        sqlRepository.applyExternalDebit(mutation, capacity = 100_000, periodStartEpochMs = periodStart)

        val result = service.withdrawPhysical(request(transactionId, 100))

        assertEquals(GuildGoldResult.Failed(transactionId, false), result)
        assertTrue(physical.deliveredValues.isEmpty())
        assertEquals(398, service.balance(guildId))
    }

    @Test
    fun `failed physical delivery restores guild balance and daily allowance`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        physical.failDelivery = true

        val result = service.withdrawPhysical(request(amount = 100))

        assertTrue(result is GuildGoldResult.Failed && result.compensationSucceeded)
        assertEquals(500, service.balance(guildId))
        assertEquals(0, sqlRepository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `uncertain physical delivery preserves debit and blocks another delivery`() {
        val service = service(sqlRepository)
        service.creditSystem(UUID.randomUUID(), guildId, playerId, 500, GuildGoldRoute.SYSTEM, "Seed")
        physical.deliverThenFail = true
        val first = request(amount = 100)
        assertEquals(GuildGoldResult.Failed(first.transactionId, false), service.withdrawPhysical(first))
        assertEquals(GuildGoldResult.Failed(first.transactionId, false), service.withdrawPhysical(first))
        assertEquals(GuildGoldResult.Failed(first.transactionId, false), service.withdrawPhysical(request(amount = 100)))
        assertEquals(listOf(100L), physical.deliveredValues)
        assertEquals(398, service.balance(guildId))
        assertEquals(100, sqlRepository.getDailyWithdrawn(guildId, periodStart))
    }

    private fun service(
        repository: GuildGoldRepository,
        capacity: Long = 100_000
    ) = GuildGoldService(
        repository = repository,
        policyProvider = GuildGoldPolicyProvider { policy() },
        capacityProvider = GuildGoldCapacityProvider { GuildGoldCapacity(capacity, 0) },
        authorization = GuildGoldAuthorizationPort.AllowAll,
        physicalGold = physical,
        periodStartProvider = { periodStart }
    )

    private fun request(
        transactionId: UUID = UUID.randomUUID(),
        amount: Long
    ) = PhysicalGoldRequest(transactionId, guildId, playerId, amount, "Physical transfer")

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

    private class FakePhysicalGold(
        private var availableValue: Long
    ) : PhysicalGoldPort {
        val reservedValues = mutableListOf<Long>()
        val deliveredValues = mutableListOf<Long>()
        var commitCount = 0
        var restoreCount = 0
        var failDelivery = false
        var deliverThenFail = false
        var commitResult: PhysicalCommitResult = PhysicalCommitResult.Committed
        var beforeRestore: () -> Unit = {}

        override fun reserve(
            transactionId: UUID,
            playerId: UUID,
            requestedValue: Long
        ): PhysicalReservationResult {
            if (availableValue < requestedValue) return PhysicalReservationResult.Insufficient
            availableValue -= requestedValue
            reservedValues += requestedValue
            return PhysicalReservationResult.Reserved(
                PhysicalGoldReservation(transactionId, playerId, requestedValue)
            )
        }

        override fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult {
            commitCount++
            return commitResult
        }

        override fun restore(reservation: PhysicalGoldReservation): Boolean {
            beforeRestore()
            restoreCount++
            availableValue += reservation.value
            return true
        }

        override fun deliver(
            playerId: UUID,
            value: Long,
            transactionId: UUID
        ): ExternalTransferResult {
            if (failDelivery) return ExternalTransferResult.Rejected("inventory unavailable before delivery")
            deliveredValues += value
            if (deliverThenFail) return ExternalTransferResult.Failed("partial delivery outcome unknown")
            return ExternalTransferResult.Applied
        }
    }

    private class RejectingApplyRepository(
        private val delegate: GuildGoldRepository,
        private val rejection: GuildGoldRejection
    ) : GuildGoldRepository by delegate {
        override fun applyExternalCredit(mutation: GuildGoldMutation, capacity: Long): GuildGoldResult = GuildGoldResult.Rejected(rejection)
        override fun apply(
            mutation: GuildGoldMutation,
            capacity: Long,
            periodStartEpochMs: Long?
        ): GuildGoldResult = GuildGoldResult.Rejected(rejection)
    }
}
