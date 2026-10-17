package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.WarNotification
import net.lumalyte.lg.domain.entities.WarNotificationKind
import net.lumalyte.lg.infrastructure.persistence.migrations.WarNotificationSchema
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

class WarNotificationRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: WarNotificationRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use {
            WarNotificationSchema.create(it, mariaDb = false)
        }
        repository = WarNotificationRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `pending notifications survive restart and deliver once`() {
        val playerId = UUID.randomUUID()
        val newer = notification(
            playerId = playerId,
            kind = WarNotificationKind.WAR_ACCEPTED,
            createdAt = 200,
        )
        val older = notification(
            playerId = playerId,
            kind = WarNotificationKind.DECLARATION_RECEIVED,
            createdAt = 100,
        )

        assertTrue(repository.add(newer))
        assertTrue(repository.add(older))
        assertFalse(repository.add(older), "duplicate deterministic id must be ignored")

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = WarNotificationRepositorySQL(storage)

        assertEquals(listOf(older, newer), repository.getPending(playerId))
        assertTrue(repository.markDelivered(older.id, 300))
        assertFalse(
            repository.markDelivered(older.id, 301),
            "already-delivered notification must not be delivered twice",
        )
        assertEquals(listOf(newer), repository.getPending(playerId))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = WarNotificationRepositorySQL(storage)

        assertEquals(listOf(newer), repository.getPending(playerId))
    }

    private fun notification(
        playerId: UUID,
        kind: WarNotificationKind,
        createdAt: Long,
    ): WarNotification = WarNotification(
        id = UUID.nameUUIDFromBytes(
            "war-notification:$kind:$playerId".toByteArray(),
        ),
        playerId = playerId,
        kind = kind,
        eventId = UUID.randomUUID(),
        ownGuildId = UUID.randomUUID(),
        opponentGuildId = UUID.randomUUID(),
        opponentName = "Iron Legion",
        opponentBanner = "serialized-banner",
        durationSeconds = 604_800,
        objectiveCount = 1,
        objectiveDescription = "Kill 25 enemy players",
        wagerAmount = 250,
        terms = "No combat logging",
        expiresAt = 500,
        ownKills = 25,
        opponentKills = 18,
        createdAt = createdAt,
    )
}
