package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.values.BlockPosition
import org.bukkit.Material
import org.bukkit.event.block.BlockBreakEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class QuestProgressListenerProvenanceTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("quest-provenance")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `normal block break clears stale provenance even without guild membership`() {
        val player = server.addPlayer()
        val block = world.getBlockAt(7, 64, 9).apply { type = Material.STONE }
        val questService = mockk<QuestService>(relaxed = true)
        val memberService = mockk<MemberService>()
        val provenance = mockk<BlockProvenanceRepository>(relaxed = true)

        every { questService.activeQuestSet() } returns null
        every { memberService.getPlayerGuilds(player.uniqueId) } returns emptySet()

        QuestProgressListener(questService, memberService, provenance)
            .onBlockBreak(BlockBreakEvent(block, player))

        verify(exactly = 1) {
            provenance.remove(BlockPosition(world.uid, 7, 64, 9))
        }
    }
}
