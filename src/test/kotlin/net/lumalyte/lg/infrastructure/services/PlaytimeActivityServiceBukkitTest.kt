package net.lumalyte.lg.infrastructure.services

import org.enthusia.playtime.activity.ActivityState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Decision matrix for the PlayTime activity gate: only AFK and SUSPICIOUS block XP;
 * ACTIVE and IDLE still earn; unknown/unavailable state never blocks.
 */
class PlaytimeActivityServiceBukkitTest {

    @Test
    fun `ACTIVE and IDLE still earn`() {
        assertFalse(PlaytimeActivityServiceBukkit.isBlockedState(ActivityState.ACTIVE))
        assertFalse(PlaytimeActivityServiceBukkit.isBlockedState(ActivityState.IDLE))
    }

    @Test
    fun `AFK and SUSPICIOUS are blocked`() {
        assertTrue(PlaytimeActivityServiceBukkit.isBlockedState(ActivityState.AFK))
        assertTrue(PlaytimeActivityServiceBukkit.isBlockedState(ActivityState.SUSPICIOUS))
    }

    @Test
    fun `unavailable state never blocks`() {
        assertFalse(PlaytimeActivityServiceBukkit.isBlockedState(null))
    }
}
