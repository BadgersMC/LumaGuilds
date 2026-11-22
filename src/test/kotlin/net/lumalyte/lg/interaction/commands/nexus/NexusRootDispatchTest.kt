package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NexusRootDispatchTest {
    private val guild = mockk<NexusQuickGuildChatCommand>(relaxUnitFun = true)
    private val ally = mockk<NexusQuickAllyChatCommand>(relaxUnitFun = true)
    private val mod = mockk<NexusQuickModChatCommand>(relaxUnitFun = true)
    private val announce = mockk<NexusQuickAnnounceCommand>(relaxUnitFun = true)
    private val fullscreen = mockk<NexusFullscreenAnnounceCommand>(relaxUnitFun = true)
    private val claims = mockk<NexusClaimListCommand>(relaxUnitFun = true)
    private val chatRoots = listOf("gc", "gac", "gmc", "ga", "gfa")
    private val dispatcher by lazy {
        commandDispatcher((chatRoots + "claimlist").toSet(), mapOf(
            NexusQuickGuildChatCommand::class to guild, NexusQuickAllyChatCommand::class to ally,
            NexusQuickModChatCommand::class to mod, NexusQuickAnnounceCommand::class to announce,
            NexusFullscreenAnnounceCommand::class to fullscreen, NexusClaimListCommand::class to claims,
        )).dispatcher
    }

    private fun source(sender: CommandSender) = mockk<CommandSourceStack>().also { every { it.sender } returns sender }

    @Test
    fun `default routes execute with absent typed and greedy arguments`() {
        val player = mockk<Player>(relaxed = true)
        every { player.hasPermission(any<String>()) } returns true
        val source = source(player)
        for (message in listOf(null, "hello guild")) {
            chatRoots.forEach { root ->
                val line = if (message == null) root else "$root $message"
                assertEquals(1, dispatcher.execute(line, source), line)
            }
            verify { guild.execute(player, message) }
            verify { ally.execute(player, message) }
            verify { mod.execute(player, message) }
            verify { announce.announce(player, message) }
            verify { fullscreen.announce(player, message) }
        }
        assertEquals(1, dispatcher.execute("claimlist", source))
        assertEquals(1, dispatcher.execute("claimlist 2", source))
        verify { claims.list(player, null) }
        verify { claims.list(player, 2) }
        assertThrows<CommandSyntaxException> { dispatcher.execute("claimlist two", source) }
    }

    @Test
    fun `console and players without permission cannot execute or see root arguments`() {
        val console = mockk<CommandSender>(relaxed = true)
        every { console.hasPermission(any<String>()) } returns true
        val player = mockk<Player>(relaxed = true)
        for (source in listOf(source(console), source(player))) {
            for (root in chatRoots + "claimlist") {
                assertEquals(0, dispatcher.execute(root, source))
                assertFalse(dispatcher.root.getChild(root).children.single().canUse(source))
                assertThrows<CommandSyntaxException> { dispatcher.execute("$root 2", source) }
            }
        }
    }
}
