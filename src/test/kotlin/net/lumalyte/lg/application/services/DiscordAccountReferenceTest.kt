package net.lumalyte.lg.application.services

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscordAccountReferenceTest {
    @Test
    fun `reference preserves provider opaque value`() {
        val reference = DiscordAccountReference("legacy:123456789012345678")

        assertEquals("legacy:123456789012345678", reference.value)
    }

    @Test
    fun `reference rejects blank values`() {
        assertFailsWith<IllegalArgumentException> {
            DiscordAccountReference("   ")
        }
    }

    @Test
    fun `reference rejects control characters`() {
        assertFailsWith<IllegalArgumentException> {
            DiscordAccountReference("account\n123")
        }
    }

    @Test
    fun `reference rejects unbounded values`() {
        assertFailsWith<IllegalArgumentException> {
            DiscordAccountReference("x".repeat(257))
        }
    }
}
