package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class QuestGeneratorTest {
    private val rewardXp: (QuestRewardTier) -> Int = { it.ordinal * 500 + 500 }

    @Test
    fun `same seed generates same shared set without duplicate content`() {
        val targets = listOf(
            target("minecraft:entity/zombie", QuestAction.KILL_MOBS, 25, 500),
            target("minecraft:block/wheat", QuestAction.HARVEST_CROPS, 250, 5_000),
            target("minecraft:block/stone", QuestAction.MINE_BLOCKS, 1_000, 25_000)
        )

        val settings = QuestGenerationSettings(conditionChancePercent = 0)
        val first = QuestGenerator(QuestGenerationValidator(), Random(42))
            .generate(targets, 3, settings, rewardXp = rewardXp)
        val second = QuestGenerator(QuestGenerationValidator(), Random(42))
            .generate(targets, 3, settings, rewardXp = rewardXp)

        assertEquals(first, second)
        assertEquals(3, first.map(QuestDefinition::fingerprint).toSet().size)
    }

    @Test
    fun `recent action target history forces a different target`() {
        val zombie = target("minecraft:entity/zombie", QuestAction.KILL_MOBS, 25, 500)
        val skeleton = target("minecraft:entity/skeleton", QuestAction.KILL_MOBS, 25, 500)
        val recent = QuestDefinition(
            id = "recent",
            action = QuestAction.KILL_MOBS,
            target = zombie,
            targetCount = 100,
            tier = QuestRewardTier.COMMON
        )

        val settings = QuestGenerationSettings(
            conditionChancePercent = 0,
            actionTargetCooldownWeeks = 3,
            exactRepeatCooldownWeeks = 8
        )
        val generated = QuestGenerator(QuestGenerationValidator(), ZeroRandom, maxAttemptsPerQuest = 1)
            .generate(
                listOf(zombie, skeleton),
                1,
                settings,
                QuestGenerationHistory(listOf(listOf(recent))),
                rewardXp
            )

        assertEquals("minecraft:entity/skeleton", generated.single().target.id)
    }

    @Test
    fun `axis conditions are generated as valid corridors`() {
        val stone = target(
            "minecraft:block/stone",
            QuestAction.MINE_BLOCKS,
            1_000,
            25_000,
            conditions = setOf(QuestConditionType.X_WITHIN)
        )

        val generated = QuestGenerator(QuestGenerationValidator(), Random(4))
            .generate(
                listOf(stone),
                1,
                QuestGenerationSettings(
                    conditionChancePercent = 100,
                    secondConditionChancePercent = 0,
                    axisCenters = listOf(0),
                    axisWidths = listOf(100)
                ),
                rewardXp = rewardXp
            )
            .single()

        assertEquals("0:100", generated.conditions.single().value)
        assertEquals(QuestConditionType.X_WITHIN, generated.conditions.single().type)
    }

    @Test
    fun `generated amounts are human milestones inside target bounds`() {
        val stone = target("minecraft:block/stone", QuestAction.MINE_BLOCKS, 1_000, 25_000)

        val amounts = (1..30).map { seed ->
            QuestGenerator(QuestGenerationValidator(), Random(seed))
                .generate(
                    listOf(stone),
                    1,
                    QuestGenerationSettings(conditionChancePercent = 0),
                    rewardXp = rewardXp
                )
                .single()
                .targetCount
        }

        assertTrue(amounts.all { it in 1_000L..25_000L })
        assertTrue(amounts.all { it % 100L == 0L || it % 500L == 0L || it % 1_000L == 0L })
        assertNotEquals(1, amounts.toSet().size)
    }

    @Test
    fun `amount policy keeps precious placement bounded`() {
        val bulk = QuestAmountPolicy.range(QuestAction.PLACE_BLOCKS, QuestTargetRarity.BULK)
        val precious = QuestAmountPolicy.range(QuestAction.PLACE_BLOCKS, QuestTargetRarity.PRECIOUS)

        assertTrue(precious.maximum < bulk.minimum)
        assertEquals(1, precious.minimum)
        assertEquals(32, precious.maximum)
    }

    @Test
    fun `fallback relaxes history when the provider pool is otherwise exhausted`() {
        val zombie = target("minecraft:entity/zombie", QuestAction.KILL_MOBS, 25, 500)
        val recent = QuestDefinition(
            id = "recent",
            action = QuestAction.KILL_MOBS,
            target = zombie,
            targetCount = 25,
            tier = QuestRewardTier.COMMON
        )

        val generated = QuestGenerator(QuestGenerationValidator(), ZeroRandom, maxAttemptsPerQuest = 1)
            .generate(
                listOf(zombie),
                1,
                QuestGenerationSettings(conditionChancePercent = 0),
                QuestGenerationHistory(listOf(listOf(recent))),
                rewardXp
            )

        assertEquals("minecraft:entity/zombie", generated.single().target.id)
    }

    @Test
    fun `dimension condition can be generated for a target that explicitly supports it`() {
        val player = QuestTarget(
            id = "minecraft:player/player",
            allowedActions = setOf(QuestAction.KILL_PLAYERS),
            minimumAmount = 10,
            maximumAmount = 100,
            naturalDimensions = setOf("NORMAL", "NETHER", "THE_END"),
            supportedConditions = setOf(QuestConditionType.IN_DIMENSION)
        )

        val generated = QuestGenerator(QuestGenerationValidator(), ZeroRandom)
            .generate(
                listOf(player),
                1,
                QuestGenerationSettings(conditionChancePercent = 100, secondConditionChancePercent = 0),
                rewardXp = rewardXp
            )
            .single()

        assertEquals(QuestConditionType.IN_DIMENSION, generated.conditions.single().type)
        assertEquals("NETHER", generated.conditions.single().value)
    }
    private fun target(
        id: String,
        action: QuestAction,
        min: Long,
        max: Long,
        conditions: Set<QuestConditionType> = emptySet()
    ) = QuestTarget(
        id = id,
        allowedActions = setOf(action),
        minimumAmount = min,
        maximumAmount = max,
        supportedConditions = conditions
    )

    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
