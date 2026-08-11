package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

/**
 * REQ-010/011/031: bank automation, budget, and security settings must be
 * persisted per guild (no hardcoded fakes) and survive round-trips.
 */
class BankSettingsRepositorySQLiteTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: BankSettingsRepositorySQLite
    private lateinit var storage: VirtualThreadSQLiteStorage

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = BankSettingsRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() {
        // Release the SQLite file handle so JUnit can delete the temp dir.
        storage.connection.close()
    }

    @Test
    fun `upsert then get round-trips all settings`() {
        val guildId = UUID.randomUUID()
        val settings = BankSettings(
            guildId = guildId,
            scheduledDepositsEnabled = true,
            autoRewardsEnabled = false,
            recurringPaymentsEnabled = true,
            interestRate = 0.075,
            dualAuthThreshold = 2500,
            monthlyBudget = 20000,
            weeklyBudget = 5000,
            dailyBudget = 1000,
            lastInterestAccrual = 1_700_000_000_000L
        )

        assertTrue(repository.upsert(settings))

        val loaded = repository.getByGuildId(guildId)
        assertNotNull(loaded)
        assertEquals(settings, loaded)
    }

    @Test
    fun `get for unknown guild returns null`() {
        assertNull(repository.getByGuildId(UUID.randomUUID()))
    }

    @Test
    fun `upsert overwrites existing row`() {
        val guildId = UUID.randomUUID()
        repository.upsert(BankSettings(guildId = guildId, monthlyBudget = 10000))
        repository.upsert(BankSettings(guildId = guildId, monthlyBudget = 42000))

        val loaded = repository.getByGuildId(guildId)
        assertNotNull(loaded)
        assertEquals(42000, loaded!!.monthlyBudget)
        assertEquals(2500, loaded.weeklyBudget) // second write carried BankSettings defaults
    }

    @Test
    fun `settings persist to sqlite - a second repository over the same db sees them`() {
        val guildId = UUID.randomUUID()
        val settings = BankSettings(
            guildId = guildId,
            scheduledDepositsEnabled = true,
            autoRewardsEnabled = false,
            recurringPaymentsEnabled = true,
            interestRate = 0.075,
            dualAuthThreshold = 2500,
            monthlyBudget = 20000,
            weeklyBudget = 5000,
            dailyBudget = 1000,
            lastInterestAccrual = 1_700_000_000_000L
        )

        assertTrue(repository.upsert(settings))

        // A fresh repository over the same database must preload the row from
        // SQLite — this proves real persistence, not just the in-memory cache.
        val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val secondRepo = BankSettingsRepositorySQLite(secondStorage)
            val loaded = secondRepo.getByGuildId(guildId)
            assertNotNull(loaded)
            assertEquals(settings, loaded)
        } finally {
            secondStorage.connection.close()
        }
    }

    @Test
    fun `returned settings are copies - mutating them does not corrupt the cache`() {
        val guildId = UUID.randomUUID()
        repository.upsert(BankSettings(guildId = guildId, monthlyBudget = 10000))

        val returned = repository.getByGuildId(guildId)
        assertNotNull(returned)
        returned!!.monthlyBudget = 99999 // caller mutation

        // Cache (and a fresh read) must be unaffected.
        assertEquals(10000, repository.getByGuildId(guildId)!!.monthlyBudget)
    }
}
