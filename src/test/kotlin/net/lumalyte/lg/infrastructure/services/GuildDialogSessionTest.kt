package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber") // Explicit millisecond fixtures exercise expiry boundaries.
internal class GuildDialogSessionTest {
    private val owner = UUID.randomUUID()

    @Test
    fun consumesOnceForOwner() {
        val state = GuildDialogSession(owner, 100L) { true }
        val page = state.beginPage(0L)
        assertFalse(state.consume(UUID.randomUUID(), page, 1L))
        assertTrue(state.consume(owner, page, 2L))
        assertFalse(state.consume(owner, page, 3L))
    }

    @Test
    fun rejectsExpiredPages() {
        val state = GuildDialogSession(owner, 100L) { true }
        val old = state.beginPage(0L)
        val current = state.beginPage(50L)
        assertFalse(state.consume(owner, old, 51L))
        assertFalse(state.consume(owner, current, 150L))
        assertFalse(state.consume(owner, current, 60L))
    }

    @Test
    fun rejectsRevokedSession() {
        var valid = true
        val state = GuildDialogSession(owner, 100L) { valid }
        val page = state.beginPage(0L)
        valid = false
        assertFalse(state.consume(owner, page, 1L))
        valid = true
        assertFalse(state.consume(owner, page, 2L))
        state.close()
        assertFalse(state.consume(owner, state.beginPage(3L), 4L))
    }

    @Test
    fun rejectsClockRollback() {
        val state = GuildDialogSession(owner, 100L) { true }
        assertFalse(state.consume(owner, state.beginPage(20L), 19L))
    }

    @Test fun dismissalAfterExpiryCannotAuthorizeAnActionOrCloseAnotherPage() {
        var valid = true
        val state = GuildDialogSession(owner, 100L) { valid }
        val page = state.beginPage(0)
        assertFalse(state.consume(owner, page, 100))
        assertFalse(state.dismiss(UUID.randomUUID(), page))
        assertTrue(state.dismiss(owner, page))
        assertFalse(state.consume(owner, page, 1))
        val next = GuildDialogSession(owner, 100L) { valid }
        val previous = next.beginPage(0)
        val current = next.beginPage(1)
        assertFalse(next.dismiss(owner, previous))
        valid = false
        assertFalse(next.dismiss(owner, current))
    }
}
