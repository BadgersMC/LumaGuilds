package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import java.time.Duration
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

interface QuestRewardSink {
    fun awardExperience(guildId: UUID, amount: Int, transactionId: UUID): Boolean
    fun awardItems(actorId: UUID, rewards: List<QuestItemReward>, transactionId: UUID): Boolean
    fun finalizeItems(actorId: UUID, transactionId: UUID) = Unit
}

data class QuestProgressContext(
    val dimension: String? = null,
    val biome: String? = null,
    val tool: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
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
    fun recentQuestSets(limit: Int): List<WeeklyQuestSet> = repository.getRecentQuestSets(limit)
    fun deactivate() = repository.deactivateActiveQuestSet()

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
            .filter { it.action == action && targetMatches(it.target.id, targetId) }
            .filter {
                !(context.playerPlacedBlock &&
                    it.target.provenancePolicy == net.lumalyte.lg.domain.entities.BlockProvenancePolicy.NATURAL_ONLY)
            }
            .filter { conditionsMatch(it.conditions, context) }
            .forEach { quest ->
                val current = repository.getProgress(active.weekId, quest.id, guildId)
                    ?: GuildQuestProgress(active.weekId, quest.id, guildId)
                repository.saveProgress(current.withIncrementedCount(amount, quest.targetCount))
            }
    }

    fun claimQuest(actorId: UUID, guildId: UUID, questId: String): Boolean {
        val active = repository.getActiveQuestSet() ?: return false
        val quest = active.quests.firstOrNull { it.id == questId } ?: return false
        val progress = repository.getProgress(active.weekId, questId, guildId) ?: return false
        if (!progress.isCompletable(quest.targetCount)) return false
        if (!repository.tryMarkClaimed(active.weekId, questId, guildId, actorId)) return false

        val claimed = progress.withClaimed(actorId)
        deliverClaimReward(active, quest, claimed)
        awardFullSetBonusIfComplete(active, guildId)
        return true
    }

    /**
     * Finishes durable quest claims left between the claim marker and reward delivery.
     * Safe to call repeatedly: XP and item delivery use stable transaction identities.
     */
    fun reconcilePendingRewards(): Int {
        var delivered = 0
        repository.getPendingClaimRewards().forEach { progress ->
            val questSet = repository.getQuestSet(progress.weekId) ?: return@forEach
            val quest = questSet.quests.firstOrNull { it.id == progress.questId } ?: return@forEach
            if (deliverClaimReward(questSet, quest, progress)) delivered++
        }

        repository.getActiveQuestSet()?.let { active ->
            reconcileFullSetBonuses(active)
        }
        return delivered
    }

    fun resetWeeklyQuests(nextQuestSet: WeeklyQuestSet) {
        val active = repository.getActiveQuestSet()
        if (active?.weekId == nextQuestSet.weekId) return

        active?.let { current ->
            check(reconcileFullSetBonuses(current)) {
                "Unable to durably settle weekly quest completion bonuses"
            }
            current.quests.filter { it.leaderboard }.forEach { quest ->
                val maxRank = quest.leaderboardPayouts.keys.maxOrNull() ?: 0
                if (maxRank > 0) {
                    repository.getQuestLeaderboard(current.weekId, quest.id, maxRank)
                        .forEachIndexed { index, progress ->
                            quest.leaderboardPayouts[index + 1]?.takeIf { it > 0 }?.let { amount ->
                                if (!repository.isLeaderboardRecipientPaid(current.weekId, quest.id, progress.guildId)) {
                                    val transactionId = rewardTransactionId(
                                        "leaderboard",
                                        current.weekId,
                                        quest.id,
                                        progress.guildId,
                                    )
                                    check(rewards.awardExperience(progress.guildId, amount, transactionId)) {
                                        "Unable to durably award weekly leaderboard XP"
                                    }
                                    check(repository.markLeaderboardRecipientPaid(
                                        current.weekId,
                                        quest.id,
                                        progress.guildId,
                                    )) {
                                        "Unable to persist weekly leaderboard payout marker"
                                    }
                                }
                            }
                        }
                }
            }
            repository.deleteWeekProgress(current.weekId)
        }
        repository.saveActiveQuestSet(nextQuestSet)
    }

    private fun deliverClaimReward(
        questSet: WeeklyQuestSet,
        quest: net.lumalyte.lg.domain.entities.QuestDefinition,
        progress: GuildQuestProgress,
    ): Boolean {
        if (progress.rewardDelivered) return true
        val transactionId = rewardTransactionId(
            "claim",
            questSet.weekId,
            quest.id,
            progress.guildId,
        )
        if (quest.experienceReward > 0 &&
            !rewards.awardExperience(progress.guildId, quest.experienceReward, transactionId)
        ) return false

        val itemActorId = if (quest.itemRewards.isNotEmpty()) {
            val actorId = progress.claimActorId ?: return false
            if (!rewards.awardItems(actorId, quest.itemRewards, transactionId)) return false
            actorId
        } else null

        val delivered = repository.markClaimRewardDelivered(
            questSet.weekId,
            quest.id,
            progress.guildId,
        )
        if (delivered && itemActorId != null) {
            runCatching { rewards.finalizeItems(itemActorId, transactionId) }
        }
        return delivered
    }

    private fun reconcileFullSetBonuses(active: WeeklyQuestSet): Boolean {
        val guildIds = repository.getClaimedProgress(active.weekId)
            .asSequence()
            .map { it.guildId }
            .distinct()
            .toList()
        return guildIds.all { awardFullSetBonusIfComplete(active, it) }
    }

    private fun awardFullSetBonusIfComplete(active: WeeklyQuestSet, guildId: UUID): Boolean {
        if (fullSetBonusExperience <= 0) return true
        if (repository.isWeeklyBonusAwarded(active.weekId, guildId)) return true
        val milestones = active.quests.filter { it.targetCount > 0 }
        if (milestones.isEmpty()) return true
        val allComplete = milestones.all { quest ->
            repository.getProgress(active.weekId, quest.id, guildId)?.claimed == true
        }
        if (!allComplete) return true

        val transactionId = rewardTransactionId("full-set", active.weekId, "all", guildId)
        if (!rewards.awardExperience(guildId, fullSetBonusExperience, transactionId)) return false
        repository.tryMarkWeeklyBonusAwarded(active.weekId, guildId)
        return repository.isWeeklyBonusAwarded(active.weekId, guildId)
    }

    private fun rewardTransactionId(
        kind: String,
        weekId: String,
        questId: String,
        guildId: UUID,
    ): UUID = UUID.nameUUIDFromBytes(
        "lumaguilds:weekly-quest:$kind:$weekId:$questId:$guildId"
            .toByteArray(StandardCharsets.UTF_8)
    )

    private fun targetMatches(questTarget: String, eventTarget: String): Boolean {
        if (questTarget == eventTarget) return true
        if (questTarget == "ANY" || questTarget.endsWith("/any")) return true
        if (':' !in questTarget) {
            return questTarget.equals(eventTarget.substringAfterLast('/'), ignoreCase = true)
        }
        return false
    }

    private fun conditionsMatch(conditions: List<QuestCondition>, context: QuestProgressContext): Boolean =
        conditions.all { conditionMatches(it, context) }

    private fun conditionMatches(condition: QuestCondition, context: QuestProgressContext): Boolean = when (condition.type) {
        QuestConditionType.WITH_TOOL -> context.tool == condition.value
        QuestConditionType.WITHOUT_TOOL -> context.tool != condition.value
        QuestConditionType.IN_DIMENSION -> context.dimension == condition.value
        QuestConditionType.IN_BIOME -> context.biome == condition.value
        QuestConditionType.ABOVE_Y ->
            context.y?.let { it > (condition.value?.toIntOrNull() ?: Int.MAX_VALUE) } == true
        QuestConditionType.BELOW_Y ->
            context.y?.let { it < (condition.value?.toIntOrNull() ?: Int.MIN_VALUE) } == true
        QuestConditionType.X_WITHIN -> axisMatches(context.x, condition.value)
        QuestConditionType.Z_WITHIN -> axisMatches(context.z, condition.value)
        QuestConditionType.USING_TRANSPORT -> context.transport == condition.value
        QuestConditionType.WITHOUT_ELYTRA -> !context.usedElytra
    }

    private fun axisMatches(actual: Int?, encoded: String?): Boolean {
        actual ?: return false
        val parts = encoded.orEmpty().split(':', limit = 2)
        val center = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val radius = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return false
        return abs(actual.toLong() - center.toLong()) <= radius.toLong()
    }
}
