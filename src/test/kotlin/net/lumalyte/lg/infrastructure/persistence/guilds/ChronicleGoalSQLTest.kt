package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.ChronicleGoal
import net.lumalyte.lg.infrastructure.persistence.migrations.ChronicleGoalSchema
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*
import java.util.concurrent.CompletableFuture

class ChronicleGoalSQLTest : RewardSqlTestFixture() {
    @Test fun `goal survives database close and reopen and clear has an ABA safe revision`() {
        var storage = openStorage()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY)")
        val guild = UUID.randomUUID()
        val other = UUID.randomUUID()
        storage.connection.executeUpdate("INSERT INTO guilds (id) VALUES (?)", guild.toString())
        storage.connection.connection.use { ChronicleGoalSchema.create(it, false) }
        val initial = ChronicleGoal(null, 0)
        assertTrue(ChronicleGoalRepositorySQL(storage).compareAndSet(guild, initial, "ally-homes"))
        closeStorage(storage)
        storage = openStorage()
        val reopened = ChronicleGoalRepositorySQL(storage)
        assertEquals(ChronicleGoal("ally-homes", 1), reopened.read(guild))
        assertEquals(initial, reopened.read(other))
        assertFalse(reopened.compareAndSet(guild, initial, "fee-5"))
        assertTrue(reopened.compareAndSet(guild, reopened.read(guild), null))
        assertEquals(ChronicleGoal(null, 2), reopened.read(guild))
        assertFalse(reopened.compareAndSet(guild, initial, "bank-1"))
        storage.connection.connection.use { ChronicleGoalSchema.delete(it, guild) }
        assertEquals(initial, reopened.read(guild))
    }

    @Test fun `two independent writers with the same rendered revision cannot both win`() {
        val storage = openStorage()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY)")
        val guild = UUID.randomUUID()
        storage.connection.executeUpdate("INSERT INTO guilds (id) VALUES (?)", guild.toString())
        storage.connection.connection.use { ChronicleGoalSchema.create(it, false) }
        val results = listOf("bank-1", "fee-5").map { id -> CompletableFuture.supplyAsync {
            ChronicleGoalRepositorySQL(storage).compareAndSet(guild, ChronicleGoal(null, 0), id)
        } }.map { it.get() }
        assertEquals(1, results.count { it })
        assertEquals(1L, ChronicleGoalRepositorySQL(storage).read(guild).revision)
        assertFalse(ChronicleGoalRepositorySQL(storage).compareAndSet(UUID.randomUUID(), ChronicleGoal(null, 0), "bank-1"))
    }

    @Test fun `failed transaction preserves the committed goal`() {
        val storage = openStorage()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY)")
        val guild = UUID.randomUUID()
        storage.connection.executeUpdate("INSERT INTO guilds (id) VALUES (?)", guild.toString())
        storage.connection.connection.use { ChronicleGoalSchema.create(it, false) }
        val repo = ChronicleGoalRepositorySQL(storage)
        assertTrue(repo.compareAndSet(guild, ChronicleGoal(null, 0), "bank-1"))
        val saved = repo.read(guild)
        val failure = if (storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB)
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'"
            else "BEGIN SELECT RAISE(ABORT, 'injected'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_goal BEFORE UPDATE ON guild_chronicle_goals $failure")
        assertFails { repo.compareAndSet(guild, saved, null) }
        assertEquals(saved, repo.read(guild))
    }
}

