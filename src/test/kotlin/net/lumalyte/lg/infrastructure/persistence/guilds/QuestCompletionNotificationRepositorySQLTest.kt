package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.QuestCompletionNotification
import net.lumalyte.lg.infrastructure.persistence.migrations.QuestCompletionNotificationSchema
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

class QuestCompletionNotificationRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: QuestCompletionNotificationRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use {
            QuestCompletionNotificationSchema.create(it, mariaDb = false)
        }
        repository = QuestCompletionNotificationRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `completion notifications survive restart and deliver once`() {
        val playerId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val newer = notification(playerId, guildId, "quest-b", 200)
        val older = notification(playerId, guildId, "quest-a", 100)

        assertTrue(repository.add(newer))
        assertTrue(repository.add(older))
        assertFalse(repository.add(older), "duplicate deterministic id must be ignored")

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = QuestCompletionNotificationRepositorySQL(storage)

        assertEquals(listOf(older, newer), repository.getPending(playerId))
        assertTrue(repository.markDelivered(older.id, 300))
        assertFalse(repository.markDelivered(older.id, 301))
        assertEquals(listOf(newer), repository.getPending(playerId))
    }

    private fun notification(
        playerId: UUID,
        guildId: UUID,
        questId: String,
        createdAt: Long,
    ): QuestCompletionNotification = QuestCompletionNotification(
        id = UUID.nameUUIDFromBytes(
            "quest-complete:$playerId:$guildId:$questId".toByteArray(),
        ),
        playerId = playerId,
        weekId = "2026-09-21",
        questId = questId,
        guildId = guildId,
        createdAt = createdAt,
    )
}
