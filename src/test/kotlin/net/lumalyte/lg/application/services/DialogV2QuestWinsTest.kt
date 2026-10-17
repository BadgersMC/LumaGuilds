package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

internal class DialogV2QuestWinsTest {
    @Test
    fun winsIgnoreActiveFutureAndEmptyScoresAndReadOnlyRetainedHistory() {
        val repository = mockk<QuestRepository>()
        val reward = mockk<QuestRewardSink>()
        val quest = mockk<QuestDefinition>()
        every { quest.id } returns "ore"
        val old = WeeklyQuestSet("old", Instant.EPOCH, Instant.EPOCH.plusSeconds(10), listOf(quest))
        val empty = old.copy(weekId = "empty")
        val active = old.copy(weekId = "active")
        val future = old.copy(weekId = "future", endsAt = Instant.now().plusSeconds(1000))
        val id = UUID.randomUUID()
        every { repository.getActiveQuestSet() } returns active
        every { repository.getRecentQuestSets(52) } returns listOf(old, empty, active, future)
        every { repository.getQuestLeaderboard("old", "ore", 1) } returns
            listOf(
                GuildQuestProgress("old", "ore", id, 7),
            )
        every { repository.getQuestLeaderboard("empty", "ore", 1) } returns
            listOf(
                GuildQuestProgress("empty", "ore", id),
            )
        assertEquals(mapOf(id to 1), QuestService(repository, reward, 0).recordedWins())
        verify(exactly = 0) { repository.getQuestLeaderboard("active", any(), any()) }
        verify(exactly = 0) { repository.getQuestLeaderboard("future", any(), any()) }
        verify(exactly = 0) { reward.awardExperience(any(), any(), any()) }
    }
}
