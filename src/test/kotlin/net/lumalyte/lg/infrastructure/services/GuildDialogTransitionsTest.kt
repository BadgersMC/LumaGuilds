package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class GuildDialogTransitionsTest {
    private val player = mockk<Player>(relaxed = true)

    @Test
    fun replacingADialogNeverClearsTheReplacement() {
        GuildDialogTransitions.shown(player)
        GuildDialogTransitions.dispatch(player) { GuildDialogTransitions.shown(player) }
        verify(exactly = 0) { player.closeDialog() }
    }

    @Test
    fun terminalActionIsDismissedAfterItRuns() {
        GuildDialogTransitions.shown(player)
        GuildDialogTransitions.dispatch(player) {
            verify(exactly = 0) { player.closeDialog() }
        }
        verify(exactly = 1) { player.closeDialog() }
    }

    @Test
    fun anotherPlayersReplacementCannotKeepThisPlayersDialogOpen() {
        val other = mockk<Player>(relaxed = true)
        GuildDialogTransitions.dispatch(player) { GuildDialogTransitions.shown(other) }
        verify(exactly = 1) { player.closeDialog() }
        verify(exactly = 0) { other.closeDialog() }
    }

    @Test
    fun errorsRemainOwnedByTheExistingFailClosedCallbackBoundary() {
        assertFailsWith<IllegalStateException> {
            GuildDialogTransitions.dispatch(player) { error("Adapter failure") }
        }
        verify(exactly = 0) { player.closeDialog() }
    }
}
