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
