package net.lumalyte.lg.config

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** REQ-257: the widened pools only name things the server knows, or config loading would fail. */
class ChapterTwoTargetPoolsTest {
    @Test
    fun `every default target is a real material or entity`() {
        ChapterTwoTargetPools.defaultMaterials().values.flatten().forEach { name ->
            assertTrue(runCatching { Material.valueOf(name) }.isSuccess, "Unknown material $name")
        }
        ChapterTwoTargetPools.defaultEntities().values.flatten().forEach { name ->
            assertTrue(runCatching { EntityType.valueOf(name) }.isSuccess, "Unknown entity $name")
        }
    }

    @Test
    fun `everyday mobs and blocks players expect are counted`() {
        val mobs = ChapterTwoTargetPools.defaultEntities().getValue("normal_mobs")
        assertTrue(mobs.containsAll(listOf("DROWNED", "HUSK", "WITCH", "BLAZE", "SLIME", "PILLAGER")))
        assertTrue("ZOMBIFIED_PIGLIN" !in mobs && "ENDER_DRAGON" !in mobs && "WARDEN" !in mobs)
        val blocks = ChapterTwoTargetPools.defaultMaterials().getValue("common_break")
        assertTrue(blocks.containsAll(listOf("OAK_LOG", "TUFF", "ANDESITE", "END_STONE", "BLACKSTONE")))
    }
}
