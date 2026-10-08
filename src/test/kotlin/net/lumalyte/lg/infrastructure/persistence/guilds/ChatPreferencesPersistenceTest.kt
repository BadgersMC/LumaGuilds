package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

internal class ChatPreferencesPersistenceTest : RewardSqlTestFixture() {
    @Test fun preferencesSurviveReloadAndFailedWrite() {
        val storage = openStorage()
        try {
            val id = UUID.randomUUID()
            val repository = ChatSettingsRepositorySQLite(storage)
            assertEquals(ChatVisibilitySettings(id), repository.getVisibilitySettings(id))
            assertTrue(repository.updateRateLimit(net.lumalyte.lg.domain.values.ChatRateLimit(id, lastAnnounceTime = 1800000000000L)))
            assertEquals(1800000000000L, ChatSettingsRepositorySQLite(storage).getRateLimit(id).lastAnnounceTime)
            val settings = ChatVisibilitySettings(id, allyChatVisible = false, globalChatVisible = false, destinationIndicator = true)
            assertTrue(repository.updateVisibilitySettings(settings))
            assertEquals(settings, ChatSettingsRepositorySQLite(storage).getVisibilitySettings(id))
            val trigger = if (storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB) "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'failure'" else "BEGIN SELECT RAISE(ABORT, 'failure'); END"
            storage.connection.executeUpdate("CREATE TRIGGER fail_preferences BEFORE INSERT ON chat_ui_preferences $trigger")
            assertFails { repository.updateVisibilitySettings(settings.copy(globalChatVisible = true, allyChatVisible = true)) }
            assertEquals(settings, repository.getVisibilitySettings(id))
            assertEquals(settings, ChatSettingsRepositorySQLite(storage).getVisibilitySettings(id))
        } finally { closeStorage(storage) }
    }
}
