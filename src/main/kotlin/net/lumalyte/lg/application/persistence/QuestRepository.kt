package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import java.util.UUID

interface QuestRepository {
    /** Verify canonical contribution provenance and consume it atomically with progress, once per quest. */
    fun applyBankContribution(transactionId: UUID, weekId: String, questId: String, guildId: UUID,
                              actorId: UUID, targetCount: Long): GuildQuestProgress? = null
    fun getActiveQuestSet(): WeeklyQuestSet?
    fun getQuestSet(weekId: String): WeeklyQuestSet?
    fun saveActiveQuestSet(questSet: WeeklyQuestSet)
    fun getRecentQuestSets(limit: Int): List<WeeklyQuestSet>
    fun deactivateActiveQuestSet()
    fun getProgress(weekId: String, questId: String, guildId: UUID): GuildQuestProgress?
    fun saveProgress(value: GuildQuestProgress)
    fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress>
    fun getQuestLeaderboard(weekId: String, questId: String, limit: Int): List<GuildQuestProgress>
    fun getClaimedProgress(weekId: String): List<GuildQuestProgress>
    fun getUnclaimedCompletedProgress(weekId: String): List<GuildQuestProgress>
    fun getPendingClaimRewards(): List<GuildQuestProgress>
    fun tryMarkClaimed(weekId: String, questId: String, guildId: UUID, actorId: UUID?): Boolean
    fun markClaimRewardDelivered(weekId: String, questId: String, guildId: UUID): Boolean
    fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean
    fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean
    fun isLeaderboardRecipientPaid(weekId: String, questId: String, guildId: UUID): Boolean
    fun markLeaderboardRecipientPaid(weekId: String, questId: String, guildId: UUID): Boolean
    fun deleteWeekProgress(weekId: String)
}
