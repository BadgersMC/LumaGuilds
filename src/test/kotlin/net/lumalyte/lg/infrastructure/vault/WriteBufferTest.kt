package net.lumalyte.lg.infrastructure.vault

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriteBufferTest {
    @Test
    fun `deletions count toward size based flush threshold`() {
        val buffer = WriteBuffer(UUID.randomUUID())

        repeat(5) { slot -> buffer.bufferSlotChange(slot, null) }

        assertTrue(buffer.shouldFlush(maxSlots = 5, maxAgeMs = Long.MAX_VALUE))
    }

    @Test
    fun `first change timestamp starts when an empty buffer receives work`() {
        val buffer = WriteBuffer(UUID.randomUUID())
        buffer.firstChangeTimestamp = 123L
        buffer.lastChangeTimestamp = 123L

        buffer.bufferSlotChange(1, null)
        val firstChange = buffer.firstChangeTimestamp

        assertTrue(firstChange > 123L)
        assertEquals(firstChange, buffer.lastChangeTimestamp)
        buffer.bufferSlotChange(2, null)

        assertEquals(firstChange, buffer.firstChangeTimestamp)
    }
}