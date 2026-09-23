package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition

enum class QuestValidationFailure {
    ACTION_TARGET_INCOMPATIBLE,
    AMOUNT_OUT_OF_RANGE,
    CONDITION_UNSUPPORTED,
    CONDITION_VALUE_INVALID,
    CONDITION_DUPLICATE,
    CONDITION_CONFLICT,
    TARGET_LOCATION_INCOMPATIBLE,
    CONDITION_REDUNDANT
}

data class QuestValidationResult(val failures: List<QuestValidationFailure>) {
    val isValid: Boolean get() = failures.isEmpty()
}

class QuestGenerationValidator {
    fun validate(quest: QuestDefinition): QuestValidationResult {
        val failures = mutableListOf<QuestValidationFailure>()
        val target = quest.target

        if (quest.action !in target.allowedActions) {
            failures += QuestValidationFailure.ACTION_TARGET_INCOMPATIBLE
        }
        if (quest.targetCount !in target.minimumAmount..target.maximumAmount) {
            failures += QuestValidationFailure.AMOUNT_OUT_OF_RANGE
        }

        if (quest.conditions.map { it.type }.distinct().size != quest.conditions.size) {
            failures += QuestValidationFailure.CONDITION_DUPLICATE
        }

        quest.conditions.forEach { condition ->
            if (condition.type !in target.supportedConditions) {
                failures += QuestValidationFailure.CONDITION_UNSUPPORTED
                return@forEach
            }

            when (condition.type) {
                QuestConditionType.IN_DIMENSION -> validateLocation(
                    condition.value,
                    target.naturalDimensions,
                    failures
                )
                QuestConditionType.IN_BIOME -> validateLocation(
                    condition.value,
                    target.naturalBiomes,
                    failures
                )
                QuestConditionType.ABOVE_Y, QuestConditionType.BELOW_Y -> {
                    if (condition.value?.toIntOrNull() == null) {
                        failures += QuestValidationFailure.CONDITION_VALUE_INVALID
                    }
                }
                QuestConditionType.X_WITHIN, QuestConditionType.Z_WITHIN -> {
                    val parts = condition.value.orEmpty().split(':', limit = 2)
                    val center = parts.getOrNull(0)?.toIntOrNull()
                    val radius = parts.getOrNull(1)?.toIntOrNull()
                    if (center == null || radius == null || radius <= 0) {
                        failures += QuestValidationFailure.CONDITION_VALUE_INVALID
                    }
                }
                QuestConditionType.WITH_TOOL,
                QuestConditionType.WITHOUT_TOOL,
                QuestConditionType.USING_TRANSPORT -> {
                    if (condition.value.isNullOrBlank()) failures += QuestValidationFailure.CONDITION_VALUE_INVALID
                }
                QuestConditionType.WITHOUT_ELYTRA -> Unit
            }
        }

        val above = quest.conditions.firstOrNull { it.type == QuestConditionType.ABOVE_Y }?.value?.toIntOrNull()
        val below = quest.conditions.firstOrNull { it.type == QuestConditionType.BELOW_Y }?.value?.toIntOrNull()
        if (above != null && below != null && above >= below) {
            failures += QuestValidationFailure.CONDITION_CONFLICT
        }

        return QuestValidationResult(failures.distinct())
    }

    private fun validateLocation(
        requested: String?,
        naturalLocations: Set<String>,
        failures: MutableList<QuestValidationFailure>
    ) {
        if (requested == null || requested !in naturalLocations) {
            failures += QuestValidationFailure.TARGET_LOCATION_INCOMPATIBLE
        } else if (naturalLocations.size == 1) {
            failures += QuestValidationFailure.CONDITION_REDUNDANT
        }
    }
}
