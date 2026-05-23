package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("UndocumentedPublicFunction")
class BannermanVisibilityTest {

    @Test
    fun visibleWhenPlayerHasNoHideCondition() {
        assertTrue(BannermanVisibility.shouldShow(hasElytra = false, hasInvisibility = false))
    }

    @Test
    fun hiddenWhenElytraEquipped() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = true, hasInvisibility = false))
    }

    @Test
    fun hiddenWhenInvisibilityActive() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = false, hasInvisibility = true))
    }

    @Test
    fun hiddenWhenBothPresent() {
        assertFalse(BannermanVisibility.shouldShow(hasElytra = true, hasInvisibility = true))
    }
}
