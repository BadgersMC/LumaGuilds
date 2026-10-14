package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BukkitCommandManager
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.InviteBlockService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class GuildInviteBlockRoutingTest {
    @BeforeEach fun setup() = stopKoin()

    /** Releases the test database or mocked server. */
    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** Verifies both roots route block unblock and blocked and enforce permission. */
    @Test
    fun blockCommandRouting() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer("Viewer")
        val target = server.addPlayer("Target")
        val blocks = mockk<InviteBlockService>(relaxed = true)
        val lang = mockk<LangService>(relaxed = true)
        every { blocks.setBlocked(any(), any(), any()) } returns true
        startKoin {
            modules(
                module {
                    single { blocks }
                    single { lang }
                },
            )
        }
        val manager = BukkitCommandManager(plugin)
        manager.registerCommand(GuildCommand())
        player.addAttachment(plugin, "lumaguilds.guild.block", true)
        for (root in listOf("g", "guild")) {
            server.dispatchCommand(player, "$root block Target")
            server.dispatchCommand(player, "$root unblock Target")
            server.dispatchCommand(player, "$root blocked")
        }
        verify(exactly = 2) { blocks.setBlocked(player.uniqueId, target.uniqueId, true) }
        verify(exactly = 2) { blocks.setBlocked(player.uniqueId, target.uniqueId, false) }
        verify(exactly = 2) { blocks.blockedBy(player.uniqueId) }
        clearMocks(blocks, answers = false)
        player.addAttachment(plugin, "lumaguilds.guild.block", false)
        server.dispatchCommand(player, "guild block Target")
        verify(exactly = 0) { blocks.setBlocked(any(), any(), any()) }
    }
}
