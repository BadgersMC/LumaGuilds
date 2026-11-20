package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

/** REQ-223: party chat reads "[Party] [Guild name] Player » message", with no PlaceholderAPI or emoji. */
internal class PartyChatLineContractTest {
    private val source = File("src/main/kotlin/net/lumalyte/lg/infrastructure/services/ChatServiceBukkit.kt").readText()
    private val body = source.substringAfter("private fun formatPartyMessage(").substringBefore("\n    private fun ")

    @Test
    fun builtFromTheLangLineWithoutPlaceholderApi() {
        assertTrue(body.contains("\"notification.chat.party.line\""))
        assertFalse(body.contains("PlaceholderAPI"))
        assertFalse(body.contains("emoji", ignoreCase = true))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun langLineUsesPartyGuildPlayerMessage() {
        val root = Yaml().load<Map<String, Any>>(File("src/main/resources/lang/en_US.yml").readText())
        val party = ((root["notification"] as Map<String, Any>)["chat"] as Map<String, Any>)["party"] as Map<String, Any>
        val line = party["line"] as String
        listOf("[<aqua>Party</aqua>]", "<guild>", "<player>", "<message>").forEach { assertTrue(line.contains(it), it) }
        assertFalse(line.contains("%"))
    }
}
