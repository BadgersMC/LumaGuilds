package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.papermc.paper.registry.data.dialog.DialogBase
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class GuildDialogTransitionsTest {
    private val player = mockk<Player>(relaxed = true)

    @Test
    fun keepOpenDialogsDisablePausingBeforeSettingTheAfterAction() {
        val builder = mockk<DialogBase.Builder>()
        every { builder.pause(false) } returns builder
        every { builder.afterAction(DialogBase.DialogAfterAction.NONE) } returns builder
        GuildDialogTransitions.configure(builder)
        verifyOrder {
            builder.pause(false)
            builder.afterAction(DialogBase.DialogAfterAction.NONE)
        }
    }

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
