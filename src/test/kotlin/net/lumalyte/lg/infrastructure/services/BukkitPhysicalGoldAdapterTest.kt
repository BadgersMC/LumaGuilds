package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BukkitPhysicalGoldAdapterTest {
    @Test
    fun `one configured block converts to nine base units`() {
        assertEquals(9, BukkitPhysicalGoldAdapter.availableValue(baseCount = 0, blockCount = 1, blockValue = 9))
    }

    @Test
    fun `mixed stacks reserve an exact partial requested value`() {
        assertEquals(
            BukkitPhysicalGoldAdapter.DenominationSelection(base = 1, blocks = 2),
            BukkitPhysicalGoldAdapter.selectExact(baseCount = 5, blockCount = 3, blockValue = 9, requestedValue = 19)
        )
    }

    @Test
    fun `reservation fails when value exists but cannot be represented exactly`() {
        assertNull(BukkitPhysicalGoldAdapter.selectExact(baseCount = 0, blockCount = 2, blockValue = 9, requestedValue = 10))
    }
}
