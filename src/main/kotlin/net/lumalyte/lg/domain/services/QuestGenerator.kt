package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.values.QuestAction
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

data class QuestGenerationSettings(
    val conditionChancePercent: Int = 45,
    val secondConditionChancePercent: Int = 15,
    val maxConditions: Int = 2,
    val exactRepeatCooldownWeeks: Int = 8,
    val actionTargetCooldownWeeks: Int = 3,
    val axisCenters: List<Int> = listOf(0, 0, 0, 0, 500, -500, 1000, -1000, 2500, -2500),
    val axisWidths: List<Int> = listOf(50, 100, 250, 500),
    val yLevels: List<Int> = listOf(-32, 0, 32, 64, 96, 128)
) {
    init {
        require(conditionChancePercent in 0..100)
        require(secondConditionChancePercent in 0..100)
        require(maxConditions in 0..4)
        require(exactRepeatCooldownWeeks >= 0)
        require(actionTargetCooldownWeeks >= 0)
        require(axisCenters.isNotEmpty())
        require(axisWidths.isNotEmpty() && axisWidths.all { it > 0 })
        require(yLevels.isNotEmpty())
    }
}

data class QuestGenerationHistory(val weeks: List<List<QuestDefinition>> = emptyList())

class QuestGenerator(
    private val validator: QuestGenerationValidator,
    private val random: Random = Random.Default,
    private val maxAttemptsPerQuest: Int = 64
) {
    init {
        require(maxAttemptsPerQuest > 0) { "Generation attempts must be positive" }
    }

    fun generate(
        targets: List<QuestTarget>,
        questCount: Int,
        settings: QuestGenerationSettings,
        history: QuestGenerationHistory = QuestGenerationHistory(),
        rewardXp: (QuestRewardTier) -> Int
    ): List<QuestDefinition> {
        require(questCount >= 0) { "Quest count cannot be negative" }
        if (questCount == 0) return emptyList()

        val sortedTargets = targets.sortedWith(
            compareBy<QuestTarget>({ it.allowedActions.minOf { action -> action.name } }, { it.id })
        )
        val actions = QuestAction.entries
            .filter { action -> sortedTargets.any { action in it.allowedActions } }
            .sortedBy { it.name }
        require(actions.isNotEmpty()) { "Quest generation requires at least one discoverable action/target pair" }

        val selected = mutableListOf<QuestDefinition>()
        repeat(questCount) {
            val generated = generateOne(actions, sortedTargets, selected, settings, history, rewardXp)
                ?: deterministicFallback(actions, sortedTargets, selected, settings, history, rewardXp)
            requireNotNull(generated) { "Unable to generate $questCount unique quests from the discovered target pool" }
            selected += generated
        }
        return selected
    }

    private fun generateOne(
        actions: List<QuestAction>,
        targets: List<QuestTarget>,
        selected: List<QuestDefinition>,
        settings: QuestGenerationSettings,
        history: QuestGenerationHistory,
        rewardXp: (QuestRewardTier) -> Int
    ): QuestDefinition? {
        repeat(maxAttemptsPerQuest) {
            val action = actions[random.nextInt(actions.size)]
            val compatible = targets.filter { action in it.allowedActions }
            if (compatible.isEmpty()) return@repeat
            val target = compatible[random.nextInt(compatible.size)]
            val amount = humanAmount(target.minimumAmount, target.maximumAmount)
            val conditions = generateConditions(target, settings)
            val tier = deriveTier(target, amount, conditions.size)
            val quest = QuestDefinition(
                id = generatedId(action, target, amount, conditions),
                action = action,
                target = target,
                targetCount = amount,
                tier = tier,
                conditions = conditions,
                experienceReward = rewardXp(tier)
            )
            if (isAcceptable(quest, selected, history, settings)) return quest
        }
        return null
    }

    private fun deterministicFallback(
        actions: List<QuestAction>,
        targets: List<QuestTarget>,
        selected: List<QuestDefinition>,
        settings: QuestGenerationSettings,
        history: QuestGenerationHistory,
        rewardXp: (QuestRewardTier) -> Int
    ): QuestDefinition? {
        for (action in actions) {
            for (target in targets.filter { action in it.allowedActions }) {
                val amount = humanize(target.minimumAmount.coerceAtLeast(1))
                val tier = deriveTier(target, amount, 0)
                val quest = QuestDefinition(
                    id = generatedId(action, target, amount, emptyList()),
                    action = action,
                    target = target,
                    targetCount = amount,
                    tier = tier,
                    experienceReward = rewardXp(tier)
                )
                if (isAcceptable(quest, selected, history, settings)) return quest
            }
        }
        // History cooldowns are guardrails, not a reason to leave a week without quests.
        // If the discovered provider pool is too small to satisfy recent-history rejection,
        // deterministically relax history while preserving semantic validity and current-set uniqueness.
        for (action in actions) {
            for (target in targets.filter { action in it.allowedActions }) {
                val amount = humanize(target.minimumAmount.coerceAtLeast(1))
                val tier = deriveTier(target, amount, 0)
                val quest = QuestDefinition(
                    id = generatedId(action, target, amount, emptyList()),
                    action = action,
                    target = target,
                    targetCount = amount,
                    tier = tier,
                    experienceReward = rewardXp(tier)
                )
                if (isFallbackAcceptable(quest, selected)) return quest
            }
        }
        return null
    }

    private fun isFallbackAcceptable(
        quest: QuestDefinition,
        selected: List<QuestDefinition>
    ): Boolean = validator.validate(quest).isValid &&
        selected.none { it.actionTargetFingerprint() == quest.actionTargetFingerprint() }

    private fun isAcceptable(
        quest: QuestDefinition,
        selected: List<QuestDefinition>,
        history: QuestGenerationHistory,
        settings: QuestGenerationSettings
    ): Boolean {
        if (!validator.validate(quest).isValid) return false
        if (selected.any { it.actionTargetFingerprint() == quest.actionTargetFingerprint() }) return false

        val exactRecent = history.weeks.take(settings.exactRepeatCooldownWeeks).flatten()
        if (exactRecent.any { it.fingerprint() == quest.fingerprint() }) return false

        val actionTargetRecent = history.weeks.take(settings.actionTargetCooldownWeeks).flatten()
        if (actionTargetRecent.any { it.actionTargetFingerprint() == quest.actionTargetFingerprint() }) return false

        return true
    }

    private fun generateConditions(target: QuestTarget, settings: QuestGenerationSettings): List<QuestCondition> {
        if (settings.maxConditions == 0 || target.supportedConditions.isEmpty()) return emptyList()
        if (random.nextInt(100) >= settings.conditionChancePercent) return emptyList()

        val available = target.supportedConditions.sortedBy { it.name }.toMutableList()
        val result = mutableListOf<QuestCondition>()

        fun rollOne(): QuestCondition? {
            while (available.isNotEmpty()) {
                val type = available.removeAt(random.nextInt(available.size))
                if (result.any { conflicts(it.type, type) }) continue
                conditionFor(type, target, settings)?.let { return it }
            }
            return null
        }

        rollOne()?.let(result::add)
        while (
            result.size < settings.maxConditions &&
            available.isNotEmpty() &&
            random.nextInt(100) < settings.secondConditionChancePercent
        ) {
            rollOne()?.let(result::add) ?: break
        }
        return result.sortedBy { it.type.name }
    }

    private fun conditionFor(
        type: QuestConditionType,
        target: QuestTarget,
        settings: QuestGenerationSettings
    ): QuestCondition? = when (type) {
        QuestConditionType.X_WITHIN, QuestConditionType.Z_WITHIN -> {
            val center = settings.axisCenters[random.nextInt(settings.axisCenters.size)]
            val width = settings.axisWidths[random.nextInt(settings.axisWidths.size)]
            QuestCondition(type, "$center:$width")
        }
        QuestConditionType.ABOVE_Y, QuestConditionType.BELOW_Y ->
            QuestCondition(type, settings.yLevels[random.nextInt(settings.yLevels.size)].toString())
        QuestConditionType.IN_DIMENSION -> target.naturalDimensions.sorted().takeIf { it.isNotEmpty() }
            ?.let { QuestCondition(type, it[random.nextInt(it.size)]) }
        QuestConditionType.IN_BIOME -> target.naturalBiomes.sorted().takeIf { it.isNotEmpty() }
            ?.let { QuestCondition(type, it[random.nextInt(it.size)]) }
        QuestConditionType.WITHOUT_ELYTRA -> QuestCondition(type)
        QuestConditionType.WITH_TOOL,
        QuestConditionType.WITHOUT_TOOL,
        QuestConditionType.USING_TRANSPORT -> null
    }

    private fun conflicts(existing: QuestConditionType, incoming: QuestConditionType): Boolean =
        (existing == QuestConditionType.ABOVE_Y && incoming == QuestConditionType.BELOW_Y) ||
            (existing == QuestConditionType.BELOW_Y && incoming == QuestConditionType.ABOVE_Y)

    private fun humanAmount(minimum: Long, maximum: Long): Long {
        if (minimum >= maximum) return humanize(minimum)
        val span = maximum - minimum
        val raw = minimum + random.nextLong(span + 1)
        return humanize(raw).coerceIn(minimum, maximum)
    }

    private fun humanize(value: Long): Long {
        val step = when {
            value < 10 -> 1L
            value < 50 -> 5L
            value < 250 -> 10L
            value < 1_000 -> 25L
            value < 5_000 -> 100L
            value < 25_000 -> 500L
            else -> 1_000L
        }
        return max(step, (value.toDouble() / step).roundToLong() * step)
    }

    private fun deriveTier(target: QuestTarget, amount: Long, conditionCount: Int): QuestRewardTier {
        val range = (target.maximumAmount - target.minimumAmount).coerceAtLeast(1)
        val percentile = (amount - target.minimumAmount).toDouble() / range.toDouble()
        val score = target.rarity.ordinal + when {
            percentile >= 0.80 -> 2
            percentile >= 0.45 -> 1
            else -> 0
        } + conditionCount

        return when {
            conditionCount > 0 && score >= 5 -> QuestRewardTier.CONDITIONED
            score >= 5 -> QuestRewardTier.HEADLINE
            score >= 3 -> QuestRewardTier.CHALLENGING
            conditionCount > 0 -> QuestRewardTier.CONDITIONED
            else -> QuestRewardTier.COMMON
        }
    }

    private fun generatedId(
        action: QuestAction,
        target: QuestTarget,
        amount: Long,
        conditions: List<QuestCondition>
    ): String {
        val canonical = buildString {
            append(action.name).append('|').append(target.id.lowercase()).append('|').append(amount).append('|')
            conditions.map(QuestCondition::canonical).sorted().joinTo(this, separator = ",")
        }
        return "generated-" + canonical.hashCode().toUInt().toString(16)
    }
}
