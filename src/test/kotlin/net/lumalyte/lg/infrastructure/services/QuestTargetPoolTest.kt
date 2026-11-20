package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.entities.QuestTargetRarity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-176: weekly quests only offer targets a guild can reasonably work on. */
class QuestTargetPoolTest {
    @Test
    fun `kill quests cover hostile mobs and farm animals but never villagers pets or props`() {
        listOf("ZOMBIE", "SKELETON", "CREEPER", "BLAZE", "COW", "SHEEP", "CHICKEN", "PIG").forEach {
            assertEquals(QuestTargetRarity.COMMON, QuestTargetPool.mobRarity(it), it)
        }
        listOf("VILLAGER", "WANDERING_TRADER", "IRON_GOLEM", "CAT", "WOLF", "HORSE", "ARMOR_STAND",
            "MANNEQUIN", "GIANT", "ILLUSIONER", "ZOMBIE_HORSE", "ALLAY", "SNIFFER", "PLAYER").forEach {
            assertNull(QuestTargetPool.mobRarity(it), it)
        }
        assertEquals(QuestTargetRarity.RARE, QuestTargetPool.mobRarity("PHANTOM"))
        assertEquals(QuestTargetRarity.RARE, QuestTargetPool.mobRarity("BREEZE"))
        assertEquals(QuestTargetRarity.PRECIOUS, QuestTargetPool.mobRarity("WITHER"))
    }

    @Test
    fun `harvest quests are farm crops only`() {
        listOf("WHEAT", "CARROTS", "POTATOES", "NETHER_WART", "SUGAR_CANE", "CACTUS", "KELP", "BAMBOO").forEach {
            assertTrue(QuestTargetPool.isHarvestTarget(it), it)
        }
        listOf("FROSTED_ICE", "MELON_STEM", "PUMPKIN_STEM", "TWISTING_VINES", "CHORUS_FLOWER", "KELP_PLANT").forEach {
            assertFalse(QuestTargetPool.isHarvestTarget(it), it)
        }
    }

    @Test
    fun `place quests are full building blocks, not decorative variants`() {
        listOf("OAK_PLANKS", "OAK_LOG", "STRIPPED_OAK_LOG", "STONE_BRICKS", "WHITE_CONCRETE", "RED_WOOL",
            "GLASS", "LIME_STAINED_GLASS", "COBBLESTONE", "TERRACOTTA", "BLUE_TERRACOTTA", "DEEPSLATE_BRICKS").forEach {
            assertTrue(QuestTargetPool.isPlaceTarget(it), it)
        }
        listOf("OAK_STAIRS", "STONE_SLAB", "COBBLESTONE_WALL", "GLASS_PANE", "WAXED_OXIDIZED_CUT_COPPER",
            "OAK_FENCE", "TORCH", "LIME_STAINED_GLASS_PANE", "CHEST", "FLOWER_POT", "INFESTED_STONE_BRICKS",
            "CRACKED_STONE_BRICKS", "WHITE_CONCRETE_POWDER").forEach {
            assertFalse(QuestTargetPool.isPlaceTarget(it), it)
        }
    }

    @Test
    fun `mining quests are world-generated blocks, never wall-mounted or placed-only ones`() {
        listOf("STONE", "DEEPSLATE", "COAL_ORE", "DEEPSLATE_IRON_ORE", "ANCIENT_DEBRIS", "OAK_LOG", "CRIMSON_STEM",
            "NETHERRACK", "END_STONE", "SAND", "GRAVEL", "OBSIDIAN").forEach {
            assertTrue(QuestTargetPool.isMineTarget(it), it)
        }
        listOf("CANDLE_CAKE", "CRIMSON_WALL_HANGING_SIGN", "DEAD_FIRE_CORAL_WALL_FAN", "WARPED_ROOTS",
            "POTTED_CACTUS", "WALL_TORCH", "SPAWNER", "BUDDING_AMETHYST").forEach {
            assertFalse(QuestTargetPool.isMineTarget(it), it)
        }
    }

    @Test
    fun `smelting quests are common smelted goods`() {
        listOf("IRON_INGOT", "GOLD_INGOT", "GLASS", "SMOOTH_STONE", "COOKED_BEEF", "IRON_NUGGET").forEach {
            assertTrue(QuestTargetPool.isSmeltTarget(it), it)
        }
        listOf("WHITE_GLAZED_TERRACOTTA", "LIME_DYE", "POPPED_CHORUS_FRUIT").forEach {
            assertFalse(QuestTargetPool.isSmeltTarget(it), it)
        }
    }

    @Test
    fun `crafting quests skip waxed and weathered copper`() {
        assertTrue(QuestTargetPool.isCraftTarget("CHEST"))
        listOf("WAXED_OXIDIZED_COPPER_BULB", "EXPOSED_CUT_COPPER", "WAXED_COPPER_BLOCK").forEach {
            assertFalse(QuestTargetPool.isCraftTarget(it), it)
        }
    }
}
