// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildCosmeticUnlockRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.DisplayName
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-121: unlock/revoke policy and holiday theme availability. */
internal class GuildCosmeticUnlockServiceTest {
    private val guildId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val guilds = mockk<GuildRepository>(relaxed = true)
    private val unlocks = InMemoryUnlocks()
    private val service = GuildCosmeticUnlockService(guilds, unlocks) { now }

    private fun guild(theme: GuiTheme = GuiTheme.DEFAULT) =
        Guild(id = guildId, name = "Enthusiasts", createdAt = now, guiTheme = theme)

    /** Holiday themes are locked until unlocked and progression themes never are. */
    @DisplayName("holiday themes are locked until unlocked and progression themes never are")
    @Test
    fun scenario1() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(GuiTheme.HALLOWEEN.requiresUnlock)
        assertTrue(GuiTheme.CHRISTMAS.requiresUnlock)
        assertFalse(GuiTheme.EMBERSTONE.requiresUnlock)

        assertFalse(service.isThemeAvailable(guildId, GuiTheme.HALLOWEEN))
        assertTrue(service.isThemeAvailable(guildId, GuiTheme.EMBERSTONE))

        assertTrue(
            service.unlock(guildId, TEST_LOWER_CATEGORY, TEST_LOWER_THEME, TEST_HOLIDAY_NAME, "event:halloween-2026"),
        )
        assertTrue(service.isThemeAvailable(guildId, GuiTheme.HALLOWEEN))
        assertFalse(service.isThemeAvailable(guildId, GuiTheme.CHRISTMAS))
        assertEquals(TEST_HOLIDAY_NAME, service.themeDisplayName(guildId, GuiTheme.HALLOWEEN))
        assertEquals(GuiTheme.EMBERSTONE.displayName, service.themeDisplayName(guildId, GuiTheme.EMBERSTONE))
    }

    /** Unlock is idempotent and records normalised values. */
    @DisplayName("unlock is idempotent and records normalised values")
    @Test
    fun scenario2() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, " menu_theme ", TEST_LOWER_THEME, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE))
        assertTrue(service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, "Other", TEST_GRANT_SOURCE))
        assertEquals(setOf(TEST_HALLOWEEN), service.unlockedKeys(guildId, TEST_LOWER_CATEGORY))
        assertEquals(
            GuildCosmeticUnlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE, now),
            unlocks.get(guildId, TEST_MENU_THEME, TEST_HALLOWEEN),
        )
    }

    /** Unknown keys are stored for forward compatibility. */
    @DisplayName("unknown keys are stored for forward compatibility")
    @Test
    fun scenario3() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, "BADGE", "HALLOWEEN_2026", "Halloween 2026 Badge", TEST_GRANT_SOURCE))
        assertTrue(service.unlock(guildId, TEST_MENU_THEME, TEST_UNKNOWN_COSMETIC, "Spring Garden", TEST_GRANT_SOURCE))
        assertEquals(setOf(TEST_UNKNOWN_COSMETIC), service.unlockedKeys(guildId, TEST_MENU_THEME))
    }

    /** Missing guild and invalid input are rejected. */
    @DisplayName("missing guild and invalid input are rejected")
    @Test
    fun scenario4() {
        every { guilds.getById(guildId) } returns null
        assertFalse(service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_SHORT_LABEL, TEST_GRANT_SOURCE))
        assertFalse(service.revoke(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))

        every { guilds.getById(guildId) } returns guild()
        assertFalse(service.unlock(guildId, TEST_MENU_THEME, " ", TEST_SHORT_LABEL, TEST_GRANT_SOURCE))
        assertFalse(service.unlock(guildId, TEST_MENU_THEME, "K".repeat(65), TEST_SHORT_LABEL, TEST_GRANT_SOURCE))
        assertFalse(service.unlock(guildId, "", TEST_HALLOWEEN, TEST_SHORT_LABEL, TEST_GRANT_SOURCE))
        assertTrue(unlocks.getForGuild(guildId).isEmpty())
    }

    /** Blank display name falls back to the key. */
    @DisplayName("blank display name falls back to the key")
    @Test
    fun scenario5() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, TEST_MENU_THEME, TEST_CHRISTMAS, "  ", TEST_GRANT_SOURCE))
        assertEquals(TEST_CHRISTMAS, unlocks.get(guildId, TEST_MENU_THEME, TEST_CHRISTMAS)?.displayName)
    }

    /** Revoking the equipped holiday theme resets only the theme. */
    @DisplayName("revoking the equipped holiday theme resets only the theme")
    @Test
    fun scenario6() {
        every { guilds.getById(guildId) } returns guild(GuiTheme.HALLOWEEN)
        every { guilds.updateGuiTheme(guildId, GuiTheme.HALLOWEEN, GuiTheme.DEFAULT) } returns true
        service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE)

        assertTrue(service.revoke(guildId, TEST_LOWER_CATEGORY, TEST_LOWER_THEME))
        assertTrue(service.revoke(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))

        verify(atLeast = 1) { guilds.updateGuiTheme(guildId, GuiTheme.HALLOWEEN, GuiTheme.DEFAULT) }
        // A full-record write would overwrite concurrent changes to the guild.
        verify(exactly = 0) { guilds.update(any()) }
        assertFalse(service.isThemeAvailable(guildId, GuiTheme.HALLOWEEN))
    }

    /** Revoking a theme the guild is not using leaves its theme alone. */
    @DisplayName("revoking a theme the guild is not using leaves its theme alone")
    @Test
    fun scenario7() {
        every { guilds.getById(guildId) } returns guild(GuiTheme.EMBERSTONE)
        service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE)
        assertTrue(service.revoke(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))
        verify(exactly = 0) { guilds.update(any()) }
    }

    /** Persistence failure is reported. */
    @DisplayName("persistence failure is reported")
    @Test
    fun scenario8() {
        every { guilds.getById(guildId) } returns guild()
        val failing = mockk<GuildCosmeticUnlockRepository> {
            every { get(any(), any(), any()) } returns null
            every { saveIfAbsent(any()) } returns false
        }
        assertFalse(
            GuildCosmeticUnlockService(guilds, failing) {
                now
            }.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_SHORT_LABEL, TEST_GRANT_SOURCE),
        )
    }

    private class InMemoryUnlocks : GuildCosmeticUnlockRepository {
        private val rows = linkedMapOf<Triple<UUID, String, String>, GuildCosmeticUnlock>()
        override fun getForGuild(guildId: UUID) = rows.values.filter { it.guildId == guildId }
        override fun get(guildId: UUID, type: String, key: String) = rows[Triple(guildId, type, key)]
        override fun saveIfAbsent(unlock: GuildCosmeticUnlock): Boolean {
            rows.putIfAbsent(Triple(unlock.guildId, unlock.type, unlock.key), unlock)
            return true
        }
        override fun delete(guildId: UUID, type: String, key: String): Boolean {
            rows.remove(Triple(guildId, type, key))
            return true
        }
    }
}

private const val TEST_MENU_THEME = "MENU_THEME"

private const val TEST_HALLOWEEN = "HALLOWEEN"

private const val TEST_HOLIDAY_NAME = "Halloween '26"

private const val TEST_GRANT_SOURCE = "src"

private const val TEST_LOWER_CATEGORY = "menu_theme"

private const val TEST_LOWER_THEME = "halloween"

private const val TEST_UNKNOWN_COSMETIC = "SPRING_GARDEN"

private const val TEST_SHORT_LABEL = "x"

private const val TEST_CHRISTMAS = "CHRISTMAS"
