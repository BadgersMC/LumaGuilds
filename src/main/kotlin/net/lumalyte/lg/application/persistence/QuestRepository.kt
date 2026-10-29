package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import java.util.UUID

data class PendingBankContribution(val transactionId: UUID, val guildId: UUID, val actorId: UUID)

interface QuestRepository {
    /** Monotonic definition generation read before a bank deposit is prepared. */
    fun currentBankQuestRevision(): Long = 0L
    /** Candidates still require canonical receipt, run and definition validation on consumption. */
    fun pendingBankContributions(limit: Int): List<PendingBankContribution> = emptyList()

    /** Raise the attempt count before processing, so failed receipts cannot monopolize a batch. */
    fun noteBankContributionAttempt(transactionId: UUID) {}

    /** Record a completed recovery attempt so ineligible receipts cannot starve the queue. */
    fun finishBankContributionRecovery(transactionId: UUID) {}

    fun hasUnresolvedBankContributions(weekId: String): Boolean = false

    /** Fence new bank preparations before paying final scores for an old week. */
    fun beginBankQuestTransition(weekId: String): Boolean = true

    /** Atomically fence only the definition revision captured before reading the payout snapshot. */
    fun beginBankQuestTransition(weekId: String, expectedRevision: Long): Boolean = false

    /** Verify canonical contribution provenance and consume it atomically with progress, once per quest. */
    fun applyBankContribution(input: BankQuestContribution): GuildQuestProgress? = null

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
