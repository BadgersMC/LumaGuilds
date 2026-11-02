package net.lumalyte.lg.acceptance

import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.Test

class QuestFixturePlanTest {
    private fun week(): WeeklyQuestSet {
        val target = QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100)
        val quests = (1..6).map { QuestDefinition("original-$it", action = QuestAction.MINE_BLOCKS,
            target = target, targetCount = 50, tier = QuestRewardTier.COMMON, experienceReward = 10) }
        return WeeklyQuestSet("week-original", Instant.parse("2026-10-26T00:00:00Z"),
            Instant.parse("2026-11-02T00:00:00Z"), quests)
    }

    @Test fun `restore preserves original definitions exactly`() {
        val original = week()
        val fixture = QuestFixturePlan.install(original)
        assertEquals(original.quests, fixture.quests.take(6))
        assertEquals(5_000, fixture.quests.last().targetCount)
        assertEquals(500, fixture.quests.last().experienceReward)
        assertEquals(original, QuestFixturePlan.restore(fixture, original.weekId, QuestFixturePlan.fingerprint(original)))
    }

    @Test fun `reject duplicate fixture`() {
        assertFailsWith<IllegalArgumentException> { QuestFixturePlan.install(QuestFixturePlan.install(week())) }
    }

    @Test fun `reject week rollover during cleanup`() {
        val original = week()
        assertFailsWith<IllegalArgumentException> {
            QuestFixturePlan.restore(QuestFixturePlan.install(original).copy(weekId = "new-week"),
                original.weekId, QuestFixturePlan.fingerprint(original))
        }
    }

    @Test fun `reject unrelated definition changes rather than overwrite them`() {
        val original = week()
        val installed = QuestFixturePlan.install(original)
        val drifted = installed.copy(quests = installed.quests.map {
            if (it.id == "original-1") it.copy(experienceReward = 999) else it
        })
        assertFailsWith<IllegalArgumentException> {
            QuestFixturePlan.restore(drifted, original.weekId, QuestFixturePlan.fingerprint(original))
        }
    }
}

