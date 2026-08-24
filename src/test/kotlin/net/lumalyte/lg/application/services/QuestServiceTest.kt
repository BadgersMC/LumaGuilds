package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class QuestServiceTest {
    private val week = WeeklyQuestSet(
        weekId = "2026-W35",
        startsAt = Instant.parse("2026-08-24T00:00:00Z"),
        endsAt = Instant.parse("2026-08-31T00:00:00Z"),
        quests = listOf(
            quest("zombies", 2, 500),
            quest("skeletons", 1, 750)
        )
    )

    @Test
    fun `shared definitions keep guild progress independent and allow overflow`() {
        val repository = FakeQuestRepository(week)
        val service = QuestService(repository, RecordingRewardSink(), fullSetBonusExperience = 2_000)
        val alpha = UUID.randomUUID()
        val beta = UUID.randomUUID()

        service.incrementProgress(alpha, QuestAction.KILL_MOBS, "ZOMBIE", 3)
        service.incrementProgress(beta, QuestAction.KILL_MOBS, "ZOMBIE", 1)

        assertEquals(3, service.progressFor(alpha, "zombies").currentCount)
        assertEquals(1, service.progressFor(beta, "zombies").currentCount)
        assertEquals(week.quests, service.activeQuestSet()!!.quests)
    }

    @Test
    fun `milestones and full set bonus are awarded once`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val service = QuestService(repository, rewards, fullSetBonusExperience = 2_000)
        val guild = UUID.randomUUID()
        val actor = UUID.randomUUID()

        service.incrementProgress(guild, QuestAction.KILL_MOBS, "ZOMBIE", 2)
        service.incrementProgress(guild, QuestAction.KILL_MOBS, "SKELETON", 1)

        assertEquals(emptyList<Int>(), rewards.experienceAwards.map { it.second })

        assertTrue(service.claimQuest(actor, guild, "zombies"))
        assertFalse(service.claimQuest(actor, guild, "zombies"))
        assertTrue(service.claimQuest(actor, guild, "skeletons"))

        assertEquals(listOf(500, 750, 2_000), rewards.experienceAwards.map { it.second })
        assertTrue(repository.isWeeklyBonusAwarded(week.weekId, guild))
    }

    @Test
    fun `leaderboard payouts happen before old week is cleared and are idempotent`() {
        val leaderboardQuest = quest("dragons", 0, 0).copy(
            leaderboard = true,
            leaderboardPayouts = mapOf(1 to 5_000, 2 to 2_500)
        )
        val leaderboardWeek = week.copy(quests = listOf(leaderboardQuest))
        val nextWeek = week.copy(weekId = "2026-W36", startsAt = week.endsAt, endsAt = Instant.parse("2026-09-07T00:00:00Z"))
        val repository = FakeQuestRepository(leaderboardWeek)
        val rewards = RecordingRewardSink()
        val service = QuestService(repository, rewards, fullSetBonusExperience = 0)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        repository.saveProgress(GuildQuestProgress(leaderboardWeek.weekId, "dragons", first, 9))
        repository.saveProgress(GuildQuestProgress(leaderboardWeek.weekId, "dragons", second, 4))

        service.resetWeeklyQuests(nextWeek)
        service.resetWeeklyQuests(nextWeek)

        assertEquals(listOf(first to 5_000, second to 2_500), rewards.experienceAwards)
        assertEquals("2026-W36", repository.getActiveQuestSet()!!.weekId)
        assertTrue(repository.getGuildProgress(leaderboardWeek.weekId, first).isEmpty())
    }

    @Test
    fun `player placed block cannot satisfy natural only quest`() {
        val naturalQuest = quest("stone", 10, 500).copy(
            action = QuestAction.MINE_BLOCKS,
            target = QuestTarget(
                "STONE", setOf(QuestAction.MINE_BLOCKS), 1, 100,
                provenancePolicy = BlockProvenancePolicy.NATURAL_ONLY
            )
        )
        val repository = FakeQuestRepository(week.copy(quests = listOf(naturalQuest)))
        val service = QuestService(repository, RecordingRewardSink(), fullSetBonusExperience = 0)
        val guild = UUID.randomUUID()

        service.incrementProgress(
            guild, QuestAction.MINE_BLOCKS, "STONE", 1,
            QuestProgressContext(playerPlacedBlock = true)
        )

        assertEquals(0, service.progressFor(guild, "stone").currentCount)
    }

    private fun quest(id: String, target: Long, xp: Int): QuestDefinition {
        val targetId = if (id == "skeletons") "SKELETON" else if (id == "dragons") "ENDER_DRAGON" else "ZOMBIE"
        return QuestDefinition(
            id = id,
            nameKey = "quests.$id.name",
            descriptionKey = "quests.$id.description",
            action = QuestAction.KILL_MOBS,
            target = QuestTarget(targetId, setOf(QuestAction.KILL_MOBS), 0, 10_000),
            targetCount = target,
            tier = QuestRewardTier.COMMON,
            experienceReward = xp
        )
    }
}

private class RecordingRewardSink : QuestRewardSink {
    val experienceAwards = mutableListOf<Pair<UUID, Int>>()
    override fun awardExperience(guildId: UUID, amount: Int) { experienceAwards += guildId to amount }
    override fun awardItems(actorId: UUID, rewards: List<net.lumalyte.lg.domain.entities.QuestItemReward>) = Unit
}

private class FakeQuestRepository(initial: WeeklyQuestSet?) : QuestRepository {
    private var active = initial
    private val progress = mutableMapOf<Triple<String, String, UUID>, GuildQuestProgress>()
    private val bonuses = mutableSetOf<Pair<String, UUID>>()
    private val paidLeaderboardRecipients = mutableSetOf<Triple<String, String, UUID>>()

    override fun getActiveQuestSet(): WeeklyQuestSet? = active
    override fun saveActiveQuestSet(questSet: WeeklyQuestSet) { active = questSet }
    override fun deactivateActiveQuestSet() { active = null }
    override fun getProgress(weekId: String, questId: String, guildId: UUID): GuildQuestProgress? = progress[Triple(weekId, questId, guildId)]
    override fun saveProgress(value: GuildQuestProgress) { progress[Triple(value.weekId, value.questId, value.guildId)] = value }
    override fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress> = progress.values.filter { it.weekId == weekId && it.guildId == guildId }
    override fun getQuestLeaderboard(weekId: String, questId: String, limit: Int): List<GuildQuestProgress> = progress.values.filter { it.weekId == weekId && it.questId == questId }.sortedByDescending { it.currentCount }.take(limit)
    override fun tryMarkClaimed(weekId: String, questId: String, guildId: UUID): Boolean {
        val key = Triple(weekId, questId, guildId)
        val value = progress[key] ?: return false
        if (value.claimed) return false
        progress[key] = value.withClaimed()
        return true
    }
    override fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean = bonuses.add(weekId to guildId)
    override fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean = weekId to guildId in bonuses
    override fun isLeaderboardRecipientPaid(weekId: String, questId: String, guildId: UUID): Boolean =
        Triple(weekId, questId, guildId) in paidLeaderboardRecipients
    override fun markLeaderboardRecipientPaid(weekId: String, questId: String, guildId: UUID) {
        paidLeaderboardRecipients += Triple(weekId, questId, guildId)
    }
    override fun deleteWeekProgress(weekId: String) { progress.keys.removeIf { it.first == weekId } }
}
