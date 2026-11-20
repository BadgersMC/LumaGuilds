package net.lumalyte.lg.application.services

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-179: one cooldown for command and menu home teleports. */
class HomeTeleportCooldownsTest {
    private var now = 1_000_000L
    private val cooldowns = HomeTeleportCooldowns { now }
    private val player = UUID.randomUUID()

    @Test
    fun `no recent teleport means no wait`() {
        assertEquals(0L, cooldowns.remainingSeconds(player, 30))
    }

    @Test
    fun `after a teleport the player waits out the cooldown, rounded up`() {
        cooldowns.markTeleported(player)
        now += 10_500
        assertEquals(20L, cooldowns.remainingSeconds(player, 30))
        now += 19_500
        assertEquals(0L, cooldowns.remainingSeconds(player, 30))
    }

    @Test
    fun `a shorter cooldown applies to the existing stamp`() {
        cooldowns.markTeleported(player)
        now += 15_000
        assertEquals(0L, cooldowns.remainingSeconds(player, 15))
    }
}
