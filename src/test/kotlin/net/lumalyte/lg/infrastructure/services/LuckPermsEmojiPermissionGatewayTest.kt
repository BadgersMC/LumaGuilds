package net.lumalyte.lg.infrastructure.services

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuckPermsEmojiPermissionGatewayTest {
    private lateinit var server: ServerMock
    private val commands = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        server.commandMap.register("lp", object : Command("lp") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                commands += "$commandLabel ${args.joinToString(" ")}"
                return true
            }
        })
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `valid grant and revoke dispatch exact LuckPerms commands`() {
        val playerId = UUID.randomUUID()
        val gateway = LuckPermsEmojiPermissionGateway(MockBukkit.createMockPlugin())

        assertTrue(gateway.grant(playerId, "enthusia.emoji.badger"))
        assertTrue(gateway.revoke(playerId, "enthusia.emoji.badger"))

        assertEquals(
            listOf(
                "lp user $playerId permission set enthusia.emoji.badger true",
                "lp user $playerId permission unset enthusia.emoji.badger",
            ),
            commands,
        )
    }

    @Test
    fun `invalid permission dispatches nothing`() {
        val gateway = LuckPermsEmojiPermissionGateway(MockBukkit.createMockPlugin())

        assertFalse(gateway.grant(UUID.randomUUID(), "permission true\nlp user attacker permission set * true"))

        assertEquals(emptyList(), commands)
    }
}
