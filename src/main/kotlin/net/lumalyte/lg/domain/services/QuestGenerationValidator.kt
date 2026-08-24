package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition

enum class QuestValidationFailure {
    ACTION_TARGET_INCOMPATIBLE,
    AMOUNT_OUT_OF_RANGE,
    CONDITION_UNSUPPORTED,
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

        quest.condition?.let { condition ->
            if (condition.type !in target.supportedConditions) {
                failures += QuestValidationFailure.CONDITION_UNSUPPORTED
            } else when (condition.type) {
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
                else -> Unit
            }
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
