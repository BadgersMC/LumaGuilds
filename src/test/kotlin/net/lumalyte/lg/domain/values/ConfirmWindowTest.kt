package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** REQ-214: leave and disband only happen when the same command is run again within 30 seconds. */
internal class ConfirmWindowTest {
    private var now = 0L
    private val window = ConfirmWindow(30_000) { now }
    private val player = UUID.randomUUID()

    @Test
    fun secondRunWithinWindowConfirms() {
        assertFalse(window.confirm(player, "leave"))
        now = 29_000
        assertTrue(window.confirm(player, "leave"))
        assertFalse(window.confirm(player, "leave"), "a confirmed action starts over")
    }

    @Test
    fun lateOrDifferentRunAsksAgain() {
        assertFalse(window.confirm(player, "leave"))
        now = 31_000
        assertFalse(window.confirm(player, "leave"))
        assertFalse(window.confirm(player, "disband"))
        assertFalse(window.confirm(UUID.randomUUID(), "disband"))
    }
}
