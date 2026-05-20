package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannermanVisibilityTest {

    @Test
    fun `visible when player has no hide condition`() {
        assertTrue(BannermanVisibility.shouldShow(hasElytra = false, hasInvisibility = false))
    }

    @Test
    fun `hidden when elytra equipped`() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = true, hasInvisibility = false))
    }

    @Test
    fun `hidden when invisibility active`() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = false, hasInvisibility = true))
    }

    @Test
    fun `hidden when both conditions present`() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = true, hasInvisibility = true))
    }
}
