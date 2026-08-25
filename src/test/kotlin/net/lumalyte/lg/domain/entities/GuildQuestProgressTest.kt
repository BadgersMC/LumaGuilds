package net.lumalyte.lg.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class GuildQuestProgressTest {
    @Test
    fun `increment saturates instead of overflowing`() {
        val progress = GuildQuestProgress("week", "quest", UUID.randomUUID(), Long.MAX_VALUE)

        assertEquals(Long.MAX_VALUE, progress.withIncrementedCount(1, Long.MAX_VALUE).currentCount)
    }
}
