package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.migrations.PlayerNotificationPreferenceSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerNotificationPreferenceRepositorySQLTest {
    @TempDir lateinit var directory: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: PlayerNotificationPreferenceRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use {
            PlayerNotificationPreferenceSchema.create(it, mariaDb = false)
        }
        repository = PlayerNotificationPreferenceRepositorySQL(storage)
    }
    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `login notifications default enabled and persist explicit preference across restart`() {
        val playerId = UUID.randomUUID()

        assertTrue(repository.isGuildLoginEnabled(playerId))
        assertTrue(repository.setGuildLoginEnabled(playerId, false))
        assertFalse(repository.isGuildLoginEnabled(playerId))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = PlayerNotificationPreferenceRepositorySQL(storage)

        assertFalse(repository.isGuildLoginEnabled(playerId))
        val defaultEnabledId = UUID.randomUUID()
        assertEquals(
            mapOf(playerId to false, defaultEnabledId to true),
            repository.guildLoginStates(setOf(playerId, defaultEnabledId)),
        )

        assertTrue(repository.setGuildLoginEnabled(playerId, true))
        assertTrue(repository.isGuildLoginEnabled(playerId))
    }
}
