package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("UndocumentedPublicFunction")
internal class BannermanVisibilityTest {

    @Test
    fun visibleWhenNoHideCondition() {
        assertTrue(BannermanVisibility.shouldShow(hasInvisibility = false))
    }

    @Test
    fun hiddenWhenInvisibilityActive() {
        assertFalse(BannermanVisibility.shouldShow(hasInvisibility = true))
    }
}
