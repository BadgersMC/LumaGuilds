package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import java.util.UUID

interface QuestRepository {
    fun getActiveQuestSet(): WeeklyQuestSet?
    fun saveActiveQuestSet(questSet: WeeklyQuestSet)
    fun getProgress(weekId: String, questId: String, guildId: UUID): GuildQuestProgress?
    fun saveProgress(value: GuildQuestProgress)
    fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress>
    fun getQuestLeaderboard(weekId: String, questId: String, limit: Int): List<GuildQuestProgress>
    fun tryMarkClaimed(weekId: String, questId: String, guildId: UUID): Boolean
    fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean
    fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean
    fun tryMarkLeaderboardPaid(weekId: String, questId: String): Boolean
}
