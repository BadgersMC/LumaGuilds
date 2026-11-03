package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DialogV2PreferencesSQLTest : RewardSqlTestFixture() {
    @Test
    fun presentationAndListingWritesMergeWithoutRestoringStaleAccessOrPreferences() {
        val storage = openStorage()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, is_open INTEGER NOT NULL)")
        val id = UUID.randomUUID()
        storage.connection.executeUpdate("INSERT INTO guilds (id, is_open) VALUES (?, 0)", id.toString())
        val repository = RecruitmentDirectoryRepositorySQL(storage)
        val original = RecruitmentProfile(id, RecruitmentMode.IN_GAME, pitch = "First")
        repository.save(original)
        repository.save(original.copy(mode = RecruitmentMode.JOIN_IMMEDIATELY, pitch = "Latest"))
        repository.savePresentation(
            original.copy(publicListing = false, joinAnnouncements = false, showBanner = false, questReminders = true),
        )
        val stored = requireNotNull(RecruitmentDirectoryRepositorySQL(storage).profile(id))
        assertEquals(RecruitmentMode.JOIN_IMMEDIATELY, stored.mode)
        assertEquals("Latest", stored.pitch)
        assertFalse(stored.publicListing)
        assertFalse(stored.joinAnnouncements)
        assertFalse(stored.showBanner)
        assertTrue(stored.questReminders)
        repository.save(original.copy(pitch = "Edited listing"))
        val edited = requireNotNull(repository.profile(id))
        assertFalse(edited.publicListing)
        assertTrue(edited.questReminders)
        assertEquals(RecruitmentMode.IN_GAME, edited.mode)
        repository.savePresentation(edited.copy(publicListing = true), setOf("publicListing"))
        repository.savePresentation(edited.copy(showBanner = true), setOf("showBanner"))
        assertTrue(requireNotNull(repository.profile(id)).publicListing)
        assertTrue(requireNotNull(repository.profile(id)).showBanner)
    }

    @Test
    fun reminderReceiptPersistsAndDoesNotChangePrivateRecruitmentDigest() {
        val storage = openStorage()
        val player = UUID.randomUUID()
        val guild = UUID.randomUUID()
        val first = RecruitmentDirectoryRepositorySQL(storage)
        assertTrue(first.consumeNotification(player, "a".repeat(64)))
        assertTrue(first.consumeQuestReminder(player, guild, "week-1"))
        val reopened = RecruitmentDirectoryRepositorySQL(storage)
        assertFalse(reopened.consumeQuestReminder(player, guild, "week-1"))
        assertTrue(reopened.consumeQuestReminder(player, guild, "week-2"))
        assertFalse(reopened.consumeNotification(player, "a".repeat(64)))
    }
}
