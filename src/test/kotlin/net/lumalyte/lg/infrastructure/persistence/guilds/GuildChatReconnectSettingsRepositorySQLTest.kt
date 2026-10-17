package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.migrations.GuildChatReconnectSettingsSchema
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reconnect settings must survive restart and reject stale writers. */
internal class GuildChatReconnectSettingsRepositorySQLTest : RewardSqlTestFixture() {
    /** Missing rows default off; guild changes survive repository restart. */
    @Test
    fun persistence() {
        val storage = openStorage()
        val repository = GuildChatReconnectSettingsRepositorySQL(storage)
        val guild = UUID.randomUUID()
        assertFalse(repository.resetOnJoin(guild))
        assertTrue(repository.compareAndSet(guild, false, true))
        assertTrue(GuildChatReconnectSettingsRepositorySQL(storage).resetOnJoin(guild))
        assertFalse(repository.resetOnJoin(UUID.randomUUID()))
    }

    /** A stale disable cannot overwrite a newer enable. */
    @Test
    fun staleWriter() {
        val repository = GuildChatReconnectSettingsRepositorySQL(openStorage())
        val guild = UUID.randomUUID()
        assertTrue(repository.compareAndSet(guild, false, true))
        assertFalse(repository.compareAndSet(guild, false, false))
        assertTrue(repository.resetOnJoin(guild))
        assertTrue(repository.compareAndSet(guild, true, false))
        assertFalse(repository.resetOnJoin(guild))
    }

    /** Guild deletion participates in its caller transaction and cannot erase preferences on rollback. */
    @Test
    fun deletionRollback() {
        val storage = openStorage()
        val repository = GuildChatReconnectSettingsRepositorySQL(storage)
        val guild = UUID.randomUUID()
        assertTrue(repository.compareAndSet(guild, false, true))
        storage.connection.connection.use { rollbackDelete(it, guild) }
        assertTrue(repository.resetOnJoin(guild))
        storage.connection.connection.use {
            GuildChatReconnectSettingsSchema.delete(it, guild)
        }
        assertFalse(repository.resetOnJoin(guild))
    }

    private fun rollbackDelete(connection: java.sql.Connection, guild: UUID) {
        connection.autoCommit = false
        try {
            GuildChatReconnectSettingsSchema.delete(connection, guild)
            connection.rollback()
        } finally {
            connection.autoCommit = true
        }
    }

    /** Failed reads preserve the current channel rather than enabling resets. */
    @Test
    fun failedRead() {
        val storage = openStorage()
        val repository = GuildChatReconnectSettingsRepositorySQL(storage)
        storage.connection.executeUpdate("DROP TABLE guild_chat_reconnect_settings")
        assertFalse(repository.resetOnJoin(UUID.randomUUID()))
    }

    /** Failed writes do not create an in-memory enabled state. */
    @Test
    fun failedWrite() {
        val storage = openStorage()
        val repository = GuildChatReconnectSettingsRepositorySQL(storage)
        storage.connection.executeUpdate("DROP TABLE guild_chat_reconnect_settings")
        assertFalse(repository.compareAndSet(UUID.randomUUID(), false, true))
    }
}
