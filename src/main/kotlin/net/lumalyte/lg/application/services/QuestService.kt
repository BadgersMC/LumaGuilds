package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import java.util.UUID
import java.time.Duration
import java.time.Instant

interface QuestRewardSink {
    fun awardExperience(guildId: UUID, amount: Int)
    fun awardItems(actorId: UUID, rewards: List<QuestItemReward>)
}

data class QuestProgressContext(
    val dimension: String? = null,
    val biome: String? = null,
    val tool: String? = null,
    val y: Int? = null,
    val transport: String? = null,
    val usedElytra: Boolean = false,
    val playerPlacedBlock: Boolean = false
)

class QuestService(
    private val repository: QuestRepository,
    private val rewards: QuestRewardSink,
    val fullSetBonusExperience: Int
) {
    fun activeQuestSet(): WeeklyQuestSet? = repository.getActiveQuestSet()

    fun progressFor(guildId: UUID, questId: String): GuildQuestProgress {
        val active = requireNotNull(repository.getActiveQuestSet()) { "No active weekly quest set" }
        require(active.quests.any { it.id == questId }) { "Unknown active quest: $questId" }
        return repository.getProgress(active.weekId, questId, guildId)
            ?: GuildQuestProgress(active.weekId, questId, guildId)
    }

    fun guildProgress(guildId: UUID): List<GuildQuestProgress> {
        val active = repository.getActiveQuestSet() ?: return emptyList()
        return active.quests.map { quest ->
            repository.getProgress(active.weekId, quest.id, guildId)
                ?: GuildQuestProgress(active.weekId, quest.id, guildId)
        }
    }

    fun rankFor(guildId: UUID, questId: String): Int? {
        val active = repository.getActiveQuestSet() ?: return null
        val quest = active.quests.firstOrNull { it.id == questId && it.leaderboard } ?: return null
        val rank = repository.getQuestLeaderboard(active.weekId, quest.id, Int.MAX_VALUE)
            .indexOfFirst { it.guildId == guildId }
        return rank.takeIf { it >= 0 }?.plus(1)
    }

    fun isWeeklyBonusAwarded(guildId: UUID): Boolean {
        val active = repository.getActiveQuestSet() ?: return false
        return repository.isWeeklyBonusAwarded(active.weekId, guildId)
    }

    fun timeRemaining(now: Instant = Instant.now()): Duration {
        val end = repository.getActiveQuestSet()?.endsAt ?: return Duration.ZERO
        return if (end.isAfter(now)) Duration.between(now, end) else Duration.ZERO
    }

    fun incrementProgress(
        guildId: UUID,
        action: QuestAction,
        targetId: String,
        amount: Long = 1,
        context: QuestProgressContext = QuestProgressContext()
    ) {
        if (amount <= 0) return
        val active = repository.getActiveQuestSet() ?: return
        active.quests.asSequence()
            .filter { it.action == action && (it.target.id == targetId || it.target.id == "ANY") }
            .filter {
                !(context.playerPlacedBlock &&
                    it.target.provenancePolicy == net.lumalyte.lg.domain.entities.BlockProvenancePolicy.NATURAL_ONLY)
            }
            .filter { conditionMatches(it.condition, context) }
            .forEach { quest ->
                val current = repository.getProgress(active.weekId, quest.id, guildId)
                    ?: GuildQuestProgress(active.weekId, quest.id, guildId)
                repository.saveProgress(current.withIncrementedCount(amount, quest.targetCount))
            }
        awardFullSetBonusIfComplete(active, guildId)
    }

    fun claimQuest(actorId: UUID, guildId: UUID, questId: String): Boolean {
        val active = repository.getActiveQuestSet() ?: return false
        val quest = active.quests.firstOrNull { it.id == questId } ?: return false
        val progress = repository.getProgress(active.weekId, questId, guildId) ?: return false
        if (!progress.isCompletable(quest.targetCount)) return false
        if (!repository.tryMarkClaimed(active.weekId, questId, guildId)) return false

        if (quest.experienceReward > 0) rewards.awardExperience(guildId, quest.experienceReward)
        if (quest.itemRewards.isNotEmpty()) rewards.awardItems(actorId, quest.itemRewards)
        awardFullSetBonusIfComplete(active, guildId)
        return true
    }

    fun resetWeeklyQuests(nextQuestSet: WeeklyQuestSet) {
        val active = repository.getActiveQuestSet()
        if (active?.weekId == nextQuestSet.weekId) return
        active?.quests?.filter { it.leaderboard }?.forEach { quest ->
            val maxRank = quest.leaderboardPayouts.keys.maxOrNull() ?: 0
            if (maxRank > 0 && repository.tryMarkLeaderboardPaid(active.weekId, quest.id)) {
                repository.getQuestLeaderboard(active.weekId, quest.id, maxRank)
                    .forEachIndexed { index, progress ->
                        quest.leaderboardPayouts[index + 1]?.takeIf { it > 0 }?.let {
                            rewards.awardExperience(progress.guildId, it)
                        }
                    }
            }
        }
        repository.saveActiveQuestSet(nextQuestSet)
    }

    private fun awardFullSetBonusIfComplete(active: WeeklyQuestSet, guildId: UUID) {
        if (fullSetBonusExperience <= 0) return
        val milestones = active.quests.filter { it.targetCount > 0 }
        if (milestones.isEmpty()) return
        val allComplete = milestones.all { quest ->
            (repository.getProgress(active.weekId, quest.id, guildId)?.currentCount ?: 0) >= quest.targetCount
        }
        if (allComplete && repository.tryMarkWeeklyBonusAwarded(active.weekId, guildId)) {
            rewards.awardExperience(guildId, fullSetBonusExperience)
        }
    }

    private fun conditionMatches(
        condition: net.lumalyte.lg.domain.entities.QuestCondition?,
        context: QuestProgressContext
    ): Boolean {
        condition ?: return true
        return when (condition.type) {
            net.lumalyte.lg.domain.entities.QuestConditionType.WITH_TOOL -> context.tool == condition.value
            net.lumalyte.lg.domain.entities.QuestConditionType.WITHOUT_TOOL -> context.tool != condition.value
            net.lumalyte.lg.domain.entities.QuestConditionType.IN_DIMENSION -> context.dimension == condition.value
            net.lumalyte.lg.domain.entities.QuestConditionType.IN_BIOME -> context.biome == condition.value
            net.lumalyte.lg.domain.entities.QuestConditionType.ABOVE_Y -> context.y?.let { it > (condition.value?.toIntOrNull() ?: Int.MAX_VALUE) } == true
            net.lumalyte.lg.domain.entities.QuestConditionType.BELOW_Y -> context.y?.let { it < (condition.value?.toIntOrNull() ?: Int.MIN_VALUE) } == true
            net.lumalyte.lg.domain.entities.QuestConditionType.USING_TRANSPORT -> context.transport == condition.value
            net.lumalyte.lg.domain.entities.QuestConditionType.WITHOUT_ELYTRA -> !context.usedElytra
        }
    }
}
