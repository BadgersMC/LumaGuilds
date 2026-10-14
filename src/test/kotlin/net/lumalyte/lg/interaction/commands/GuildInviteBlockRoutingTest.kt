package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BukkitCommandManager
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

/** Real ACF routing and permission coverage for both guild command roots. */
internal class GuildInviteBlockRoutingTest {
    private val blocks = mockk<InviteBlockService>(relaxed = true)
    private val fixture by lazy { Fixture() }

    /** Resets application injection before creating the mocked command fixture. */
    @BeforeEach
    fun setup() = stopKoin()

    /** Releases singleton injection and Bukkit state after each test. */
    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** Checks block/unblock/list dispatch through both command roots. */
    @Test
    fun blockCommandRouting() {
        val player = fixture.player
        player.addAttachment(fixture.plugin, "lumaguilds.guild.block", true)
        for (root in listOf("g", "guild")) {
            fixture.server.dispatchCommand(player, "$root block Target")
            fixture.server.dispatchCommand(player, "$root unblock Target")
            fixture.server.dispatchCommand(player, "$root blocked")
        }
        verify(exactly = 2) { blocks.setBlocked(player.uniqueId, fixture.target.uniqueId, true) }
        verify(exactly = 2) { blocks.setBlocked(player.uniqueId, fixture.target.uniqueId, false) }
        verify(exactly = 2) { blocks.blockedBy(player.uniqueId) }
    }

    /** An explicit permission denial prevents block mutation. */
    @Test
    fun deniedCommandDoesNotMutate() {
        fixture.player.addAttachment(fixture.plugin, "lumaguilds.guild.block", false)
        fixture.server.dispatchCommand(fixture.player, "guild block Target")
        verify(exactly = 0) { blocks.setBlocked(any(), any(), any()) }
    }

    private inner class Fixture {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer("Viewer")
        val target = server.addPlayer("Target")

        init {
            every { blocks.setBlocked(any(), any(), any()) } returns true
            val lang = mockk<LangService>(relaxed = true)
            startKoin {
                modules(module {
                    single { blocks }
                    single { lang }
                })
            }
            BukkitCommandManager(plugin).registerCommand(GuildCommand())
        }
    }
}
