package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

internal class ChatPreferencesPersistenceTest : RewardSqlTestFixture() {
    @Test fun preferencePersistence() {
        val storage = openStorage()
        try {
            val id = UUID.randomUUID()
            val repository = ChatSettingsRepositorySQLite(storage)
            assertEquals(ChatVisibilitySettings(id), repository.getVisibilitySettings(id))
            checkRateLimit(repository, storage, id)
            val settings = testPreferences(id)
            assertTrue(repository.updateVisibilitySettings(settings))
            assertEquals(settings, ChatSettingsRepositorySQLite(storage).getVisibilitySettings(id))
            rejectPreferences(storage)
            assertRollback(repository, storage, settings)
        } finally {
            closeStorage(storage)
        }
    }

    private fun assertRollback(
        repository: ChatSettingsRepositorySQLite,
        storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>,
        settings: ChatVisibilitySettings,
    ) {
        assertFails {
            repository.updateVisibilitySettings(
                settings.copy(globalChatVisible = true, allyChatVisible = true),
            )
        }
        assertEquals(settings, repository.getVisibilitySettings(settings.playerId))
        assertEquals(settings, ChatSettingsRepositorySQLite(storage).getVisibilitySettings(settings.playerId))
    }

    private fun checkRateLimit(
        repository: ChatSettingsRepositorySQLite,
        storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>,
        id: UUID,
    ) {
        assertTrue(
            repository.updateRateLimit(
                net.lumalyte.lg.domain.values
                    .ChatRateLimit(id, lastAnnounceTime = FUTURE_ANNOUNCEMENT),
            ),
        )
        assertEquals(FUTURE_ANNOUNCEMENT, ChatSettingsRepositorySQLite(storage).getRateLimit(id).lastAnnounceTime)
    }

    private fun testPreferences(id: UUID) =
        ChatVisibilitySettings(
            id,
            allyChatVisible = false,
            globalChatVisible = false,
            destinationIndicator = true,
        )

    private fun rejectPreferences(
        storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>,
    ) {
        val trigger =
            if (storage.dialect ==
                net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB
            ) {
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'failure'"
            } else {
                "BEGIN SELECT RAISE(ABORT, 'failure'); END"
            }
        storage.connection.executeUpdate(
            "CREATE TRIGGER fail_preferences BEFORE INSERT ON chat_ui_preferences $trigger",
        )
    }

    private companion object {
        const val FUTURE_ANNOUNCEMENT = 1_800_000_000_000L
    }
}
