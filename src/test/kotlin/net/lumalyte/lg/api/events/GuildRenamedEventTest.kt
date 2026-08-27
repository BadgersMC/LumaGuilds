package net.lumalyte.lg.api.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.UUID

class GuildRenamedEventTest {
    @Test
    fun `exposes guild id and old and new names`() {
        val guildId = UUID.randomUUID()
        val event = GuildRenamedEvent(guildId, "Old", "New")

        assertEquals(guildId, event.guildId)
        assertEquals("Old", event.oldName)
        assertEquals("New", event.newName)
        assertSame(event.handlers, GuildRenamedEvent.getHandlerList())
    }
}
