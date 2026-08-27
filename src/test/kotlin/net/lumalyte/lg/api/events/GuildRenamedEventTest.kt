package net.lumalyte.lg.api.events

import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildRenamedEventTest {
    @Test
    fun `exposes old and updated guild state with actor`() {
        val oldGuild = Guild(UUID.randomUUID(), "Old", createdAt = Instant.EPOCH)
        val updatedGuild = oldGuild.copy(name = "New")
        val actorId = UUID.randomUUID()

        val event = GuildRenamedEvent(oldGuild, updatedGuild, actorId)

        assertSame(oldGuild, event.oldGuild)
        assertSame(updatedGuild, event.guild)
        assertEquals(actorId, event.actorId)
        assertSame(event.handlers, GuildRenamedEvent.getHandlerList())
    }
}
