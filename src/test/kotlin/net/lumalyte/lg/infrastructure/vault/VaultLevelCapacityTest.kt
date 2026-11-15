package net.lumalyte.lg.infrastructure.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VaultLevelCapacityTest {
    @Test fun `invalid or reset levels never unlock extra vault rows`() {
        for (level in listOf(-100, -1, 0, 1)) {
            assertEquals(9, VaultLevelCapacity.slots(level), "level=$level must fail closed")
        }
    }

    @Test fun `ordinary levels unlock by row and cap at fifty four`() {
        val expected = mapOf(
            2 to 18, 5 to 18, 6 to 27, 10 to 27,
            11 to 36, 15 to 36, 16 to 45, 20 to 45,
            21 to 54, 100 to 54,
        )
        for ((level, slots) in expected) {
            assertEquals(slots, VaultLevelCapacity.slots(level), "level=$level")
        }
    }
}
