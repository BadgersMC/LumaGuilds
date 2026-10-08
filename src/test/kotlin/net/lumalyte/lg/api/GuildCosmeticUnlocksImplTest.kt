// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-121: the public API delegates to the unlock service and never throws across the plugin boundary. */
internal class GuildCosmeticUnlocksImplTest {
    private val guildId = UUID.randomUUID()
    private val service = mockk<GuildCosmeticUnlockService>()
    private val api: GuildCosmeticUnlocks = GuildCosmeticUnlocksImpl(service)

    /** Delegates unlock revoke and query. */
    @DisplayName("delegates unlock revoke and query")
    @Test
    fun scenario1() {
        every { service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE) } returns
            true
        every { service.revoke(guildId, TEST_MENU_THEME, TEST_HALLOWEEN) } returns true
        every { service.unlockedKeys(guildId, TEST_MENU_THEME) } returns setOf(TEST_HALLOWEEN)

        assertTrue(api.unlockCosmetic(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE))
        assertTrue(api.revokeCosmetic(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))
        assertEquals(setOf(TEST_HALLOWEEN), api.getUnlockedCosmetics(guildId, TEST_MENU_THEME))
        verify(exactly = 1) {
            service.unlock(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, TEST_HOLIDAY_NAME, TEST_GRANT_SOURCE)
        }
    }

    /** Unexpected failures become false or empty. */
    @DisplayName("unexpected failures become false or empty")
    @Test
    fun scenario2() {
        every { service.unlock(any(), any(), any(), any(), any()) } throws IllegalStateException(TEST_DATABASE_FAILURE)
        every { service.revoke(any(), any(), any()) } throws IllegalStateException(TEST_DATABASE_FAILURE)
        every { service.unlockedKeys(any(), any()) } throws IllegalStateException(TEST_DATABASE_FAILURE)

        assertFalse(api.unlockCosmetic(guildId, TEST_MENU_THEME, TEST_HALLOWEEN, "x", TEST_GRANT_SOURCE))
        assertFalse(api.revokeCosmetic(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))
        assertEquals(emptySet(), api.getUnlockedCosmetics(guildId, TEST_MENU_THEME))
    }
}

private const val TEST_MENU_THEME = "MENU_THEME"

private const val TEST_HALLOWEEN = "HALLOWEEN"

private const val TEST_HOLIDAY_NAME = "Halloween '26"

private const val TEST_GRANT_SOURCE = "src"

private const val TEST_DATABASE_FAILURE = "db down"
