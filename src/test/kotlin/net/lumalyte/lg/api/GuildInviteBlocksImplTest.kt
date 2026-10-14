package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.InviteBlockService
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class GuildInviteBlocksImplTest {
    /** Verifies the API passes straight through to the invite block service. */
    @Test
    fun apiDelegatesToService() {
        val owner = UUID.randomUUID()
        val target = UUID.randomUUID()
        val service =
            mockk<InviteBlockService> {
                every { isBlocked(owner, target) } returns true
                every { setBlocked(owner, target, false) } returns false
                every { blockedBy(owner) } returns setOf(target)
            }
        val api: GuildInviteBlocks = GuildInviteBlocksImpl(service)
        assertEquals(1, api.apiVersion())
        assertTrue(api.isBlocked(owner, target))
        assertFalse(api.setBlocked(owner, target, false))
        assertEquals(setOf(target), api.getBlocked(owner))
    }
}
