package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.QuestAction

enum class QuestRewardTier { COMMON, CHALLENGING, HEADLINE, CONDITIONED }

enum class QuestConditionType {
    WITH_TOOL,
    WITHOUT_TOOL,
    IN_DIMENSION,
    IN_BIOME,
    ABOVE_Y,
    BELOW_Y,
    USING_TRANSPORT,
    WITHOUT_ELYTRA
}

enum class BlockProvenancePolicy { NATURAL_ONLY, PLAYER_PLACED, ANY }

data class QuestCondition(val type: QuestConditionType, val value: String? = null)

data class QuestTarget(
    val id: String,
    val allowedActions: Set<QuestAction>,
    val minimumAmount: Long,
    val maximumAmount: Long,
    val naturalDimensions: Set<String> = emptySet(),
    val naturalBiomes: Set<String> = emptySet(),
    val supportedConditions: Set<QuestConditionType> = emptySet(),
    val provenancePolicy: BlockProvenancePolicy = BlockProvenancePolicy.ANY
) {
    init {
        require(id.isNotBlank()) { "Quest target id cannot be blank" }
        require(minimumAmount >= 0) { "Minimum amount cannot be negative" }
        require(maximumAmount >= minimumAmount) { "Maximum amount cannot be below minimum" }
    }
}

data class QuestItemReward(val itemId: String, val amount: Int)

data class QuestDefinition(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val action: QuestAction,
    val target: QuestTarget,
    val targetCount: Long,
    val tier: QuestRewardTier,
    val condition: QuestCondition? = null,
    val experienceReward: Int = 0,
    val itemRewards: List<QuestItemReward> = emptyList(),
    val leaderboard: Boolean = false,
    val leaderboardPayouts: Map<Int, Int> = emptyMap()
)

data class QuestCandidate(val definition: QuestDefinition, val weight: Int = 1) {
    init {
        require(weight > 0) { "Quest candidate weight must be positive" }
    }
}
