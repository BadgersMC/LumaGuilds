package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.GuildCreationCooldown
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildCreationHistorySQLTest : RewardSqlTestFixture() {
    private val created = Instant.parse("2026-09-17T00:00:00Z")
    private val creator = UUID.randomUUID()
    private val guild = UUID.randomUUID()

    @Test fun `rollback failure is suppressed and never enables auto commit on partial writes`() {
        val connection = mockk<java.sql.Connection>(relaxed = true)
        every { connection.autoCommit } returns true
        every { connection.prepareStatement(any()).executeQuery().next() } returns false
        val rollback = java.sql.SQLException("rollback failed")
        every { connection.rollback() } throws rollback
        val database = mockk<co.aikar.idb.Database>(relaxed = true)
        every { database.connection } returns connection
        val storage = mockk<net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>>()
        every { storage.connection } returns database
        every { storage.dialect } returns net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.SQLITE
        val history = GuildCreationHistorySQL(storage)
        val failure = AssertionError("callback failed")
        assertSame(failure, assertFailsWith<AssertionError> {
            history.create(guild, creator, created) { throw failure }
        })
        assertEquals(listOf(rollback), failure.suppressed.toList())
        verify(exactly = 0) { connection.autoCommit = true }
        verify(exactly = 1) { connection.close() }
    }

    @Test fun `callback Error rolls back writes before auto commit is restored`() {
        val storage = openStorage()
        val history = GuildCreationHistorySQL(storage)
        val failure = AssertionError("injected callback failure")
        val thrown = assertFailsWith<AssertionError> {
            history.create(guild, creator, created) { connection ->
                connection.prepareStatement("UPDATE guild_creation_cooldowns SET blocked_until = 123 WHERE player_id = ?").use {
                    it.setString(1, creator.toString())
                    it.executeUpdate()
                }
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertNull(history.cooldownUntil(creator))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_creation_cooldowns")!!.getInt("n"))
    }

    @Test fun `concurrent deletion retries settle one cooldown only`() {
        val history = GuildCreationHistorySQL(openStorage())
        history.create(guild, creator, created) { true }
        val callbacks = java.util.concurrent.atomic.AtomicInteger()
        val attempts = (1..4).map { offset ->
            java.util.concurrent.CompletableFuture.supplyAsync {
                history.delete(guild, GuildCreationCooldown(), created.plusSeconds(offset.toLong())) {
                    callbacks.incrementAndGet()
                    true
                }
            }
        }
        assertEquals(1, attempts.count { it.get(10, java.util.concurrent.TimeUnit.SECONDS) })
        assertEquals(1, callbacks.get())
        assertNotNull(history.cooldownUntil(creator))
    }

    @Test fun `early deletion blocks original creator through restart and expires exactly`() {
        val storage = openStorage()
        val history = GuildCreationHistorySQL(storage)
        assertTrue(history.create(guild, creator, created) { true })
        val deleted = created.plusSeconds(86400)
        assertTrue(history.delete(guild, GuildCreationCooldown(), deleted) { true })
        val expires = deleted.plusSeconds(15 * 86400)
        assertEquals(expires, history.cooldownUntil(creator))
        assertFalse(history.create(UUID.randomUUID(), creator, expires.minusMillis(1)) { error("Must not insert") })
        assertNull(history.cooldownUntil(UUID.randomUUID()))
        closeStorage(storage)
        val reopened = GuildCreationHistorySQL(openStorage())
        assertEquals(expires, reopened.cooldownUntil(creator))
        assertTrue(reopened.create(UUID.randomUUID(), creator, expires) { true })
    }

    @Test fun `failed deletion does not impose cooldown and replay cannot extend it`() {
        val history = GuildCreationHistorySQL(openStorage())
        assertTrue(history.create(guild, creator, created) { true })
        assertFalse(history.delete(guild, GuildCreationCooldown(), created.plusSeconds(1)) { false })
        assertNull(history.cooldownUntil(creator))
        assertFailsWith<IllegalStateException> {
            history.delete(guild, GuildCreationCooldown(), created.plusSeconds(2)) { error("storage failed") }
        }
        assertNull(history.cooldownUntil(creator))
        assertTrue(history.delete(guild, GuildCreationCooldown(), created.plusSeconds(3)) { true })
        val expires = history.cooldownUntil(creator)
        assertFalse(history.delete(guild, GuildCreationCooldown(), created.plusSeconds(100)) { error("Already deleted") })
        assertEquals(expires, history.cooldownUntil(creator))
    }

    @Test fun `old guild boundary and unknown legacy creators never get an invented penalty`() {
        val history = GuildCreationHistorySQL(openStorage())
        history.create(guild, creator, created) { true }
        assertTrue(history.delete(guild, GuildCreationCooldown(), created.plusSeconds(7 * 86400)) { true })
        assertNull(history.cooldownUntil(creator))
        assertTrue(history.delete(UUID.randomUUID(), GuildCreationCooldown(), created) { true })
        assertNull(history.cooldownUntil(creator))
    }

    @Test fun `failed creation rolls back creator receipt and configured duration cannot shorten cooldown`() {
        val history = GuildCreationHistorySQL(openStorage())
        assertFalse(history.create(guild, creator, created) { false })
        assertTrue(history.create(guild, creator, created) { true })
        val second = UUID.randomUUID()
        history.create(second, creator, created) { true }
        history.delete(guild, GuildCreationCooldown(), created.plusSeconds(1)) { true }
        val expires = history.cooldownUntil(creator)
        history.delete(second, GuildCreationCooldown(7, 1), created.plusSeconds(2)) { true }
        assertEquals(expires, history.cooldownUntil(creator))
        assertFailsWith<IllegalArgumentException> { GuildCreationCooldown(-1, 15) }
        assertFailsWith<IllegalArgumentException> { GuildCreationCooldown().expiresAt(created, created.minusSeconds(1)) }
    }
}
