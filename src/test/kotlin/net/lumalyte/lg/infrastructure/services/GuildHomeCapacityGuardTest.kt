package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildHomeCapacityGuardTest {
    @Test
    fun `grandfathered locations remain editable but do not grant new home slots`() {
        // Six legacy locations + nine bought Outposts were saved before the first prestige.
        assertFalse(maySetGuildHome(existingCount = 15, earnedCapacity = 11, replacingExisting = false))
        assertTrue(maySetGuildHome(existingCount = 15, earnedCapacity = 11, replacingExisting = true))

        for (capacity in 12..15) {
            assertFalse(maySetGuildHome(existingCount = 15, earnedCapacity = capacity, replacingExisting = false))
        }
        assertTrue(maySetGuildHome(existingCount = 15, earnedCapacity = 16, replacingExisting = false))
        assertFalse(maySetGuildHome(existingCount = 16, earnedCapacity = 16, replacingExisting = false))
    }
}
