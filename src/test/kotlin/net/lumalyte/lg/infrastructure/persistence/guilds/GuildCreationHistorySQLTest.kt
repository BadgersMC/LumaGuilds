package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.GuildCreationCooldown
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildCreationHistorySQLTest : RewardSqlTestFixture() {
    private val created = Instant.parse("2026-09-17T00:00:00Z")
    private val creator = UUID.randomUUID()
    private val guild = UUID.randomUUID()

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
