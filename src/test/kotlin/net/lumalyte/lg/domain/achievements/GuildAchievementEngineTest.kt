package net.lumalyte.lg.domain.achievements

import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildAchievementEngineTest {
    private fun node(id: String, type: String = "SALE", target: String? = null, goal: Int = 3) = GuildAchievement(
        id, null, id, listOf(id), type, target, goal, 500, emptyList(), "Commerce", "GOLD_INGOT", "TASK", 0f, 0f)

    @Test fun `replayed sale cannot advance or reward again`() {
        val engine = GuildAchievementEngine(listOf(node("sales")))
        var record = AchievementRecord()
        for (i in 1..3) record = engine.advance(record, "SALE", receipt = "sale-$i").record
        assertEquals(3, record.progress["sales"])
        assertEquals(setOf("sales"), record.rewarded)
        assertEquals(AchievementChange(record, emptyList()), engine.advance(record, "SALE", receipt = "sale-3"))
        assertTrue(engine.advance(record, "SALE", receipt = "sale-4").completed.isEmpty())
        assertEquals(3, engine.advance(record, "SALE", receipt = "sale-4").record.receipts.size)
    }
    @Test fun `absolute playtime observations do not accumulate on repeated polling`() {
        val engine = GuildAchievementEngine(listOf(node("hours", "HOURS", goal = 1000)))
        val first = engine.advance(AchievementRecord(), "HOURS", amount = 100, absolute = true).record
        val again = engine.advance(first, "HOURS", amount = 100, absolute = true).record
        assertEquals(100, again.progress["hours"])
        assertEquals(100, engine.advance(again, "HOURS", amount = 50, absolute = true).record.progress["hours"])
    }
    @Test fun `level jumps cross all applicable thresholds but ranks require positive qualifying place`() {
        val engine = GuildAchievementEngine(listOf(node("ten", "GUILD_LEVEL_UP", "10", 1),
            node("max", "GUILD_LEVEL_UP", "100", 1), node("elite", "GUILD_LEADERBOARD_RANK", "3", 1)))
        assertEquals(setOf("ten", "max"), engine.advance(AchievementRecord(), "GUILD_LEVEL_UP", "100").completed.map { it.id }.toSet())
        assertTrue(engine.advance(AchievementRecord(), "GUILD_LEADERBOARD_RANK", "0").completed.isEmpty())
        assertEquals(1, engine.advance(AchievementRecord(), "GUILD_LEADERBOARD_RANK", "2").completed.size)
    }
    @Test fun `boss target and large counts are bounded without overflowing`() {
        val engine = GuildAchievementEngine(listOf(node("dragon", "BOSS", "ENDER_DRAGON", 1), node("blocks", "BLOCKS", goal = 100000)))
        assertTrue(engine.advance(AchievementRecord(), "BOSS", "WITHER").completed.isEmpty())
        assertEquals(100000, engine.advance(AchievementRecord(), "BLOCKS", amount = Int.MAX_VALUE).record.progress["blocks"])
        assertFailsWith<IllegalArgumentException> { engine.advance(AchievementRecord(), "BLOCKS", amount = -1) }
    }
    @Test fun `known unpaid completion recovers once without incrementing other counters`() {
        val engine = GuildAchievementEngine(listOf(node("sales")))
        val unpaid = AchievementRecord(progress = mapOf("sales" to 3))
        val recovered = engine.pending(unpaid)
        assertEquals(listOf("sales"), recovered.completed.map { it.id })
        assertEquals(unpaid.progress, recovered.record.progress)
        assertTrue(engine.pending(recovered.record).completed.isEmpty())
    }
}
