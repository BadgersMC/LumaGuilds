package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** REQ-222: hidden party chat hides other people's messages, never the sender's own. */
internal class PartyRecipientsTest {
    @Test
    fun senderAlwaysSeesTheirOwnMessage() {
        val sender = UUID.randomUUID()
        val shown = UUID.randomUUID()
        val hidden = UUID.randomUUID()
        val visible = setOf(shown)
        assertEquals(setOf(sender, shown), PartyRecipients.of(setOf(sender, shown, hidden), sender) { it in visible })
    }
}
