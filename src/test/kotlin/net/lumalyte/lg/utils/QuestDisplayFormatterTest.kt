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
        assertEquals("Mine 4,000 Stone within 100 blocks of X=0", QuestDisplayFormatter.description(quest))
    }

    @Test
    fun `custom provider target hides namespace and kind in display text`() {
        assertEquals("Ancient Ore", QuestDisplayFormatter.target("nexo:block/ancient_ore"))
    }

    @Test
    fun `any fish quest reads as a player objective`() {
        val quest = quest(QuestAction.FISH, "minecraft:item/any", 100)
        assertEquals("Catch Any Fish", QuestDisplayFormatter.name(quest))
        assertEquals("Catch 100 fish", QuestDisplayFormatter.description(quest))
    }

    @Test
    fun `guild bank quest names the gold ore currency`() {
        val quest = quest(QuestAction.DEPOSIT_BANK, "lumaguilds:bank/coins", 30_000)
        assertEquals("Deposit Gold Ore", QuestDisplayFormatter.name(quest))
        assertEquals("Deposit 30,000 Gold Ore to the Guild Bank", QuestDisplayFormatter.description(quest))
    }

    @Test
    fun `item objectives pluralize naturally`() {
        val quest = quest(QuestAction.ENCHANT_ITEMS, "nexo:item/stone_spear", 90)
        assertEquals("Enchant Stone Spear", QuestDisplayFormatter.name(quest))
        assertEquals("Enchant 90 Stone Spears", QuestDisplayFormatter.description(quest))
    }

    private fun quest(action: QuestAction, id: String, count: Long) = QuestDefinition(
        id = "test-${action.name.lowercase()}",
        action = action,
        target = QuestTarget(
            id = id,
            allowedActions = setOf(action),
            minimumAmount = 1,
            maximumAmount = 100_000
        ),
        targetCount = count,
        tier = QuestRewardTier.CHALLENGING
    )
}