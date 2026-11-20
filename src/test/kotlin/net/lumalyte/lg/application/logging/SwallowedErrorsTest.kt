package net.lumalyte.lg.application.logging

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class SwallowedErrorsTest {
    @Test
    fun `logging a handled error never throws, even when repeated within the interval`() {
        assertDoesNotThrow {
            repeat(3) { SwallowedErrors.log("SwallowedErrorsTest:1", IllegalStateException("boom"), now = 1_000L + it) }
            SwallowedErrors.log("SwallowedErrorsTest:1", IllegalStateException("boom"), now = 120_000L)
        }
    }
}
