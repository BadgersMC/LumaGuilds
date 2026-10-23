package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.GuildCreationCooldown
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildCreationAdmissionSQLTest : RewardSqlTestFixture() {
    @Test
    fun lostCommitAcknowledgmentMustNotBecomeRefundableFailure() {
        val actual = openStorage()
        migrateProductionSchema(actual)
        val database = io.mockk.spyk(actual.connection)
        io.mockk.every { database.connection } answers {
            val connection = actual.connection.connection
            java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader,
                arrayOf(java.sql.Connection::class.java)) { _, method, args ->
                try {
                    val result = method.invoke(connection, *(args ?: emptyArray()))
                    if (method.name == "commit") throw java.sql.SQLException("lost commit acknowledgment")
                    result
                } catch (error: java.lang.reflect.InvocationTargetException) { throw error.cause!! }
            } as java.sql.Connection
        }
        val storage = io.mockk.mockk<Storage<Database>>()
        io.mockk.every { storage.connection } returns database
        io.mockk.every { storage.dialect } returns actual.dialect
        val repository = GuildRepositorySQLite(storage)
        val guild = Guild(UUID.randomUUID(), "Commit Uncertain", createdAt = Instant.now())
        kotlin.test.assertFailsWith<GuildCreationTransactionUncertainException> {
            repository.addCreated(guild, UUID.randomUUID())
        }
        // The server committed; treating the exception as false would incorrectly restore gold.
        assertEquals(1, actual.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM guilds WHERE id = ?", guild.id.toString())!!.getInt("n"))
    }

    @Test
    fun newGuildCanResolveRewardsBeforeOwnerAdmission() {
        val storage = openStorage()
        val repository = repository(storage)
        val config = io.mockk.mockk<net.lumalyte.lg.application.services.ConfigService>()
        io.mockk.every { config.loadConfig() } returns net.lumalyte.lg.config.MainConfig()
        val progression = ProgressionRepositorySQLite(storage, config)
        val guild = Guild(UUID.randomUUID(), "Bootstrap Ready", createdAt = Instant.now())
        assertTrue(repository.addCreated(guild, UUID.randomUUID()))
        val reader = RewardStateRepositorySQL(storage,
            RewardOwnershipRepositorySQL(storage, net.lumalyte.lg.domain.rewards.RewardCatalog.chapterTwo()))
        kotlin.test.assertIs<net.lumalyte.lg.domain.rewards.RewardStateRead.Found>(reader.read(guild.id))
        assertEquals(1, assertNotNull(progression.getGuildProgression(guild.id)).currentLevel)
    }

    @Test
    fun configuredThresholdAndBootstrapFailureStayAtomic() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val repository = GuildRepositorySQLite(storage) { 4321 }
        val guild = Guild(UUID.randomUUID(), "Configured Curve", createdAt = Instant.now())
        assertTrue(repository.addCreated(guild, UUID.randomUUID()))
        assertEquals(4321, storage.connection.getFirstRow(
            "SELECT experience_for_next_level AS n FROM guild_progression WHERE guild_id = ?",
            guild.id.toString())!!.getInt("n"))
        val failed = Guild(UUID.randomUUID(), "Atomic Failure", createdAt = Instant.now())
        val body = failureTriggerBody(storage)
        storage.connection.executeUpdate("CREATE TRIGGER reject_progression BEFORE INSERT ON guild_progression $body")
        assertFalse(repository.addCreated(failed, UUID.randomUUID()))
        assertNull(repository.getById(failed.id))
        assertCreationRowsMissing(storage, failed)
        assertEquals(0, storage.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM guild_reward_accounts WHERE guild_id = ?", failed.id.toString())!!.getInt("n"))
    }

    @Test
    fun setupCleanupSkipsCooldown() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Setup Failure", createdAt = Instant.now())

        assertTrue(repository.addCreated(guild, creator))
        assertTrue(repository.remove(guild.id))
        assertNull(repository.creationCooldownUntil(creator))
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guild_reward_accounts WHERE guild_id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
        assertTrue(repository.addCreated(Guild(UUID.randomUUID(), "Retry", createdAt = Instant.now()), creator))
    }

    @Test
    fun admissionAndDeletionAreAtomic() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val at = Instant.parse("2026-09-17T00:00:00Z")
        val guild = Guild(UUID.randomUUID(), "First", createdAt = at)

        assertTrue(repository.addCreated(guild, creator))
        assertEquals(guild, repository.getById(guild.id))
        assertTrue(repository.removeWithCreationCooldown(guild.id, GuildCreationCooldown(), at.plusSeconds(1)))
        assertNull(repository.getById(guild.id))
        val expires = requireNotNull(repository.creationCooldownUntil(creator))
        assertFalse(repository.addCreated(Guild(UUID.randomUUID(), "Blocked", createdAt = at.plusSeconds(2)), creator))
        assertTrue(repository.addCreated(Guild(UUID.randomUUID(), "Allowed", createdAt = expires), creator))
    }

    @Test
    fun historyFailureRollsBackDelete() {
        val storage = openStorage()
        val repository = repository(storage)
        val creator = UUID.randomUUID()
        val at = Instant.parse("2026-09-17T00:00:00Z")
        val guild = Guild(UUID.randomUUID(), "Keep", createdAt = at)

        assertTrue(repository.addCreated(guild, creator))
        installRejectCreatorUpdate(storage)

        assertFalse(repository.removeWithCreationCooldown(guild.id, GuildCreationCooldown(), at.plusSeconds(1)))
        assertEquals(guild, repository.getById(guild.id))
        assertNull(repository.creationCooldownUntil(creator))
        assertGuildPresent(storage, guild)
    }

    @Test
    fun originFailureRollsBackCreate() {
        val storage = openStorage()
        val repository = repository(storage)
        GuildCreationHistorySQL(storage)
        val body = failureTriggerBody(storage)
        storage.connection.executeUpdate("CREATE TRIGGER reject_creator_insert BEFORE INSERT ON guild_creators $body")
        val guild = Guild(UUID.randomUUID(), "Rollback", createdAt = Instant.now())

        assertFalse(repository.addCreated(guild, UUID.randomUUID()))
        assertNull(repository.getById(guild.id))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guilds")!!.getInt("n"))
    }

    @Test
    fun newGuildInitializesPrestige() {
        val storage = openStorage()
        val repository = repository(storage)
        val guild = Guild(UUID.randomUUID(), "Prestige Ready", createdAt = Instant.now())

        assertTrue(repository.addCreated(guild, UUID.randomUUID()))

        val row =
            storage.connection.getFirstRow(
                "SELECT version, initial_home_capacity, prestige_count FROM guild_reward_accounts WHERE guild_id = ?",
                guild.id.toString(),
            )
        assertNotNull(row)
        assertEquals(0, row.getInt("version"))
        assertEquals(1, row.getInt("initial_home_capacity"))
        assertEquals(0, row.getInt("prestige_count"))
    }

    @Test
    fun rewardInitFailureRollsBack() {
        val storage = openStorage()
        val repository = repository(storage)
        rejectInserts(storage, "guild_reward_accounts")
        val guild = Guild(UUID.randomUUID(), "No Partial Guild", createdAt = Instant.now())

        assertFalse(repository.addCreated(guild, UUID.randomUUID()))

        assertNull(repository.getById(guild.id))
        assertCreationRowsMissing(storage, guild)
    }

    private fun installRejectCreatorUpdate(storage: Storage<Database>) {
        val body = failureTriggerBody(storage)
        storage.connection.executeUpdate("CREATE TRIGGER reject_creator_update BEFORE UPDATE ON guild_creators $body")
    }

    private fun assertGuildPresent(storage: Storage<Database>, guild: Guild) {
        assertEquals(
            1,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guilds WHERE id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
    }

    private fun assertCreationRowsMissing(storage: Storage<Database>, guild: Guild) {
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guilds WHERE id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS n FROM guild_creators WHERE guild_id = ?",
                guild.id.toString(),
            )!!.getInt("n"),
        )
    }

    private fun repository(storage: Storage<Database>): GuildRepositorySQLite {
        migrateProductionSchema(storage)
        return GuildRepositorySQLite(storage)
    }
}

private fun failureTriggerBody(storage: Storage<Database>): String {
    return if (storage.dialect == SqlDialect.MARIADB) {
        "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'"
    } else {
        "BEGIN SELECT RAISE(ABORT, 'injected'); END"
    }
}
