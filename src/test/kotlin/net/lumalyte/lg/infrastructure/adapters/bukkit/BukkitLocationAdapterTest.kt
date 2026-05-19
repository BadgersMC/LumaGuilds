package net.lumalyte.lg.infrastructure.adapters.bukkit

import org.bukkit.Location
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class BukkitLocationAdapterTest {

    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("test")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `toPosition3D floors positive coordinates`() {
        val pos = Location(world, 100.7, 64.0, 200.3).toPosition3D()
        assertEquals(100, pos.x)
        assertEquals(64, pos.y)
        assertEquals(200, pos.z)
    }

    @Test
    fun `toPosition3D floors negative coordinates instead of truncating`() {
        // toInt() would give (-100, -201) here; the block actually occupied is (-101, -202).
        val pos = Location(world, -100.5, 64.0, -201.5).toPosition3D()
        assertEquals(-101, pos.x)
        assertEquals(-202, pos.z)
    }

    @Test
    fun `toPosition3D round-trips a negative-coordinate block through toLocation`() {
        // Player stands on a block centered at (-100.5, -200.5): set-home then teleport
        // must land back inside the same block, not one block away.
        val standing = Location(world, -100.5, 70.0, -200.5)
        val restored = standing.toPosition3D().toLocation(world)
        assertEquals(-100.5, restored.x)
        assertEquals(-200.5, restored.z)
        assertEquals(70.0, restored.y)
    }

    @Test
    fun `toPosition2D floors negative coordinates instead of truncating`() {
        val pos = Location(world, -1.5, 0.0, -1.5).toPosition2D()
        assertEquals(-2, pos.x)
        assertEquals(-2, pos.z)
    }
}
