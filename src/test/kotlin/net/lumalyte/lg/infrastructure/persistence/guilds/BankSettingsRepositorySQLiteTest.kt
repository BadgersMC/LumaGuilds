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

    @BeforeEach
    fun setUp() {
        repository = BankSettingsRepositorySQLite(VirtualThreadSQLiteStorage(tempDir.toFile()))
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
}
