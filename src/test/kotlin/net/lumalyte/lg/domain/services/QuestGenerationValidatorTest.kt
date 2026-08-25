package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestGenerationValidatorTest {
    private val validator = QuestGenerationValidator()

    @Test
    fun `dragon quest without condition has no hidden location requirement`() {
        val result = validator.validate(
            quest(action = QuestAction.KILL_MOBS, target = dragon, amount = 3)
        )

        assertTrue(result.isValid)
        assertEquals(emptyList<QuestValidationFailure>(), result.failures)
    }

    @Test
    fun `wheat in nether is rejected as impossible`() {
        val result = validator.validate(
            quest(
                action = QuestAction.HARVEST_CROPS,
                target = wheat,
                amount = 1_000,
                condition = QuestCondition(QuestConditionType.IN_DIMENSION, "NETHER")
            )
        )

        assertEquals(listOf(QuestValidationFailure.TARGET_LOCATION_INCOMPATIBLE), result.failures)
    }

    @Test
    fun `dragon in end is rejected as redundant`() {
        val result = validator.validate(
            quest(
                action = QuestAction.KILL_MOBS,
                target = dragon,
                amount = 3,
                condition = QuestCondition(QuestConditionType.IN_DIMENSION, "THE_END")
            )
        )

        assertEquals(listOf(QuestValidationFailure.CONDITION_REDUNDANT), result.failures)
    }

    @Test
    fun `invalid action and amount return structured failures`() {
        val result = validator.validate(
            quest(action = QuestAction.CRAFT_ITEMS, target = sheep, amount = 1_000)
        )

        assertEquals(
            listOf(
                QuestValidationFailure.ACTION_TARGET_INCOMPATIBLE,
                QuestValidationFailure.AMOUNT_OUT_OF_RANGE
            ),
            result.failures
        )
    }

    @Test
    fun `malformed numeric condition value is rejected`() {
        val target = wheat.copy(supportedConditions = setOf(QuestConditionType.ABOVE_Y))
        val result = validator.validate(quest(QuestAction.HARVEST_CROPS, target, 1_000,
            QuestCondition(QuestConditionType.ABOVE_Y, "not-a-number")))

        assertEquals(listOf(QuestValidationFailure.CONDITION_VALUE_INVALID), result.failures)
    }

    private fun quest(
        action: QuestAction,
        target: QuestTarget,
        amount: Long,
        condition: QuestCondition? = null
    ) = QuestDefinition(
        id = "test",
        nameKey = "quests.test.name",
        descriptionKey = "quests.test.description",
        action = action,
        target = target,
        targetCount = amount,
        tier = QuestRewardTier.COMMON,
        condition = condition
    )

    private val dragon = QuestTarget(
        id = "ENDER_DRAGON",
        allowedActions = setOf(QuestAction.KILL_MOBS),
        minimumAmount = 1,
        maximumAmount = 5,
        naturalDimensions = setOf("THE_END"),
        supportedConditions = setOf(QuestConditionType.IN_DIMENSION)
    )

    private val wheat = QuestTarget(
        id = "WHEAT",
        allowedActions = setOf(QuestAction.HARVEST_CROPS),
        minimumAmount = 500,
        maximumAmount = 3_000,
        naturalDimensions = setOf("OVERWORLD"),
        supportedConditions = setOf(QuestConditionType.IN_DIMENSION)
    )

    private val sheep = QuestTarget(
        id = "SHEEP",
        allowedActions = setOf(QuestAction.KILL_MOBS),
        minimumAmount = 50,
        maximumAmount = 500,
        naturalDimensions = setOf("OVERWORLD"),
        supportedConditions = setOf(QuestConditionType.IN_DIMENSION)
    )
}
