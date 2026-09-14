package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.WarRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WarPaymentServiceTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var wars: WarRepositorySQL
    private lateinit var goldRepository: GuildGoldRepositorySQL
    private lateinit var gold: GuildGoldService
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private var secondCapacity = 2_000L

    @BeforeEach fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        wars = WarRepositorySQL(storage)
        goldRepository = GuildGoldRepositorySQL(storage)
        gold = goldService(goldRepository)
        seed(first, 1_000)
    }

    @AfterEach fun cleanup() { storage.connection.close() }

    private fun goldService(repository: GuildGoldRepository) = GuildGoldService(repository,
        GuildGoldPolicyProvider { GuildGoldPolicy(1, 2_000, 1.0, 2_000, 0.0, 0.0, 0, 0, 2_000, 3_000, false) },
        GuildGoldCapacityProvider { GuildGoldCapacity(if (it == second) secondCapacity else 2_000, 0) })

    private fun seed(guild: UUID, amount: Long) {
        assertIs<GuildGoldResult.Applied>(gold.creditSystem(UUID.randomUUID(), guild, UUID(0, 0), amount, GuildGoldRoute.SYSTEM, "seed"))
    }

    private fun pending(): UUID {
        val id = UUID.randomUUID()
        assertTrue(wars.save(DurableWarRecord(id,
            declaration = WarDeclaration(id = id, declaringGuildId = first, defendingGuildId = second, wagerAmount = 100),
            war = War(id = id, declaringGuildId = first, defendingGuildId = second),
            wager = WarWager(warId = id, declaringGuildId = first, defendingGuildId = second,
                declaringGuildWager = 100, defendingGuildWager = 100), paymentPhase = WarPaymentPhase.FUNDING)))
        return id
    }

    @Test fun `funding and settlement replay after service recreation without moving money twice`() {
        seed(second, 1_000)
        val id = pending()
        assertTrue(WarPaymentService(wars, gold).fund(id))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
        assertTrue(WarPaymentService(wars, gold).settle(id, first))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).settle(id, first))
        assertEquals(1_100, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `partial draw refund retries only the unpaid guild when capacity becomes available`() {
        seed(second, 1_000)
        val id = pending()
        val service = WarPaymentService(wars, gold)
        assertTrue(service.fund(id))
        secondCapacity = 900
        assertFalse(service.settle(id, null))
        assertEquals(1_000, gold.balance(first))
        assertEquals(900, gold.balance(second))
        secondCapacity = 2_000
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).settle(id, null))
        assertEquals(1_000, gold.balance(first))
        assertEquals(1_000, gold.balance(second))
    }

    @Test fun `a retry cannot change the chosen winner or turn a draw into a win`() {
        seed(second, 1_000)
        val id = pending()
        val service = WarPaymentService(wars, gold)
        assertTrue(service.fund(id))
        secondCapacity = 900
        assertFalse(service.settle(id, null))
        assertFalse(WarPaymentService(wars, gold).settle(id, first))
        assertEquals(1_000, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `failed second escrow debit refunds first and permits a distinct fully funded retry`() {
        val id = pending()
        assertFalse(WarPaymentService(wars, gold).fund(id))
        assertEquals(1_000, gold.balance(first))
        assertEquals(0, gold.balance(second))
        assertEquals(WagerStatus.CANCELLED, wars.get(id)!!.wager!!.status)
        seed(second, 1_000)
        assertTrue(WarPaymentService(wars, gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `crash before the second leg marker does not repeat the first debit`() {
        seed(second, 1_000)
        val id = pending()
        val failSecondMarker = object : WarRepository by wars {
            override fun save(record: DurableWarRecord): Boolean =
                if (record.paymentAttempts.containsKey("defending-debit")) false else wars.save(record)
        }
        assertFalse(WarPaymentService(failSecondMarker, gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(1_000, gold.balance(second))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `failed settled marker replays the payment journal instead of paying again`() {
        seed(second, 1_000)
        val id = pending()
        assertTrue(WarPaymentService(wars, gold).fund(id))
        val failSettled = object : WarRepository by wars {
            override fun save(record: DurableWarRecord): Boolean =
                if (record.paymentPhase == WarPaymentPhase.SETTLED) false else wars.save(record)
        }
        assertFalse(WarPaymentService(failSettled, gold).settle(id, first))
        assertEquals(1_100, gold.balance(first))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).settle(id, first))
        assertEquals(1_100, gold.balance(first))
    }

    @Test fun `uncertain debit is held for review with no speculative refund or retry`() {
        seed(second, 1_000)
        val id = pending()
        val uncertain = object : GuildGoldRepository by goldRepository {
            override fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult {
                if (mutation.guildId == second && mutation.direction == GuildGoldDirection.DEBIT) {
                    goldRepository.prepare(mutation)
                    return GuildGoldResult.Failed(mutation.transactionId, false)
                }
                return goldRepository.apply(mutation, capacity, periodStartEpochMs)
            }
        }
        assertFalse(WarPaymentService(wars, goldService(uncertain)).fund(id))
        assertEquals(WarPaymentPhase.REVIEW, wars.get(id)!!.paymentPhase)
        assertEquals(900, gold.balance(first))
        assertFalse(WarPaymentService(WarRepositorySQL(storage), gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(1_000, gold.balance(second))
    }

    @Test fun `failed funded marker reuses both committed debit legs`() {
        seed(second, 1_000)
        val id = pending()
        val failFunded = object : WarRepository by wars {
            override fun save(record: DurableWarRecord): Boolean =
                if (record.paymentPhase == WarPaymentPhase.ESCROWED) false else wars.save(record)
        }
        assertFalse(WarPaymentService(failFunded, gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `lost response after a committed debit is recovered from the journal`() {
        seed(second, 1_000)
        val id = pending()
        val lostReply = object : GuildGoldRepository by goldRepository {
            override fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult {
                val result = goldRepository.apply(mutation, capacity, periodStartEpochMs)
                if (mutation.guildId == first && mutation.direction == GuildGoldDirection.DEBIT) error("Reply lost after commit")
                return result
            }
        }
        assertTrue(WarPaymentService(wars, goldService(lostReply)).fund(id))
        assertTrue(WarPaymentService(WarRepositorySQL(storage), gold).fund(id))
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }
}
