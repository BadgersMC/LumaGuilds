package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestCandidate
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class QuestGeneratorTest {
    @Test
    fun `same seed generates same shared set without duplicates`() {
        val candidates = listOf(
            candidate("zombies", "ZOMBIE", QuestAction.KILL_MOBS, 100),
            candidate("wheat", "WHEAT", QuestAction.HARVEST_CROPS, 1_000),
            candidate("stone", "STONE", QuestAction.MINE_BLOCKS, 2_000)
        )

        val first = QuestGenerator(QuestGenerationValidator(), Random(42)).generate(candidates, 3, candidates.first().definition)
        val second = QuestGenerator(QuestGenerationValidator(), Random(42)).generate(candidates, 3, candidates.first().definition)

        assertEquals(first, second)
        assertEquals(3, first.map { it.id }.toSet().size)
    }

    @Test
    fun `bounded retries use deterministic fallback instead of looping`() {
        val invalid = candidate("bad", "SHEEP", QuestAction.CRAFT_ITEMS, 1_000)
        val fallback = candidate("fallback", "STONE", QuestAction.MINE_BLOCKS, 500).definition

        val result = QuestGenerator(QuestGenerationValidator(), Random(7), maxAttemptsPerQuest = 2)
            .generate(listOf(invalid), 1, fallback)

        assertEquals(listOf(fallback), result)
        assertNotEquals(invalid.definition, result.single())
    }

    private fun candidate(id: String, targetId: String, action: QuestAction, amount: Long): QuestCandidate {
        val target = QuestTarget(
            id = targetId,
            allowedActions = if (id == "bad") setOf(QuestAction.KILL_MOBS) else setOf(action),
            minimumAmount = if (id == "bad") 1 else amount,
            maximumAmount = if (id == "bad") 10 else amount
        )
        return QuestCandidate(
            definition = QuestDefinition(
                id = id,
                nameKey = "quests.$id.name",
                descriptionKey = "quests.$id.description",
                action = action,
                target = target,
                targetCount = amount,
                tier = QuestRewardTier.COMMON
            ),
            weight = 1
        )
    }
}
