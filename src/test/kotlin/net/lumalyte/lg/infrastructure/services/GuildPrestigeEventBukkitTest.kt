package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.events.GuildPrestigeEvent
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** REQ-153: the public event carries the completion exactly. */
class GuildPrestigeEventBukkitTest {
    @Test
    fun `publishes every completion field on the event`() {
        val completion = PrestigeCompletion(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 6, "travel-50",
            Instant.ofEpochMilli(1_730_000_000_000L), true,
        )
        val fired = mutableListOf<GuildPrestigeEvent>()
        GuildPrestigeEventBukkit { fired += it }.prestiged(completion)
        val event = fired.single()
        assertEquals(completion.guildId, event.guildId)
        assertEquals(completion.actorId, event.actorId)
        assertEquals(6, event.tier)
        assertEquals("travel-50", event.retainedRewardId)
        assertEquals(completion.appliedAt, event.appliedAt)
        assertEquals(true, event.firstToReachTier)
        assertSame(GuildPrestigeEvent.getHandlerList(), event.handlers)
    }
}
