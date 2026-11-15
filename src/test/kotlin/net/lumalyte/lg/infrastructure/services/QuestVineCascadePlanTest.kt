package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.BlockPosition
import org.bukkit.Material
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestVineCascadePlanTest {
    @Test
    fun `breaking cave vines in the middle only tracks the disconnected lower stalk`() {
        val before = mapOf(
            104 to Material.STONE,
            103 to Material.CAVE_VINES_PLANT,
            102 to Material.CAVE_VINES_PLANT,
            101 to Material.CAVE_VINES_PLANT,
            100 to Material.CAVE_VINES,
            99 to Material.AIR,
        )
        val candidates = QuestVineCascadePlan.captureYs(
            Material.CAVE_VINES_PLANT, 102, 0, 320,
        ) { before[it] ?: Material.AIR }
        assertEquals(listOf(102, 101, 100), candidates)

        // The broken middle segment plus both descendants disappeared; the upper stalk remains.
        val after = before + mapOf(
            102 to Material.AIR,
            101 to Material.AIR,
            100 to Material.AIR,
        )
        assertEquals(
            3,
            QuestVineCascadePlan.removedPrefixEnd(Material.CAVE_VINES_PLANT, candidates, 0) {
                after[it] ?: Material.AIR
            },
        )
    }

    @Test
    fun `breaking kelp in the middle credits its upper stalk rather than the lower stalk`() {
        val before = mapOf(
            61 to Material.KELP_PLANT,
            62 to Material.KELP_PLANT,
            63 to Material.KELP_PLANT,
            64 to Material.KELP,
            65 to Material.WATER,
        )
        val candidates = QuestVineCascadePlan.captureYs(
            Material.KELP_PLANT, 62, 0, 320,
        ) { before[it] ?: Material.WATER }
        assertEquals(listOf(62, 63, 64), candidates)
        val after = before + mapOf(
            62 to Material.WATER,
            63 to Material.WATER,
            64 to Material.WATER,
        )
        assertEquals(
            3,
            QuestVineCascadePlan.removedPrefixEnd(Material.KELP_PLANT, candidates, 0) {
                after[it] ?: Material.WATER
            },
        )
    }

    @Test
    fun `canceled break and surviving segments earn no invented credit`() {
        val before = mapOf(
            80 to Material.WEEPING_VINES_PLANT,
            79 to Material.WEEPING_VINES_PLANT,
            78 to Material.WEEPING_VINES,
        )
        val candidates = QuestVineCascadePlan.captureYs(
            Material.WEEPING_VINES_PLANT, 80, 0, 320,
        ) { before[it] ?: Material.AIR }
        assertEquals(listOf(80, 79, 78), candidates)
        assertEquals(
            0,
            QuestVineCascadePlan.removedPrefixEnd(Material.WEEPING_VINES_PLANT, candidates, 0) {
                before[it] ?: Material.AIR
            },
        )
        val partiallyRemoved = before + (80 to Material.AIR)
        assertEquals(
            1,
            QuestVineCascadePlan.removedPrefixEnd(Material.WEEPING_VINES_PLANT, candidates, 0) {
                partiallyRemoved[it] ?: Material.AIR
            },
        )
    }

    @Test
    fun `changing body to tip is not mistaken for an actual removal`() {
        val candidates = listOf(70, 71)
        val state = mapOf(70 to Material.TWISTING_VINES, 71 to Material.AIR)
        assertEquals(
            0,
            QuestVineCascadePlan.removedPrefixEnd(Material.TWISTING_VINES_PLANT, candidates, 0) {
                state[it] ?: Material.AIR
            },
        )
    }

    @Test
    fun `a stalk collapsing across several scheduled ticks is credited incrementally`() {
        val candidates = listOf(62, 63, 64)
        val firstTick = mapOf(
            62 to Material.WATER,
            63 to Material.KELP_PLANT,
            64 to Material.KELP,
        )
        val secondTick = firstTick + (63 to Material.WATER)
        val thirdTick = secondTick + (64 to Material.WATER)

        val firstEnd = QuestVineCascadePlan.removedPrefixEnd(Material.KELP_PLANT, candidates, 0) {
            firstTick[it] ?: Material.WATER
        }
        assertEquals(1, firstEnd)

        val secondEnd = QuestVineCascadePlan.removedPrefixEnd(Material.KELP_PLANT, candidates, firstEnd) {
            secondTick[it] ?: Material.WATER
        }
        assertEquals(2, secondEnd)

        val thirdEnd = QuestVineCascadePlan.removedPrefixEnd(Material.KELP_PLANT, candidates, secondEnd) {
            thirdTick[it] ?: Material.WATER
        }
        assertEquals(3, thirdEnd)
    }

    @Test
    fun `candidate scan stops at world height and unrelated block family`() {
        val before = mapOf(
            319 to Material.KELP_PLANT,
            318 to Material.KELP,
        )
        assertEquals(
            listOf(318, 319),
            QuestVineCascadePlan.captureYs(Material.KELP, 318, 0, 320) {
                before[it] ?: Material.AIR
            },
        )
        assertTrue(
            QuestVineCascadePlan.captureYs(Material.STONE, 65, 0, 320) {
                Material.STONE
            }.isEmpty(),
        )
    }

    @Test
    fun `overlapping breaks cannot credit one fallen segment twice`() {
        val credits = RecentVineCredits()
        val position = BlockPosition(UUID.randomUUID(), 12, 85, 4)
        assertTrue(credits.record(position, 100L))
        assertFalse(credits.record(position, 100L))
        assertFalse(credits.record(position, 101L))
        assertTrue(credits.record(position, 103L))
    }
}
