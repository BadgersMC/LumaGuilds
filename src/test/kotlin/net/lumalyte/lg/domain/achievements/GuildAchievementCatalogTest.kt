package net.lumalyte.lg.domain.achievements

import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-224/225: long-term tiers exist, chain correctly and never overlap in the tree. */
internal class GuildAchievementCatalogTest {
    private val nodes = JSONObject(javaClass.classLoader.getResource("guild-achievements.json")!!.readText())
        .getJSONArray("nodes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun goals(requirement: String, target: String? = null) = nodes
        .filter { it.getString("requirement") == requirement && (target == null || it.optString("target") == target) }
        .map { it.getInt("goal") }.toSet()

    private fun targets(requirement: String) = nodes.filter { it.getString("requirement") == requirement }
        .map { it.getString("target").toInt() }.toSet()

    @Test fun `long-term tiers exist for each chain`() {
        assertTrue(targets("GUILD_LEVEL_UP").containsAll(setOf(10, 25, 50, 75, 100)))
        assertTrue(goals("GUILD_WAR_WON").containsAll(setOf(1, 5, 25)))
        assertTrue(goals("GUILD_WAR_KILL").containsAll(setOf(50, 250, 1000)))
        assertTrue(goals("KOTH_CAPTURE").containsAll(setOf(1, 10, 50)))
        assertTrue(goals("GUILD_COMBINED_PLAYTIME").containsAll(setOf(1000, 10000, 50000, 100000)))
        assertTrue(goals("GUILD_SHOP_SALE").contains(10000))
        assertTrue(goals("GUILD_ALLIANCE_FORMED").contains(5))
        assertTrue(goals("GUILD_COLLECTIVE_BOSS_KILL", "WITHER").contains(10))
        assertTrue(goals("GUILD_COLLECTIVE_BOSS_KILL", "ENDER_DRAGON").contains(5))
        listOf("SMELT", "BLOCK_BREAK", "CROP_HARVEST", "MOB_KILL").forEach {
            assertTrue(goals("GUILD_COLLECTIVE_$it").size >= 3, "GUILD_COLLECTIVE_$it needs two long-term tiers")
        }
        assertEquals(setOf(10, 25, 50), goals("GUILD_MEMBER_COUNT"))
        assertEquals(setOf(30, 180, 365), goals("GUILD_AGE_DAYS"))
    }

    @Test fun `guild XP rewards scale with difficulty and jokes pay nothing`() {
        val xp = nodes.associate { it.getString("id") to it.getInt("guildXp") }
        listOf("guilds_root", "friendly_fire", "the_exile", "coup_detat").forEach { assertEquals(0, xp[it], it) }
        nodes.filter { it.getString("id") !in setOf("guilds_root", "friendly_fire", "the_exile", "coup_detat") }
            .forEach { assertTrue(it.getInt("guildXp") > 0, "${it.getString("id")} needs a guild XP reward") }
        assertTrue(xp.getValue("timeless") > xp.getValue("ten_thousand_hours"))
        assertTrue(xp.getValue("ten_thousand_hours") > xp.getValue("founding"))
    }

    @Test fun `vanguard status ends the level chain`() {
        val byId = nodes.associateBy { it.getString("id") }
        val vanguard = byId.getValue("vanguard_status")
        assertEquals("75", byId.getValue(vanguard.getString("parent")).getString("target"))
    }

    @Test fun `parents are listed before their children for the advancement renderer`() {
        val seen = mutableSetOf<String>()
        nodes.forEach {
            val parent = it.optString("parent").takeUnless { p -> p.isBlank() || p == "null" }
            assertTrue(parent == null || parent in seen, "${it.getString("id")} is listed before its parent $parent")
            seen += it.getString("id")
        }
    }

    @Test fun `every tier chains from the previous tier and no frames overlap`() {
        val engine = GuildAchievementEngine(nodes.map {
            GuildAchievement(it.getString("id"), it.optString("parent").takeUnless { p -> p.isBlank() || p == "null" },
                it.getString("title"), listOf(""), it.getString("requirement"), null, it.getInt("goal"),
                it.getInt("personalXp"), emptyList(), it.getString("category"), it.getString("icon"),
                it.getString("frame"), it.getFloat("x"), it.getFloat("y"))
        })
        val positions = engine.definitions.map { it.x to it.y }
        assertEquals(positions.size, positions.distinct().size, "Advancement frames must not overlap")
        // REQ-256: a vanilla frame fills almost a whole grid cell, so frames closer than one cell overlap
        engine.definitions.forEachIndexed { index, a ->
            engine.definitions.drop(index + 1).forEach { b ->
                assertTrue(kotlin.math.abs(a.x - b.x) >= 1f || kotlin.math.abs(a.y - b.y) >= 1f,
                    "${a.id} (${a.x}, ${a.y}) overlaps ${b.id} (${b.x}, ${b.y}) in the advancement tree")
            }
        }
        engine.definitions.filter { it.parent != null }.forEach { child ->
            val parent = engine.definitions.first { it.id == child.parent }
            assertTrue(child.x > parent.x, "${child.id} must sit to the right of ${parent.id}")
        }
    }
}
