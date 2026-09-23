package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuestDisplayFormatterTest {
    @Test
    fun `namespaced targets and axis conditions render as player text`() {
        val quest = QuestDefinition(
            id = "axis-stone",
            action = QuestAction.MINE_BLOCKS,
            target = QuestTarget(
                id = "minecraft:block/stone",
                allowedActions = setOf(QuestAction.MINE_BLOCKS),
                minimumAmount = 1_000,
                maximumAmount = 25_000,
                supportedConditions = setOf(QuestConditionType.X_WITHIN)
            ),
            targetCount = 4_000,
            tier = QuestRewardTier.CONDITIONED,
            conditions = listOf(QuestCondition(QuestConditionType.X_WITHIN, "0:100"))
        )

        assertEquals("Mine Stone", QuestDisplayFormatter.name(quest))
        assertEquals(
            "4000 Stone within 100 blocks of X=0",
            QuestDisplayFormatter.description(quest)
        )
    }

    @Test
    fun `custom provider target hides namespace and kind in display text`() {
        assertEquals("Ancient Ore", QuestDisplayFormatter.target("nexo:block/ancient_ore"))
    }
}
