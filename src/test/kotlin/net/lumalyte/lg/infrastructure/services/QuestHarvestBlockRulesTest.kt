package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.CaveVinesPlant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestHarvestBlockRulesTest {
    @Test
    fun `cave vine tips and body segments count without requiring maturity or berries`() {
        val immatureTip = mockk<Ageable> {
            every { age } returns 0
            every { maximumAge } returns 25
        }
        val body = mockk<BlockData>()
        val expected = QuestBlockBreak(QuestAction.HARVEST_CROPS, Material.CAVE_VINES)

        assertEquals(expected, QuestHarvestBlockRules.classify(Material.CAVE_VINES, immatureTip))
        assertEquals(expected, QuestHarvestBlockRules.classify(Material.CAVE_VINES_PLANT, body))
    }

    @Test
    fun `kelp and nether vine bodies use their growing-tip harvest quest targets`() {
        val tipsAndBodies = listOf(
            Material.KELP to Material.KELP_PLANT,
            Material.WEEPING_VINES to Material.WEEPING_VINES_PLANT,
            Material.TWISTING_VINES to Material.TWISTING_VINES_PLANT,
        )
        val blockData = mockk<BlockData>()
        for ((tip, body) in tipsAndBodies) {
            val expected = QuestBlockBreak(QuestAction.HARVEST_CROPS, tip)
            assertEquals(expected, QuestHarvestBlockRules.classify(tip, blockData))
            assertEquals(expected, QuestHarvestBlockRules.classify(body, blockData))
            assertTrue(QuestHarvestBlockRules.isVineSegment(body))
            assertFalse(QuestHarvestBlockRules.isVineSegment(tip))
        }
    }

    @Test
    fun `right click glow berry harvest requires the berry state on tips or body`() {
        val withBerries = mockk<CaveVinesPlant> {
            every { hasBerries() } returns true
        }
        val withoutBerries = mockk<CaveVinesPlant> {
            every { hasBerries() } returns false
        }
        assertTrue(QuestHarvestBlockRules.canHarvestGlowBerries(Material.CAVE_VINES, withBerries))
        assertTrue(QuestHarvestBlockRules.canHarvestGlowBerries(Material.CAVE_VINES_PLANT, withBerries))
        assertFalse(QuestHarvestBlockRules.canHarvestGlowBerries(Material.CAVE_VINES, withoutBerries))
        assertFalse(QuestHarvestBlockRules.canHarvestGlowBerries(Material.CAVE_VINES_PLANT, withoutBerries))
        assertFalse(QuestHarvestBlockRules.canHarvestGlowBerries(Material.KELP, withBerries))
        assertFalse(QuestHarvestBlockRules.canHarvestGlowBerries(Material.STONE, withBerries))
    }

    @Test
    fun `four growing plant families have the correct cascade direction`() {
        assertEquals(-1, QuestHarvestBlockRules.cascadeStep(Material.CAVE_VINES_PLANT))
        assertEquals(-1, QuestHarvestBlockRules.cascadeStep(Material.WEEPING_VINES))
        assertEquals(1, QuestHarvestBlockRules.cascadeStep(Material.KELP))
        assertEquals(1, QuestHarvestBlockRules.cascadeStep(Material.TWISTING_VINES_PLANT))
        assertEquals(null, QuestHarvestBlockRules.cascadeStep(Material.WHEAT))
    }

    @Test
    fun `ordinary crops still require maturity`() {
        val immature = mockk<Ageable> {
            every { age } returns 2
            every { maximumAge } returns 7
        }
        val mature = mockk<Ageable> {
            every { age } returns 7
            every { maximumAge } returns 7
        }

        assertEquals(
            QuestBlockBreak(QuestAction.MINE_BLOCKS, Material.WHEAT),
            QuestHarvestBlockRules.classify(Material.WHEAT, immature),
        )
        assertEquals(
            QuestBlockBreak(QuestAction.HARVEST_CROPS, Material.WHEAT),
            QuestHarvestBlockRules.classify(Material.WHEAT, mature),
        )
    }

    @Test
    fun `ordinary blocks stay mining targets`() {
        assertEquals(
            QuestBlockBreak(QuestAction.MINE_BLOCKS, Material.STONE),
            QuestHarvestBlockRules.classify(Material.STONE, mockk<BlockData>()),
        )
    }
}
