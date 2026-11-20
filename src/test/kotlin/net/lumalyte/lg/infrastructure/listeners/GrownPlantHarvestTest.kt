package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-177: growth-timer plants count when broken, with the column above them. */
class GrownPlantHarvestTest {
    private fun column(vararg types: Material): Block {
        val blocks = types.map { type -> mockk<Block>().also { every { it.type } returns type } }
        blocks.forEachIndexed { i, block ->
            every { block.getRelative(BlockFace.UP) } returns (blocks.getOrNull(i + 1)
                ?: mockk<Block>().also { every { it.type } returns Material.AIR })
        }
        return blocks.first()
    }

    @Test
    fun `cactus counts the broken block and everything above it`() {
        val harvest = GrownPlantHarvest.of(column(Material.CACTUS, Material.CACTUS, Material.CACTUS))!!
        assertEquals("minecraft:block/cactus", harvest.target)
        assertEquals(3, harvest.blocks.size)
    }

    @Test
    fun `kelp plant segments count as kelp`() {
        val harvest = GrownPlantHarvest.of(column(Material.KELP_PLANT, Material.KELP_PLANT, Material.KELP))!!
        assertEquals("minecraft:block/kelp", harvest.target)
        assertEquals(3, harvest.blocks.size)
    }

    @Test
    fun `sugar cane stops at a different block`() {
        val harvest = GrownPlantHarvest.of(column(Material.SUGAR_CANE, Material.SUGAR_CANE, Material.STONE))!!
        assertEquals(2, harvest.blocks.size)
    }

    @Test
    fun `other blocks are not grown plants`() {
        assertNull(GrownPlantHarvest.of(column(Material.WHEAT)))
    }
}
