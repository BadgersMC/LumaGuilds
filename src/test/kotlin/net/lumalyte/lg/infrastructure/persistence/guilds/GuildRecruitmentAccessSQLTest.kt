package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildRecruitmentAccessSQLTest : RewardSqlTestFixture() {
    private val guild = UUID.randomUUID()
    private val cache = mockk<GuildRepository>(relaxed = true)
    private val storage by lazy {
        openStorage().also {
            it.connection.executeUpdate(
                "CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, is_open INTEGER NOT NULL)",
            )
            it.connection.executeUpdate("INSERT INTO guilds (id, is_open) VALUES (?, 0)", guild.toString())
        }
    }
    private val listings by lazy { RecruitmentDirectoryRepositorySQL(storage, cache) }
    private val access by lazy { GuildRecruitmentAccessSQL(storage) }

    @org.junit.jupiter.api.DisplayName("committedPublicationRefreshesCacheAndStaleSettingsCannotReopenGuild")
    @Test
    fun staleSettingsPreserveAccess() {
        val database = openStorage()
        migrateProductionSchema(database)
        val repository = GuildRepositorySQLite(database)
        val original = openGuild()
        assertTrue(repository.add(original))
        val profiles = RecruitmentDirectoryRepositorySQL(database, repository)
        profiles.save(RecruitmentProfile(guild, RecruitmentMode.IN_GAME))
        assertFalse(requireNotNull(repository.getById(guild)).isOpen)
        assertTrue(repository.update(original.copy(name = "Renamed")))
        assertFalse(requireNotNull(repository.getById(guild)).isOpen)
        assertTrue(repository.updateAccess(guild, true))
        assertTrue(requireNotNull(repository.getById(guild)).isOpen)
        assertEquals(RecruitmentMode.JOIN_IMMEDIATELY, profiles.profile(guild)?.mode)
        assertTrue(requireNotNull(GuildRepositorySQLite(database).getById(guild)).isOpen)
    }

    private fun openGuild(): net.lumalyte.lg.domain.entities.Guild {
        return net.lumalyte.lg.domain.entities.Guild(
            id = guild,
            name = "Recruitment",
            createdAt = java.time.Instant.now(),
            isOpen = true,
        )
    }

    @org.junit.jupiter.api.DisplayName("publishingEveryMethodSynchronizesAccess")
    @Test
    fun publishedMethodsMirrorAccess() {
        RecruitmentMode.entries.forEach { mode ->
            listings.save(
                RecruitmentProfile(
                    guild,
                    mode,
                    discordInvite = "https://discord.gg/Example",
                    discordInstructions = "Open a ticket",
                ),
            )
            assertEquals(mode == RecruitmentMode.JOIN_IMMEDIATELY, isOpen())
            assertEquals(mode, listings.profile(guild)?.mode)
        }
        verify(exactly = RecruitmentMode.entries.size) { cache.refreshCachedAccess(guild) }
    }

    @org.junit.jupiter.api.DisplayName("legacyTogglePreservesListingFields")
    @Test
    fun legacyTogglePreservesFields() {
        val original =
            RecruitmentProfile(guild, RecruitmentMode.IN_GAME, pitch = "Builders welcome", language = "English")
        listings.save(original)
        assertTrue(access.setOpen(guild, true))
        assertTrue(isOpen())
        assertEquals(original.copy(mode = RecruitmentMode.JOIN_IMMEDIATELY), listings.profile(guild))
        assertTrue(access.setOpen(guild, false))
        assertFalse(isOpen())
        assertEquals(original.copy(mode = RecruitmentMode.CLOSED), listings.profile(guild))
    }

    @org.junit.jupiter.api.DisplayName("legacyGuildKeepsFallbackWithoutInventingListing")
    @Test
    fun legacyAccessKeepsFallback() {
        assertNull(listings.profile(guild))
        assertTrue(access.setOpen(guild, true))
        assertTrue(isOpen())
        assertNull(listings.profile(guild))
        assertFalse(access.setOpen(UUID.randomUUID(), true))
    }

    @org.junit.jupiter.api.DisplayName("rejectedProfileWriteRollsBackAccessAndDoesNotRefreshCache")
    @Test
    fun profileFailureRollsBackAccess() {
        listings.save(RecruitmentProfile(guild, RecruitmentMode.CLOSED))
        rejectProfileUpdates()
        assertFails { listings.save(RecruitmentProfile(guild, RecruitmentMode.JOIN_IMMEDIATELY)) }
        assertFalse(isOpen())
        assertEquals(RecruitmentMode.CLOSED, listings.profile(guild)?.mode)
        verify(exactly = 1) { cache.refreshCachedAccess(guild) }
    }

    @org.junit.jupiter.api.DisplayName("rejectedLegacyProfileWriteRollsBackToggle")
    @Test
    fun toggleFailureRollsBackAccess() {
        listings.save(RecruitmentProfile(guild, RecruitmentMode.IN_GAME))
        rejectProfileUpdates()
        assertFails { access.setOpen(guild, true) }
        assertFalse(isOpen())
        assertEquals(RecruitmentMode.IN_GAME, listings.profile(guild)?.mode)
    }

    private fun isOpen(): Boolean {
        return requireNotNull(
            storage.connection.getFirstRow(
                "SELECT is_open FROM guilds WHERE id = ?", guild.toString(),
            ),
        ).getInt("is_open") == 1
    }

    private fun rejectProfileUpdates() {
        val body =
            if (storage.dialect == SqlDialect.MARIADB) {
                "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected write failure'"
            } else {
                "BEGIN SELECT RAISE(ABORT, 'injected write failure'); END"
            }
        storage.connection.executeUpdate(
            "CREATE TRIGGER reject_profile_update BEFORE UPDATE ON guild_recruitment_profiles $body",
        )
    }
}
