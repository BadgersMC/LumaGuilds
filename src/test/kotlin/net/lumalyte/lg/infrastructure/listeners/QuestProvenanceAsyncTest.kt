package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.values.BlockPosition
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.block.BlockPistonExtendEvent
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.assertEquals

class QuestProvenanceAsyncTest {
    @Test
    fun `piston captures immutable coordinates and does not call database on event thread`() {
        val workers = mutableListOf<Runnable>()
        val queue = BlockProvenanceOperationQueue(Executor { workers.add(it) })
        val repository = mockk<BlockProvenanceRepository>(relaxed = true)
        val listener = QuestProgressListener(mockk<QuestService>(), mockk<MemberService>(), repository, queue)
        val world = mockk<World>()
        val worldId = UUID.randomUUID()
        every { world.uid } returns worldId
        val source = mockk<Block>()
        val destination = mockk<Block>()
        every { source.world } returns world
        every { source.x } returns 10
        every { source.y } returns 64
        every { source.z } returns 10
        every { source.getRelative(BlockFace.EAST) } returns destination
        every { destination.world } returns world
        every { destination.x } returns 11
        every { destination.y } returns 64
        every { destination.z } returns 10
        val event = mockk<BlockPistonExtendEvent>()
        every { event.blocks } returns listOf(source)
        every { event.direction } returns BlockFace.EAST
        listener.onPistonExtend(event)
        verify(exactly = 0) { repository.moveAll(any()) }
        assertEquals(1, workers.size)
        // A later world change must not alter the queued operation.
        every { source.x } returns 999
        workers.single().run()
        verify(exactly = 1) {
            repository.moveAll(listOf(BlockPosition(worldId, 10, 64, 10) to BlockPosition(worldId, 11, 64, 10)))
        }
    }
}
