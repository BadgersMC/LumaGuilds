package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertEquals

class ChatUtilsTest {

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `component broadcast preserves interaction events`() {
        val server = MockBukkit.mock()
        val player = server.addPlayer()
        val message = Component.text("Open help")
            .clickEvent(ClickEvent.runCommand("/g help"))

        ChatUtils.broadcastMessage(message)

        val received = requireNotNull(player.nextComponentMessage())
        assertEquals(ClickEvent.runCommand("/g help"), received.clickEvent())
    }
}
