package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class QuestRepositorySQLiteTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: QuestRepositorySQLite

    @BeforeEach fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = QuestRepositorySQLite(storage)
    }

    @AfterEach fun tearDown() { storage.connection.close() }

    @Test
    fun `active shared set and independent progress survive repository recreation`() {
        val set = questSet()
        val alpha = UUID.randomUUID()
        val beta = UUID.randomUUID()
        repository.saveActiveQuestSet(set)
        repository.saveProgress(GuildQuestProgress(set.weekId, "zombies", alpha, 12))
        repository.saveProgress(GuildQuestProgress(set.weekId, "zombies", beta, 4))

        val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val second = QuestRepositorySQLite(secondStorage)
            assertEquals(set, second.getActiveQuestSet())
            assertEquals(12, second.getProgress(set.weekId, "zombies", alpha)!!.currentCount)
            assertEquals(4, second.getProgress(set.weekId, "zombies", beta)!!.currentCount)
        } finally {
            secondStorage.connection.close()
        }
    }

    @Test
    fun `claim bonus and leaderboard markers transition once`() {
        val set = questSet()
        val guild = UUID.randomUUID()
        repository.saveActiveQuestSet(set)
        repository.saveProgress(GuildQuestProgress(set.weekId, "zombies", guild, 100))

        assertTrue(repository.tryMarkClaimed(set.weekId, "zombies", guild))
        assertFalse(repository.tryMarkClaimed(set.weekId, "zombies", guild))
        assertTrue(repository.tryMarkWeeklyBonusAwarded(set.weekId, guild))
        assertFalse(repository.tryMarkWeeklyBonusAwarded(set.weekId, guild))
        assertTrue(repository.tryMarkLeaderboardPaid(set.weekId, "zombies"))
        assertFalse(repository.tryMarkLeaderboardPaid(set.weekId, "zombies"))
    }

    private fun questSet(): WeeklyQuestSet {
        val definition = QuestDefinition(
            id = "zombies",
            nameKey = "quests.zombies.name",
            descriptionKey = "quests.zombies.description",
            action = QuestAction.KILL_MOBS,
            target = QuestTarget("ZOMBIE", setOf(QuestAction.KILL_MOBS), 50, 500),
            targetCount = 100,
            tier = QuestRewardTier.COMMON,
            experienceReward = 500,
            leaderboard = true,
            leaderboardPayouts = mapOf(1 to 5_000)
        )
        return WeeklyQuestSet("2026-W35", Instant.parse("2026-08-24T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"), listOf(definition))
    }
}
