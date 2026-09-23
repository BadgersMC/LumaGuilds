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
    X_WITHIN,
    Z_WITHIN,
    USING_TRANSPORT,
    WITHOUT_ELYTRA
}

enum class BlockProvenancePolicy { NATURAL_ONLY, PLAYER_PLACED, ANY }

enum class QuestTargetRarity {
    BULK,
    COMMON,
    UNCOMMON,
    RARE,
    PRECIOUS
}

data class QuestCondition(val type: QuestConditionType, val value: String? = null) {
    fun canonical(): String = "${type.name}:${value.orEmpty().lowercase()}"
}

data class QuestTarget(
    val id: String,
    val allowedActions: Set<QuestAction>,
    val minimumAmount: Long,
    val maximumAmount: Long,
    val naturalDimensions: Set<String> = emptySet(),
    val naturalBiomes: Set<String> = emptySet(),
    val supportedConditions: Set<QuestConditionType> = emptySet(),
    val provenancePolicy: BlockProvenancePolicy = BlockProvenancePolicy.ANY,
    val rarity: QuestTargetRarity = QuestTargetRarity.COMMON
) {
    init {
        require(id.isNotBlank()) { "Quest target id cannot be blank" }
        require(':' in id && '/' in id.substringAfter(':')) {
            "Quest target id must be namespaced and typed, e.g. minecraft:block/stone: $id"
        }
        require(allowedActions.isNotEmpty()) { "Quest target must support at least one action" }
        require(minimumAmount > 0) { "Minimum amount must be positive" }
        require(maximumAmount >= minimumAmount) { "Maximum amount cannot be below minimum" }
    }

    val namespace: String get() = id.substringBefore(':')
    val kind: String get() = id.substringAfter(':').substringBefore('/')
    val value: String get() = id.substringAfter('/')

    fun actionKey(action: QuestAction): String = "${action.name}|${id.lowercase()}"
}

data class QuestItemReward(val itemId: String, val amount: Int)

data class QuestDefinition(
    val id: String,
    val nameKey: String = "menu.quests.item.quest.name",
    val descriptionKey: String = "menu.quests.item.quest.description",
    val action: QuestAction,
    val target: QuestTarget,
    val targetCount: Long,
    val tier: QuestRewardTier,
    val conditions: List<QuestCondition> = emptyList(),
    val experienceReward: Int = 0,
    val itemRewards: List<QuestItemReward> = emptyList(),
    val leaderboard: Boolean = false,
    val leaderboardPayouts: Map<Int, Int> = emptyMap()
) {
    val condition: QuestCondition? get() = conditions.firstOrNull()

    fun fingerprint(): String = buildString {
        append(action.name).append('|')
        append(target.id.lowercase()).append('|')
        append(targetCount).append('|')
        conditions.map(QuestCondition::canonical).sorted().joinTo(this, separator = ",")
    }

    fun actionTargetFingerprint(): String = target.actionKey(action)
}
