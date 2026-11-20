package net.lumalyte.lg.interaction.listeners

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * REQ-222: Paper fires the legacy AsyncPlayerChatEvent before AsyncChatEvent, and RoseChat and
 * InteractiveChat broadcast from the legacy event. Party chat must be cancelled there first.
 */
internal class PartyChatLegacyEventContractTest {
    @Test
    fun legacyChatEventIsCancelledForPartyChat() {
        val main = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        assertTrue(main.contains("org.bukkit.event.player.AsyncPlayerChatEvent::class.java"))
        assertTrue(main.contains("partyChatListener.onLegacyChat(event as org.bukkit.event.player.AsyncPlayerChatEvent)"))
        val listener = File("src/main/kotlin/net/lumalyte/lg/interaction/listeners/PartyChatListener.kt").readText()
        val body = listener.substringAfter("fun onLegacyChat(").substringBefore("\n    fun ")
        assertTrue(body.contains("event.isCancelled = true") && body.contains("event.recipients.clear()"))
    }
}
