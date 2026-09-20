package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildDiscordRoleSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuildDiscordRoleRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildDiscordRoleRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use { GuildDiscordRoleSchema.create(it, mariaDb = false) }
        repository = GuildDiscordRoleRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `guild discord role link survives restart and can be replaced without losing unlock time`() {
        val guildId = UUID.randomUUID()
        val unlockedAt = Instant.ofEpochMilli(1_726_850_400_000L)
        val initial = GuildDiscordRoleLink(guildId, "123456789012345678", unlockedAt)

        assertTrue(repository.upsert(initial))
        assertEquals(initial, repository.get(guildId))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = GuildDiscordRoleRepositorySQL(storage)

        assertEquals(initial, repository.get(guildId))

        val replacement = initial.copy(discordRoleId = "987654321098765432")
        assertTrue(repository.upsert(replacement))
        assertEquals(replacement, repository.get(guildId))
        assertEquals(unlockedAt, repository.get(guildId)?.unlockedAt)

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = GuildDiscordRoleRepositorySQL(storage)

        assertEquals(replacement, repository.get(guildId))
        assertTrue(repository.delete(guildId))
        assertNull(repository.get(guildId))
    }
}
