package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val AFTER_ONBOARDING_SECONDS = 999_999L
private const val REMINDER_INTERVAL_MINUTES = 30L
private const val REMINDER_INTERVAL_SECONDS = 1_800L
private const val SHA256_HEX_LENGTH = 64

internal class RecruitmentDirectoryRepositorySQLTest : RewardSqlTestFixture() {
    private val player = UUID.randomUUID()
    private val now = Instant.parse("2026-10-14T12:00:00Z")

    private fun prepareGuild(
        storage: net.lumalyte.lg.infrastructure.persistence.storage.Storage<co.aikar.idb.Database>,
        id: UUID,
    ) {
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guilds (id VARCHAR(36) PRIMARY KEY, is_open INTEGER NOT NULL)",
        )
        storage.connection.executeUpdate("INSERT INTO guilds (id, is_open) VALUES (?, 0)", id.toString())
    }

    @Test
    fun listingTimestampPersists() {
        val storage = openStorage()
        val repository = RecruitmentDirectoryRepositorySQL(storage)
        val profile = RecruitmentProfile(UUID.randomUUID(), RecruitmentMode.IN_GAME)
        prepareGuild(storage, profile.guildId)
        assertNull(repository.updatedAt(profile.guildId))
        val before = Instant.now()
        repository.save(profile)
        val at = requireNotNull(RecruitmentDirectoryRepositorySQL(storage).updatedAt(profile.guildId))
        assertTrue(at >= before && at <= Instant.now())
        val row =
            storage.connection.getFirstRow(
                "SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?",
                profile.guildId.toString(),
            )
        val legacy = org.json.JSONObject(requireNotNull(row).getString("profile"))
        legacy.remove("updatedAt")
        storage.connection.executeUpdate(
            "UPDATE guild_recruitment_profiles SET profile = ? WHERE guild_id = ?",
            legacy.toString(),
            profile.guildId.toString(),
        )
        assertNull(repository.updatedAt(profile.guildId))
        assertEquals(profile, repository.profile(profile.guildId))
    }

    @org.junit.jupiter.api.DisplayName("profile round trips Discord instructions and optional filters")
    @Test
    fun profileRoundTripsDiscordIn1() {
        val storage = openStorage()
        val first = RecruitmentDirectoryRepositorySQL(storage)
        val profile =
            RecruitmentProfile(
                UUID.randomUUID(), RecruitmentMode.DISCORD, "Welcome", "Be kind",
                setOf("Building", "Quests"), "Evenings", "English", true, "https://discord.gg/Example", "Open a ticket",
            )
        assertNull(first.profile(profile.guildId))
        prepareGuild(storage, profile.guildId)
        first.save(profile)
        assertEquals(profile, RecruitmentDirectoryRepositorySQL(storage).profile(profile.guildId))
        first.save(profile.copy(mode = RecruitmentMode.CLOSED, pitch = "Paused"))
        assertEquals("Paused", first.profile(profile.guildId)?.pitch)
    }

    @org.junit.jupiter.api.DisplayName("one time prompt and personal opt out survive repository reconstruction")
    @Test
    fun oneTimePromptAndPersonalOp2() {
        val storage = openStorage()
        val first = RecruitmentDirectoryRepositorySQL(storage)
        assertTrue(first.consumePrompt(player, now, null))
        val reopened = RecruitmentDirectoryRepositorySQL(storage)
        assertFalse(reopened.consumePrompt(player, now.plusSeconds(AFTER_ONBOARDING_SECONDS), null))
        reopened.setReminders(player, false)
        assertFalse(first.remindersEnabled(player))
        assertFalse(
            first.consumePrompt(
                player,
                now.plusSeconds(AFTER_ONBOARDING_SECONDS),
                Duration.ofMinutes(REMINDER_INTERVAL_MINUTES),
            ),
        )
    }

    @org.junit.jupiter.api.DisplayName("Reminder timing survives preference changes")
    @Test
    fun optionalReminderWaitsForIt3() {
        val repository = RecruitmentDirectoryRepositorySQL(openStorage())
        assertTrue(repository.consumePrompt(player, now, Duration.ofMinutes(REMINDER_INTERVAL_MINUTES)))
        repository.setReminders(player, false)
        repository.setReminders(player, true)
        assertFalse(
            repository.consumePrompt(
                player,
                now.plusSeconds(REMINDER_INTERVAL_SECONDS - 1),
                Duration.ofMinutes(REMINDER_INTERVAL_MINUTES),
            ),
        )
        assertTrue(
            repository.consumePrompt(
                player,
                now.plusSeconds(REMINDER_INTERVAL_SECONDS),
                Duration.ofMinutes(REMINDER_INTERVAL_MINUTES),
            ),
        )
        assertFalse(
            repository.consumePrompt(
                player,
                now.plusSeconds(REMINDER_INTERVAL_SECONDS),
                Duration.ofMinutes(REMINDER_INTERVAL_MINUTES),
            ),
        )
    }

    @org.junit.jupiter.api.DisplayName("private notification digest survives reconnects without replay")
    @Test
    fun privateNotificationDigestS4() {
        val storage = openStorage()
        val first = RecruitmentDirectoryRepositorySQL(storage)
        val digest = "a".repeat(SHA256_HEX_LENGTH)
        assertTrue(first.consumeNotification(player, digest))
        assertFalse(RecruitmentDirectoryRepositorySQL(storage).consumeNotification(player, digest))
        assertTrue(first.consumeNotification(player, "b".repeat(SHA256_HEX_LENGTH)))
    }
}
