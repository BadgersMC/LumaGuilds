package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GuildGoldRepositorySQLTest {
    private class MariaDBNamedWrapper(delegate: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>) :
        net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database> by delegate

    @Test fun `storage wrapper name cannot change SQL dialect`() {
        val wrapped = GuildGoldRepositorySQL(MariaDBNamedWrapper(storage))
        assertTrue(wrapped.apply(mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100), 1_000, null) is GuildGoldResult.Applied)
        assertEquals(100, wrapped.getBalance(guildId))
    }

    @Test fun `pending guard uses an index after repeated schema initialization`() {
        GuildGoldRepositorySQL(storage)
        storage.connection.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("EXPLAIN QUERY PLAN SELECT transaction_id FROM guild_gold_operations " +
                    "WHERE guild_id = 'test' AND route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM') " +
                    "AND status IN ('PREPARED', 'BALANCE_APPLIED')").use { rows ->
                    val plans = buildList { while (rows.next()) add(rows.getString("detail")) }
                    assertTrue(plans.any { it.contains("idx_guild_gold_pending") }, plans.toString())
                }
            }
        }
    }
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildGoldRepository
    private val guildId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val actorId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = GuildGoldRepositorySQL(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @Test
    fun `recovery abandons only unstarted or confirmed no-effect preparations`() {
        val ready = mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100).copy(route = GuildGoldRoute.PERSONAL_ACCOUNT)
        repository.prepare(ready)
        val restarted = GuildGoldRepositorySQL(storage)
        assertEquals(1, restarted.reconcileUnstarted(Long.MAX_VALUE))
        assertTrue(!restarted.beginExternal(ready.transactionId, "DEPOSIT"))
        val started = ready.copy(transactionId = UUID.randomUUID())
        restarted.prepare(started)
        assertTrue(restarted.beginExternal(started.transactionId, "DEPOSIT"))
        assertEquals(0, restarted.reconcileUnstarted(Long.MAX_VALUE))
        assertTrue(restarted.recordExternalOutcome(started.transactionId, false))
        assertEquals(1, restarted.reconcileUnstarted(Long.MAX_VALUE))
        assertEquals(0, restarted.getBalance(guildId))
    }

    @Test
    fun `confirmed deposit recovers once but admission retains completion guard`() {
        val deposit = mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100).copy(route = GuildGoldRoute.PERSONAL_ACCOUNT)
        repository.prepare(deposit)
        repository.beginExternal(deposit.transactionId, "DEPOSIT")
        repository.recordExternalOutcome(deposit.transactionId, true)
        val restarted = GuildGoldRepositorySQL(storage)
        assertTrue(restarted.recoverConfirmedCredit(deposit.transactionId, 1_000) is GuildGoldResult.Applied)
        restarted.recoverConfirmedCredit(deposit.transactionId, 1_000)
        assertEquals(100, restarted.getBalance(guildId))
        val admission = deposit.copy(transactionId = UUID.randomUUID())
        restarted.prepare(admission)
        restarted.beginExternal(admission.transactionId, "ADMISSION")
        restarted.recordExternalOutcome(admission.transactionId, true)
        restarted.recoverConfirmedCredit(admission.transactionId, 1_000)
        assertEquals(net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.BALANCE_APPLIED,
            restarted.findOperation(admission.transactionId)?.status)
        assertEquals(200, restarted.getBalance(guildId))
    }

    @Test
    fun `legacy preparation without external evidence remains held`() {
        val legacy = mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100).copy(route = GuildGoldRoute.PERSONAL_ACCOUNT)
        repository.prepare(legacy)
        storage.connection.executeUpdate("DELETE FROM guild_gold_external_attempts")
        assertEquals(0, GuildGoldRepositorySQL(storage).reconcileUnstarted(Long.MAX_VALUE))
        assertEquals(net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.PREPARED, repository.findOperation(legacy.transactionId)?.status)
    }

    @Test
    fun `confirmed recovery exceeding capacity stays held until capacity is available`() {
        val deposit = mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100).copy(route = GuildGoldRoute.PERSONAL_ACCOUNT)
        repository.prepare(deposit); repository.beginExternal(deposit.transactionId, "DEPOSIT")
        repository.recordExternalOutcome(deposit.transactionId, true)
        assertTrue(repository.recoverConfirmedCredit(deposit.transactionId, 50) is GuildGoldResult.Failed)
        assertEquals(0, repository.getBalance(guildId))
        assertEquals(net.lumalyte.lg.domain.gold.GuildGoldOperationStatus.PREPARED, repository.findOperation(deposit.transactionId)?.status)
        assertTrue(repository.recoverConfirmedCredit(deposit.transactionId, 100) is GuildGoldResult.Applied)
        assertEquals(100, repository.getBalance(guildId))
    }

    @Test
    fun `external debit replay changes balance and usage only once`() {
        repository.apply(mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 600), 1_000, null)
        val debit = mutation(UUID.randomUUID(), GuildGoldDirection.DEBIT, 100)
        val first = repository.applyExternalDebit(debit, 1_000, 123L)
        assertEquals(first, repository.applyExternalDebit(debit, 1_000, 123L))
        assertEquals(500, repository.getBalance(guildId))
        assertEquals(100, repository.getDailyWithdrawn(guildId, 123L))
    }

    @Test
    fun `external credit replay changes balance only once`() {
        val credit = mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 100)
        val first = repository.applyExternalCredit(credit, 1_000)
        assertEquals(first, repository.applyExternalCredit(credit, 1_000))
        assertEquals(100, repository.getBalance(guildId))
    }

    @Test
    fun `duplicate transaction id returns original result without applying twice`() {
        val transactionId = UUID.randomUUID()
        val mutation = mutation(transactionId, GuildGoldDirection.CREDIT, 600)

        val first = repository.apply(mutation, capacity = 1_000, periodStartEpochMs = null)
        val second = repository.apply(mutation, capacity = 1_000, periodStartEpochMs = null)

        assertEquals(first, second)
        assertEquals(600, repository.getBalance(guildId))
    }

    @Test
    fun `concurrent credits cannot cross capacity`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = List(2) {
            executor.submit<GuildGoldResult> {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                repository.apply(
                    mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 600),
                    capacity = 1_000,
                    periodStartEpochMs = null
                )
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()

        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertEquals(1, results.count { it is GuildGoldResult.Applied })
        assertEquals(
            1,
            results.count { it == GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED) }
        )
        assertEquals(600, repository.getBalance(guildId))
    }

    @Test
    fun `debit balance and daily usage commit together`() {
        val periodStart = 1_788_048_000_000L
        repository.apply(
            mutation(UUID.randomUUID(), GuildGoldDirection.CREDIT, 800),
            capacity = 1_000,
            periodStartEpochMs = null
        )

        val result = repository.apply(
            mutation(UUID.randomUUID(), GuildGoldDirection.DEBIT, 200),
            capacity = 1_000,
            periodStartEpochMs = periodStart
        )

        assertTrue(result is GuildGoldResult.Applied)
        assertEquals(600, repository.getBalance(guildId))
        assertEquals(200, repository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `insufficient debit leaves balance and daily usage unchanged`() {
        val periodStart = 1_788_048_000_000L

        val result = repository.apply(
            mutation(UUID.randomUUID(), GuildGoldDirection.DEBIT, 200),
            capacity = 1_000,
            periodStartEpochMs = periodStart
        )

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.INSUFFICIENT_FUNDS), result)
        assertEquals(0, repository.getBalance(guildId))
        assertEquals(0, repository.getDailyWithdrawn(guildId, periodStart))
    }

    @Test
    fun `same transaction id with different fields is rejected`() {
        val transactionId = UUID.randomUUID()
        repository.apply(
            mutation(transactionId, GuildGoldDirection.CREDIT, 100),
            capacity = 1_000,
            periodStartEpochMs = null
        )

        val result = repository.apply(
            mutation(transactionId, GuildGoldDirection.CREDIT, 200),
            capacity = 1_000,
            periodStartEpochMs = null
        )

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING), result)
        assertEquals(100, repository.getBalance(guildId))
    }

    @Test
    fun `duplicate rejected transaction returns its original rejection`() {
        val transactionId = UUID.randomUUID()
        val mutation = mutation(transactionId, GuildGoldDirection.CREDIT, 1_100)

        val first = repository.apply(mutation, capacity = 1_000, periodStartEpochMs = null)
        val second = repository.apply(mutation, capacity = 1_000, periodStartEpochMs = null)

        assertEquals(GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED), first)
        assertEquals(first, second)
        assertEquals(0, repository.getBalance(guildId))
    }

    private fun mutation(
        transactionId: UUID,
        direction: GuildGoldDirection,
        amount: Long
    ) = GuildGoldMutation(
        transactionId = transactionId,
        guildId = guildId,
        actorId = actorId,
        route = GuildGoldRoute.SYSTEM,
        direction = direction,
        amount = amount,
        fee = 0,
        description = "repository contract test"
    )
}
