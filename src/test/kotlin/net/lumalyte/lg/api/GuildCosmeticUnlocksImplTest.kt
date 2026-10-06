package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-121: the public API delegates to the unlock service and never throws across the plugin boundary. */
class GuildCosmeticUnlocksImplTest {
    private val guildId = UUID.randomUUID()
    private val service = mockk<GuildCosmeticUnlockService>()
    private val api: GuildCosmeticUnlocks = GuildCosmeticUnlocksImpl(service)

    @Test
    fun `delegates unlock revoke and query`() {
        every { service.unlock(guildId, "MENU_THEME", "HALLOWEEN", "Halloween '26", "src") } returns true
        every { service.revoke(guildId, "MENU_THEME", "HALLOWEEN") } returns true
        every { service.unlockedKeys(guildId, "MENU_THEME") } returns setOf("HALLOWEEN")

        assertTrue(api.unlockCosmetic(guildId, "MENU_THEME", "HALLOWEEN", "Halloween '26", "src"))
        assertTrue(api.revokeCosmetic(guildId, "MENU_THEME", "HALLOWEEN"))
        assertEquals(setOf("HALLOWEEN"), api.getUnlockedCosmetics(guildId, "MENU_THEME"))
        verify(exactly = 1) { service.unlock(guildId, "MENU_THEME", "HALLOWEEN", "Halloween '26", "src") }
    }

    @Test
    fun `unexpected failures become false or empty`() {
        every { service.unlock(any(), any(), any(), any(), any()) } throws IllegalStateException("db down")
        every { service.revoke(any(), any(), any()) } throws IllegalStateException("db down")
        every { service.unlockedKeys(any(), any()) } throws IllegalStateException("db down")

        assertFalse(api.unlockCosmetic(guildId, "MENU_THEME", "HALLOWEEN", "x", "src"))
        assertFalse(api.revokeCosmetic(guildId, "MENU_THEME", "HALLOWEEN"))
        assertEquals(emptySet(), api.getUnlockedCosmetics(guildId, "MENU_THEME"))
    }
}
