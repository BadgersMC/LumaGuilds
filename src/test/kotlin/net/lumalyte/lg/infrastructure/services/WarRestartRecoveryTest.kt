package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.WarRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.*

class WarRestartRecoveryTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var gold: GuildGoldService
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val legacyFrozen = mutableSetOf<UUID>()

    @BeforeEach fun setup() {
        MockBukkit.mock()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        gold = GuildGoldService(GuildGoldRepositorySQL(storage),
            GuildGoldPolicyProvider { GuildGoldPolicy(1, 2_000, 1.0, 2_000, 0.0, 0.0, 0, 0, 2_000, 3_000, false) },
            GuildGoldCapacityProvider { GuildGoldCapacity(2_000, 0) },
            additionalFrozen = { it in legacyFrozen })
        for (guild in listOf(first, second)) assertIs<GuildGoldResult.Applied>(
            gold.creditSystem(UUID.randomUUID(), guild, UUID(0, 0), 1_000, GuildGoldRoute.SYSTEM, "seed"))
    }
    @AfterEach fun cleanup() { storage.connection.close(); MockBukkit.unmock() }

    private fun service(repository: net.lumalyte.lg.application.persistence.WarRepository = WarRepositorySQL(storage)): WarServiceBukkit {
        return WarServiceBukkit(mockk { every { loadConfig() } returns MainConfig() }, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), progressionService = mockk(relaxed = true),
            warRepository = repository, warPayments = WarPaymentService(repository, gold))
    }

    @Test fun `paid war keeps identity statistics and one settlement across service restarts`() {
        var wars = service()
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            100, actorId = UUID.randomUUID()))
        wars = service()
        assertEquals(declaration, wars.getPendingDeclarationsForGuild(second).single())
        val active = assertNotNull(wars.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        assertEquals(declaration.id, active.id)
        assertEquals(WarStatus.ACTIVE, active.status)
        val stats = WarStats(active.id, declaringGuildKills = 4, defendingGuildKills = 2)
        assertTrue(wars.updateWarStats(stats))
        wars = service()
        assertEquals(active, wars.getActiveWars().single())
        assertEquals(stats, wars.getWarStats(active.id))
        assertEquals(200, wars.getWager(active.id)?.totalPot)
        assertEquals(900, gold.balance(first))
        assertTrue(wars.endWar(active.id, first, actorId = UUID.randomUUID()))
        assertEquals(1_100, gold.balance(first))
        wars = service()
        assertEquals(WarStatus.ENDED, wars.getWar(active.id)?.status)
        wars.resolveWager(active.id, first)
        assertEquals(1_100, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `finished winner cannot be overwritten by draw or cancellation after restart`() {
        val wars = service()
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            100, actorId = UUID.randomUUID()))
        val active = assertNotNull(wars.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        assertTrue(wars.endWar(active.id, first, actorId = UUID.randomUUID()))
        val restored = service()
        assertFalse(restored.endWarAsDraw(active.id, "late draw", UUID.randomUUID()))
        assertFalse(restored.cancelWar(active.id, UUID.randomUUID()))
        assertEquals(first, service().getWar(active.id)?.winner)
        assertEquals(WarStatus.ENDED, service().getWar(active.id)?.status)
        assertEquals(1_100, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `legacy freeze blocks wager funding without moving gold`() {
        val wars = service()
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            100, actorId = UUID.randomUUID()))
        legacyFrozen.add(first)
        assertNull(wars.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        assertTrue(service().getActiveWars().isEmpty())
        assertEquals(1_000, gold.balance(first))
        assertEquals(1_000, gold.balance(second))
    }

    @Test fun `recovery activates confirmed escrow after a failed active marker without charging frozen guilds again`() {
        val repository = WarRepositorySQL(storage)
        val interrupted = object : net.lumalyte.lg.application.persistence.WarRepository by repository {
            override fun save(record: DurableWarRecord): Boolean =
                if (record.war?.isActive == true) false else repository.save(record)
        }
        val wars = service(interrupted)
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            100, actorId = UUID.randomUUID()))
        assertNull(wars.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        assertEquals(WarPaymentPhase.ESCROWED, repository.get(declaration.id)?.paymentPhase)
        legacyFrozen.addAll(listOf(first, second))
        service().processExpiredWars()
        assertEquals(WarStatus.ACTIVE, service().getWar(declaration.id)?.status)
        service().processExpiredWars()
        assertEquals(900, gold.balance(first))
        assertEquals(900, gold.balance(second))
    }

    @Test fun `rejected declaration does not reappear after restart`() {
        val wars = service()
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            actorId = UUID.randomUUID()))
        assertTrue(wars.rejectWarDeclaration(declaration.id, UUID.randomUUID()))
        assertTrue(service().getPendingDeclarationsForGuild(second).isEmpty())
    }

    @Test fun `single record queries avoid full history and operations load one snapshot`() {
        val sql = WarRepositorySQL(storage)
        var scans = 0
        val counted = object : net.lumalyte.lg.application.persistence.WarRepository by sql {
            override fun getAll(): List<DurableWarRecord> { scans++; return sql.getAll() }
        }
        val wars = service(counted)
        scans = 0
        val declaration = assertNotNull(wars.createWarDeclaration(first, second, Duration.ofDays(1), emptySet(),
            actorId = UUID.randomUUID()))
        assertEquals(1, scans, "Declaration must reuse its history snapshot")
        val active = assertNotNull(wars.acceptWarDeclaration(declaration.id, UUID.randomUUID()))
        scans = 0
        assertEquals(active, wars.getWar(active.id))
        wars.getWarStats(active.id)
        wars.getWager(active.id)
        wars.checkForDrawCondition(active.id)
        assertEquals(0, scans, "Single-record queries must not scan history")
        wars.processExpiredWars()
        assertEquals(1, scans, "Expiry pass must reuse one history snapshot")
    }
}
