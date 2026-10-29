package net.lumalyte.lg.application.services

import io.mockk.mockk
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs
import net.lumalyte.lg.api.events.GuildBankContributionEvent
import net.lumalyte.lg.api.events.GuildBankDepositEvent
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.BankDepositXpEligibility
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import net.lumalyte.lg.infrastructure.listeners.QuestProgressListener
import net.lumalyte.lg.infrastructure.persistence.guilds.BankDepositDeliveryFixture
import net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.migrations.QuestSchema
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.bukkit.event.EventHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

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
    fun `shared definitions keep guild progress independent and continue scoring past completion`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val notifications = RecordingCompletionNotifier()
        val service = QuestService(
            repository,
            rewards,
            fullSetBonusExperience = 2_000,
            completionNotifier = notifications,
        )
        val alpha = UUID.randomUUID()
        val beta = UUID.randomUUID()

        service.incrementProgress(alpha, QuestAction.KILL_MOBS, "minecraft:entity/zombie", 3)
        service.incrementProgress(alpha, QuestAction.KILL_MOBS, "minecraft:entity/zombie", 5)
        service.incrementProgress(beta, QuestAction.KILL_MOBS, "minecraft:entity/zombie", 1)

        assertEquals(8, service.progressFor(alpha, "zombies").currentCount)
        assertEquals(1, service.progressFor(beta, "zombies").currentCount)
        assertEquals(listOf(alpha to 500), rewards.experienceAwards)
        assertEquals(listOf("zombies"), notifications.questIds)
        assertEquals(week.quests, service.activeQuestSet()!!.quests)
    }

    @Test
    fun `milestones auto claim notify and full set bonus are awarded once`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val notifications = RecordingCompletionNotifier()
        val service = QuestService(
            repository,
            rewards,
            fullSetBonusExperience = 2_000,
            completionNotifier = notifications,
        )
        val guild = UUID.randomUUID()
        val actor = UUID.randomUUID()

        service.incrementProgress(
            guild, QuestAction.KILL_MOBS, "minecraft:entity/zombie", 2, actorId = actor
        )
        service.incrementProgress(
            guild, QuestAction.KILL_MOBS, "minecraft:entity/skeleton", 1, actorId = actor
        )

        assertEquals(listOf(500, 750, 2_000), rewards.experienceAwards.map { it.second })
        assertEquals(listOf("zombies", "skeletons"), notifications.questIds)
        assertTrue(repository.getProgress(week.weekId, "zombies", guild)!!.claimed)
        assertTrue(repository.getProgress(week.weekId, "skeletons", guild)!!.claimed)
        assertFalse(service.claimQuest(actor, guild, "zombies"))
        assertTrue(repository.isWeeklyBonusAwarded(week.weekId, guild))
    }

    @Test
    fun `leaderboard payouts are idempotent and final scores are preserved`() {
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
        assertEquals(
            9,
            repository.getProgress(leaderboardWeek.weekId, "dragons", first)!!.currentCount,
        )
    }

    @Test
    fun `legacy persisted quests use configured first place leaderboard reward`() {
        val current = week.copy(quests = listOf(quest("zombies", 2, 500)))
        val next = week.copy(
            weekId = "2026-W36",
            startsAt = week.endsAt,
            endsAt = Instant.parse("2026-09-07T00:00:00Z"),
        )
        val repository = FakeQuestRepository(current)
        val rewards = RecordingRewardSink()
        val service = QuestService(
            repository,
            rewards,
            fullSetBonusExperience = 0,
            leaderboardWinnerExperience = 5_000,
        )
        val winner = UUID.randomUUID()
        val runnerUp = UUID.randomUUID()
        repository.saveProgress(GuildQuestProgress(current.weekId, "zombies", winner, 20))
        repository.saveProgress(GuildQuestProgress(current.weekId, "zombies", runnerUp, 10))

        service.resetWeeklyQuests(next)

        assertEquals(listOf(winner to 5_000), rewards.experienceAwards)
        assertEquals(5_000, service.leaderboardWinnerReward(current.quests.single()))
    }

    @Test
    fun `completed progress is auto claimed after restart window`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val notifications = RecordingCompletionNotifier()
        val service = QuestService(
            repository,
            rewards,
            fullSetBonusExperience = 0,
            completionNotifier = notifications,
        )
        val guild = UUID.randomUUID()
        repository.saveProgress(
            GuildQuestProgress(
                weekId = week.weekId,
                questId = "zombies",
                guildId = guild,
                currentCount = 2,
                completedAt = Instant.parse("2026-08-25T12:00:00Z"),
            )
        )

        service.reconcilePendingRewards()

        val recovered = repository.getProgress(week.weekId, "zombies", guild)!!
        assertTrue(recovered.claimed)
        assertTrue(recovered.rewardDelivered)
        assertEquals(listOf(guild to 500), rewards.experienceAwards)
        assertEquals(listOf("zombies"), notifications.questIds)
    }

    @Test
    fun `claimed reward is recovered without duplicate XP after delivery marker failure`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val service = QuestService(repository, rewards, fullSetBonusExperience = 0)
        val guild = UUID.randomUUID()
        val actor = UUID.randomUUID()
        repository.saveProgress(GuildQuestProgress(week.weekId, "zombies", guild, 2))
        assertTrue(repository.tryMarkClaimed(week.weekId, "zombies", guild, actor))
        repository.skipNextClaimDeliveryMarker = true

        assertEquals(0, service.reconcilePendingRewards())
        assertEquals(listOf(guild to 500), rewards.experienceAwards)
        assertFalse(repository.getProgress(week.weekId, "zombies", guild)!!.rewardDelivered)

        assertEquals(1, service.reconcilePendingRewards())
        assertEquals(listOf(guild to 500), rewards.experienceAwards)
        assertTrue(repository.getProgress(week.weekId, "zombies", guild)!!.rewardDelivered)
    }

    @Test
    fun `notification persistence failure keeps reward pending and retry does not duplicate XP`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val notifications = FlakyCompletionNotifier()
        val service = QuestService(
            repository,
            rewards,
            fullSetBonusExperience = 0,
            completionNotifier = notifications,
        )
        val guild = UUID.randomUUID()
        val actor = UUID.randomUUID()

        service.incrementProgress(
            guild,
            QuestAction.KILL_MOBS,
            "minecraft:entity/zombie",
            2,
            actorId = actor,
        )

        val pending = repository.getProgress(week.weekId, "zombies", guild)!!
        assertTrue(pending.claimed)
        assertFalse(pending.rewardDelivered)
        assertEquals(listOf(guild to 500), rewards.experienceAwards)
        assertEquals(1, notifications.attempts)

        service.reconcilePendingRewards()

        assertTrue(repository.getProgress(week.weekId, "zombies", guild)!!.rewardDelivered)
        assertEquals(listOf(guild to 500), rewards.experienceAwards)
        assertEquals(2, notifications.attempts)
    }

    @Test
    fun `full set bonus retry reuses transaction after marker failure`() {
        val repository = FakeQuestRepository(week)
        val rewards = RecordingRewardSink()
        val service = QuestService(repository, rewards, fullSetBonusExperience = 2_000)
        val guild = UUID.randomUUID()
        val actor = UUID.randomUUID()
        repository.saveProgress(GuildQuestProgress(week.weekId, "zombies", guild, 2))
        repository.saveProgress(GuildQuestProgress(week.weekId, "skeletons", guild, 1))

        assertTrue(service.claimQuest(actor, guild, "zombies"))
        repository.skipNextBonusMarker = true
        assertTrue(service.claimQuest(actor, guild, "skeletons"))

        assertFalse(repository.isWeeklyBonusAwarded(week.weekId, guild))
        assertEquals(listOf(500, 750, 2_000), rewards.experienceAwards.map { it.second })

        service.reconcilePendingRewards()

        assertTrue(repository.isWeeklyBonusAwarded(week.weekId, guild))
        assertEquals(listOf(500, 750, 2_000), rewards.experienceAwards.map { it.second })
    }

    @Test
    fun `leaderboard payout retry reuses transaction after marker failure`() {
        val leaderboardQuest = quest("dragons", 0, 0).copy(
            leaderboard = true,
            leaderboardPayouts = mapOf(1 to 5_000)
        )
        val current = week.copy(quests = listOf(leaderboardQuest))
        val next = week.copy(
            weekId = "2026-W36",
            startsAt = week.endsAt,
            endsAt = Instant.parse("2026-09-07T00:00:00Z")
        )
        val repository = FakeQuestRepository(current)
        val rewards = RecordingRewardSink()
        val service = QuestService(repository, rewards, fullSetBonusExperience = 0)
        val guild = UUID.randomUUID()
        repository.saveProgress(GuildQuestProgress(current.weekId, "dragons", guild, 9))
        repository.skipNextLeaderboardMarker = true

        assertThrows<IllegalStateException> { service.resetWeeklyQuests(next) }
        assertEquals(current.weekId, repository.getActiveQuestSet()!!.weekId)
        assertEquals(listOf(guild to 5_000), rewards.experienceAwards)

        service.resetWeeklyQuests(next)

        assertEquals(listOf(guild to 5_000), rewards.experienceAwards)
        assertEquals(next.weekId, repository.getActiveQuestSet()!!.weekId)
    }

    @Test
    fun `player placed block cannot satisfy natural only quest`() {
        val naturalQuest = quest("stone", 10, 500).copy(
            action = QuestAction.MINE_BLOCKS,
            target = QuestTarget(
                "minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100,
                provenancePolicy = BlockProvenancePolicy.NATURAL_ONLY
            )
        )
        val repository = FakeQuestRepository(week.copy(quests = listOf(naturalQuest)))
        val service = QuestService(repository, RecordingRewardSink(), fullSetBonusExperience = 0)
        val guild = UUID.randomUUID()

        service.incrementProgress(
            guild, QuestAction.MINE_BLOCKS, "minecraft:block/stone", 1,
            QuestProgressContext(playerPlacedBlock = true)
        )

        assertEquals(0, service.progressFor(guild, "stone").currentCount)
    }

    @Test
    fun `axis corridor conditions require activity inside the configured corridor`() {
        val corridorQuest = quest("stone-axis", 2, 500).copy(
            action = QuestAction.MINE_BLOCKS,
            target = QuestTarget(
                "minecraft:block/stone",
                setOf(QuestAction.MINE_BLOCKS),
                1,
                100,
                supportedConditions = setOf(QuestConditionType.X_WITHIN, QuestConditionType.Z_WITHIN)
            ),
            conditions = listOf(
                QuestCondition(QuestConditionType.X_WITHIN, "0:100"),
                QuestCondition(QuestConditionType.Z_WITHIN, "500:50")
            )
        )
        val repository = FakeQuestRepository(week.copy(quests = listOf(corridorQuest)))
        val service = QuestService(repository, RecordingRewardSink(), fullSetBonusExperience = 0)
        val guild = UUID.randomUUID()

        service.incrementProgress(
            guild, QuestAction.MINE_BLOCKS, "minecraft:block/stone", 1,
            QuestProgressContext(x = 90, z = 525)
        )
        service.incrementProgress(
            guild, QuestAction.MINE_BLOCKS, "minecraft:block/stone", 1,
            QuestProgressContext(x = 101, z = 525)
        )
        service.incrementProgress(
            guild, QuestAction.MINE_BLOCKS, "minecraft:block/stone", 1,
            QuestProgressContext(x = 90, z = 551)
        )

        assertEquals(1, service.progressFor(guild, "stone-axis").currentCount)
    }
    private fun quest(id: String, target: Long, xp: Int): QuestDefinition {
        val targetId = if (id == "skeletons") "minecraft:entity/skeleton" else if (id == "dragons") "minecraft:entity/ender_dragon" else "minecraft:entity/zombie"
        return QuestDefinition(
            id = id,
            nameKey = "quests.$id.name",
            descriptionKey = "quests.$id.description",
            action = QuestAction.KILL_MOBS,
            target = QuestTarget(targetId, setOf(QuestAction.KILL_MOBS), 1, 10_000),
            targetCount = target,
            tier = QuestRewardTier.COMMON,
            experienceReward = xp
        )
    }
}

private class RecordingRewardSink : QuestRewardSink {
    val experienceAwards = mutableListOf<Pair<UUID, Int>>()
    private val experienceTransactions = mutableSetOf<UUID>()

    override fun awardExperience(guildId: UUID, amount: Int, transactionId: UUID): Boolean {
        if (experienceTransactions.add(transactionId)) {
            experienceAwards += guildId to amount
        }
        return true
    }

    override fun awardItems(
        actorId: UUID,
        rewards: List<net.lumalyte.lg.domain.entities.QuestItemReward>,
        transactionId: UUID,
    ): Boolean = true
}

private class RecordingCompletionNotifier : QuestCompletionNotifier {
    val questIds = mutableListOf<String>()

    override fun onCompleted(
        guildId: UUID,
        quest: QuestDefinition,
        progress: GuildQuestProgress,
    ): Boolean {
        questIds += quest.id
        return true
    }
}

private class FlakyCompletionNotifier : QuestCompletionNotifier {
    var attempts = 0
        private set

    override fun onCompleted(
        guildId: UUID,
        quest: QuestDefinition,
        progress: GuildQuestProgress,
    ): Boolean {
        attempts++
        return attempts > 1
    }
}

private class FakeQuestRepository(initial: WeeklyQuestSet?) : QuestRepository {
    private var active = initial
    private val questSets = mutableMapOf<String, WeeklyQuestSet>().apply {
        initial?.let { put(it.weekId, it) }
    }
    private val progress = mutableMapOf<Triple<String, String, UUID>, GuildQuestProgress>()
    private val bonuses = mutableSetOf<Pair<String, UUID>>()
    private val paidLeaderboardRecipients = mutableSetOf<Triple<String, String, UUID>>()

    var skipNextClaimDeliveryMarker = false
    var skipNextLeaderboardMarker = false
    var skipNextBonusMarker = false

    override fun getActiveQuestSet(): WeeklyQuestSet? = active
    override fun getQuestSet(weekId: String): WeeklyQuestSet? = questSets[weekId]
    override fun getRecentQuestSets(limit: Int): List<WeeklyQuestSet> =
        questSets.values.sortedByDescending { it.startsAt }.take(limit)

    override fun saveActiveQuestSet(questSet: WeeklyQuestSet) {
        active = questSet
        questSets[questSet.weekId] = questSet
    }

    override fun deactivateActiveQuestSet() { active = null }

    override fun getProgress(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): GuildQuestProgress? = progress[Triple(weekId, questId, guildId)]

    override fun saveProgress(value: GuildQuestProgress) {
        progress[Triple(value.weekId, value.questId, value.guildId)] = value
    }

    override fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress> =
        progress.values.filter { it.weekId == weekId && it.guildId == guildId }

    override fun getQuestLeaderboard(
        weekId: String,
        questId: String,
        limit: Int,
    ): List<GuildQuestProgress> = progress.values
        .filter { it.weekId == weekId && it.questId == questId }
        .sortedByDescending { it.currentCount }
        .take(limit)

    override fun getClaimedProgress(weekId: String): List<GuildQuestProgress> =
        progress.values.filter { it.weekId == weekId && it.claimed }

    override fun getUnclaimedCompletedProgress(weekId: String): List<GuildQuestProgress> =
        progress.values.filter {
            it.weekId == weekId && !it.claimed && it.completedAt != null
        }

    override fun getPendingClaimRewards(): List<GuildQuestProgress> =
        progress.values.filter { it.claimed && !it.rewardDelivered }

    override fun tryMarkClaimed(
        weekId: String,
        questId: String,
        guildId: UUID,
        actorId: UUID?,
    ): Boolean {
        val key = Triple(weekId, questId, guildId)
        val value = progress[key] ?: return false
        if (value.claimed) return false
        progress[key] = value.withClaimed(actorId).copy(rewardDelivered = false)
        return true
    }

    override fun markClaimRewardDelivered(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): Boolean {
        if (skipNextClaimDeliveryMarker) {
            skipNextClaimDeliveryMarker = false
            return false
        }
        val key = Triple(weekId, questId, guildId)
        val value = progress[key] ?: return false
        progress[key] = value.withRewardDelivered()
        return true
    }

    override fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean {
        if (skipNextBonusMarker) {
            skipNextBonusMarker = false
            return false
        }
        return bonuses.add(weekId to guildId)
    }

    override fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean =
        weekId to guildId in bonuses

    override fun isLeaderboardRecipientPaid(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): Boolean = Triple(weekId, questId, guildId) in paidLeaderboardRecipients

    override fun markLeaderboardRecipientPaid(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): Boolean {
        if (skipNextLeaderboardMarker) {
            skipNextLeaderboardMarker = false
            return false
        }
        paidLeaderboardRecipients += Triple(weekId, questId, guildId)
        return true
    }

    override fun deleteWeekProgress(weekId: String) {
        progress.entries.removeIf { (key, value) ->
            key.first == weekId && !(value.claimed && !value.rewardDelivered)
        }
    }
}

/** Real canonical deposit/withdrawal settlement plus the live path's post-credit event adapter. */
// Shared native contracts use deliberate currency fixtures and separate failure scenarios.
@Suppress("MagicNumber", "TooManyFunctions")
internal abstract class BankQuestContributionContract : BankDepositDeliveryFixture() {

    @Test fun `weekly rotation waits for committed bank contribution recovery`() {
        val (repository, quests, rewards) = bankQuests()
        val original = repository.getActiveQuestSet()!!
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        val next = original.copy(weekId = "following-week")
        assertThrows<IllegalStateException> { repository.saveActiveQuestSet(next) }
        assertEquals(original.weekId, repository.getActiveQuestSet()!!.weekId)
        quests.reconcilePendingRewards()
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
        repository.saveActiveQuestSet(next)
        assertEquals(next.weekId, repository.getActiveQuestSet()!!.weekId)
    }

    @Test fun `reset does not pay leaderboard before pending contribution is recovered`() {
        val (repository, quests, rewards) = bankQuests()
        val active = repository.getActiveQuestSet()!!
        repository.saveActiveQuestSet(active.copy(quests = listOf(active.quests.single().copy(
            leaderboard = true, leaderboardPayouts = mapOf(1 to 200),
        ))))
        val first = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(first))
        quests.incrementBankDeposit(guild, actor, first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(physicalRequest(amount = 4_000)))
        assertThrows<IllegalStateException> { quests.resetWeeklyQuests(active.copy(weekId = "following-week")) }
        assertTrue(rewards.experienceAwards.isEmpty())
        quests.reconcilePendingRewards()
        quests.resetWeeklyQuests(active.copy(weekId = "following-week"))
        assertEquals(listOf(500, 200), rewards.experienceAwards.map { it.second })
    }

    @Test fun `weekly transition fences new deposits before external payment and resumes after restart`() {
        val (repository, _, _) = bankQuests()
        val active = repository.getActiveQuestSet()!!
        assertTrue(repository.beginBankQuestTransition(active.weekId))
        val request = physicalRequest(amount = 100)
        assertEquals(GuildGoldResult.Rejected(net.lumalyte.lg.domain.gold.GuildGoldRejection.WEEKLY_QUEST_TRANSITION),
            service.depositPhysical(request))
        assertEquals(0, scalar("SELECT COUNT(*) FROM guild_gold_operations WHERE transaction_id = ?", request.transactionId.toString()))
        restart()
        val resumed = QuestRepositorySQLite(db)
        assertTrue(resumed.beginBankQuestTransition(active.weekId))
        val next = active.copy(weekId = "following-week")
        resumed.saveActiveQuestSet(next)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(physicalRequest(amount = 100)))
    }

    @Test fun `MariaDB deposit preparation and transition cannot both pass the fence`() {
        assumeTrue(db.dialect == SqlDialect.MARIADB)
        val (repository, _, _) = bankQuests()
        val active = repository.getActiveQuestSet()!!
        val start = java.util.concurrent.CountDownLatch(1)
        val deposit = CompletableFuture.supplyAsync {
            start.await()
            service.depositPhysical(physicalRequest(amount = 100))
        }
        val transition = CompletableFuture.supplyAsync {
            start.await()
            repository.beginBankQuestTransition(active.weekId)
        }
        start.countDown()
        val result = deposit.get(15, TimeUnit.SECONDS)
        val fenced = transition.get(15, TimeUnit.SECONDS)
        if (fenced) {
            assertEquals(GuildGoldResult.Rejected(net.lumalyte.lg.domain.gold.GuildGoldRejection.WEEKLY_QUEST_TRANSITION), result)
        } else {
            assertIs<GuildGoldResult.Applied>(result)
            assertTrue(repository.hasUnresolvedBankContributions(active.weekId))
        }
    }

    @Test fun `deposit retains its original quest identity across replacement`() {
        val (repository, _, _) = bankQuests()
        val original = repository.getActiveQuestSet()!!
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        assertEquals(1, scalar("SELECT COUNT(*) FROM guild_bank_quest_intents WHERE transaction_id = ? AND quest_id = ?", request.transactionId.toString(), "bank"))
        repository.saveActiveQuestSet(original.copy(quests = listOf(original.quests.single().copy(id = "replacement"))))
        restart()
        assertEquals(1, scalar("SELECT COUNT(*) FROM guild_bank_quest_intents WHERE transaction_id = ? AND quest_id = ?", request.transactionId.toString(), "bank"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM guild_bank_quest_intents WHERE transaction_id = ? AND quest_id = ?", request.transactionId.toString(), "replacement"))
    }

    @Test fun `delayed deposit cannot credit replacement quest definition`() {
        val (repository, quests, rewards) = bankQuests()
        val original = repository.getActiveQuestSet()!!
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        // The active definition can be replaced in the same week before event delivery.
        val replacement = original.quests.single().copy(id = "replacement")
        repository.saveActiveQuestSet(original.copy(quests = listOf(replacement)))
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, "replacement").currentCount)
        quests.reconcilePendingRewards()
        assertTrue(repository.pendingBankContributions(1).isEmpty())
        assertTrue(rewards.experienceAwards.isEmpty())
    }

    @Test fun `delayed deposit cannot credit edited definition with same quest id`() {
        val (repository, quests, rewards) = bankQuests()
        val original = repository.getActiveQuestSet()!!
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        val edited = original.quests.single().copy(experienceReward = 900)
        repository.saveActiveQuestSet(original.copy(quests = listOf(edited)))
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, edited.id).currentCount)
        assertTrue(rewards.experienceAwards.isEmpty())
    }

    @Test fun `committed contribution recovers when publication is lost`() {
        val (_, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        // Simulate a stop after settlement and before the post-commit event is delivered.
        restart()
        val recovered = QuestService(QuestRepositorySQLite(db), rewards, fullSetBonusExperience = 0)
        recovered.reconcilePendingRewards()
        assertEquals(5_000, recovered.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `recycled deposit cannot starve the recovery queue`() {
        val (repository, quests, rewards) = bankQuests()
        val first = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(first))
        quests.incrementBankDeposit(guild, actor, first.transactionId)
        assertIs<GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 1_000)))
        val recycled = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(recycled))
        assertEquals(1, repository.pendingBankContributions(128).size)
        quests.reconcilePendingRewards()
        assertEquals(1_000, quests.progressFor(guild, "bank").currentCount)
        assertTrue(rewards.experienceAwards.isEmpty())
        assertTrue(repository.pendingBankContributions(128).isEmpty())
    }

    @Test fun `bounded recovery drains backlog over multiple passes`() {
        val (repository, quests, _) = bankQuests()
        repeat(129) { assertIs<GuildGoldResult.Applied>(service.depositPhysical(physicalRequest(amount = 1))) }
        quests.reconcilePendingRewards()
        assertEquals(128, quests.progressFor(guild, "bank").currentCount)
        assertEquals(1, repository.pendingBankContributions(128).size)
        quests.reconcilePendingRewards()
        assertEquals(129, quests.progressFor(guild, "bank").currentCount)
        assertTrue(repository.pendingBankContributions(1).isEmpty())
    }

    @Test fun `fresh deposits without a chapter count once and pay the quest once`() {
        execute("DELETE FROM chapter_lifecycle")
        val (_, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(5_000, quests.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `chapterless deposit cannot count after a chapter begins`() {
        execute("DELETE FROM chapter_lifecycle")
        val (_, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        execute("INSERT INTO chapter_lifecycle (chapter_id, phase, starts_at, ends_at) VALUES (?, 'SCHEDULED', ?, ?)",
            "new-chapter", occurredAt.minusSeconds(1).toEpochMilli(), occurredAt.plusSeconds(604_800).toEpochMilli())
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        assertTrue(rewards.experienceAwards.isEmpty())
    }

    @Test fun `chapter deposit cannot count after the chapter fence disappears`() {
        val (_, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        execute("DELETE FROM chapter_lifecycle")
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        assertTrue(rewards.experienceAwards.isEmpty())
    }

    @Test
    fun `withdrawn raw gold cannot complete a quest through redeposit`() {
        val (_, quests, rewards) = bankQuests()
        val listener = bankListener(quests)
        val first = contributeViaEvent(listener, 2_500, 25)
        assertEquals(2_500, quests.progressFor(guild, "bank").currentCount)
        verifyDuplicateEvents(listener, quests, first)
        assertIs<GuildGoldResult.Applied>(
            service.withdrawPersonal(personalRequest(amount = 2_500)),
        )
        contributeViaEvent(listener, 2_500, 0)
        assertTrue(rewards.experienceAwards.isEmpty(), "Recycled input must not award the 500 XP quest")
        assertEquals(2_500, quests.progressFor(guild, "bank").currentCount)
        restart()
        verifyFreshQuestReward(rewards, first)
    }

    private fun bankListener(quests: QuestService): QuestProgressListener {
        return QuestProgressListener(
            quests,
            mockk<MemberService>(relaxed = true),
            mockk<BlockProvenanceRepository>(relaxed = true),
            BlockProvenanceOperationQueue(Executor { it.run() }),
        )
    }

    private fun contributeViaEvent(
        listener: QuestProgressListener,
        amount: Long,
        expectedXp: Int,
    ): UUID {
        val request = physicalRequest(amount = amount)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        val completion = delivery.complete(request.transactionId)!!
        assertEquals(expectedXp, completion.acceptedXp)
        if (expectedXp == 0) assertEquals(BankDepositXpStatus.NO_UNITS, completion.status)
        listener.onBankContribution(GuildBankContributionEvent(guild, actor, request.transactionId))
        return request.transactionId
    }

    private fun verifyDuplicateEvents(
        listener: QuestProgressListener,
        quests: QuestService,
        first: UUID,
    ) {
        listener.onBankContribution(GuildBankContributionEvent(guild, actor, first))
        assertTrue(listener.javaClass.declaredMethods.none { method ->
            method.getAnnotation(EventHandler::class.java) != null &&
                method.parameterTypes.contains(GuildBankDepositEvent::class.java)
        })
        quests.incrementProgress(guild, QuestAction.DEPOSIT_BANK, "lumaguilds:bank/coins", Long.MAX_VALUE)
        assertEquals(2_500, quests.progressFor(guild, "bank").currentCount)
    }

    private fun verifyFreshQuestReward(rewards: RecordingRewardSink, first: UUID) {
        val fresh = physicalRequest(amount = 2_500)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(fresh))
        val reopened = QuestRepositorySQLite(db)
        val resumed = QuestService(reopened, rewards, fullSetBonusExperience = 0)
        resumed.incrementBankDeposit(guild, actor, fresh.transactionId)
        resumed.incrementBankDeposit(guild, actor, fresh.transactionId)
        resumed.incrementBankDeposit(guild, actor, first)
        assertEquals(5_000, resumed.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `canonical identity week and run fences reject stale or fabricated quest input`() {
        val (repository, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        quests.incrementBankDeposit(guild, UUID.randomUUID(), request.transactionId)
        quests.incrementBankDeposit(UUID.randomUUID(), actor, request.transactionId)
        quests.incrementBankDeposit(guild, actor, UUID.randomUUID())
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        val original = repository.getActiveQuestSet()!!
        // Model an explicitly closed recovery item to exercise the old-run fence below.
        repository.finishBankContributionRecovery(request.transactionId)
        repository.saveActiveQuestSet(
            original.copy(
                weekId = "next",
                startsAt = occurredAt.plusSeconds(604_800),
                endsAt = occurredAt.plusSeconds(1_209_600),
            ),
        )
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        repository.saveActiveQuestSet(original)
        execute("UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guild.toString())
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        assertTrue(rewards.experienceAwards.isEmpty())
    }

    @Test fun `quest amount is fresh contribution independent of ordinary XP denial or cap`() {
        val (_, quests, rewards) = bankQuests()
        quote = quote.copy(eligibility = BankDepositXpEligibility.AFK)
        val request = physicalRequest(amount = 5_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        assertEquals(BankDepositXpStatus.AFK, delivery.complete(request.transactionId)!!.status)
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(5_000, quests.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `concurrent contribution progress and replay have no lost or repeated increments`() {
        val (_, quests, _) = bankQuests()
        val ids = (1..4).map {
            val request = physicalRequest(amount = 1_000)
            assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
            request.transactionId
        }
        val tasks = (ids + ids).map { id -> CompletableFuture.runAsync {
            quests.incrementBankDeposit(guild, actor, id)
        } }
        tasks.forEach { it.get(15, TimeUnit.SECONDS) }
        assertEquals(4000, quests.progressFor(guild, "bank").currentCount)
    }

    @Test fun `progress failure rolls back consumption marker so supported retry counts once`() {
        val (_, quests, _) = bankQuests()
        val request = physicalRequest(amount = 1_000)
        assertIs<GuildGoldResult.Applied>(service.depositPhysical(request))
        rejectInsert("guild_quest_progress")
        org.junit.jupiter.api.assertThrows<java.sql.SQLException> {
            quests.incrementBankDeposit(guild, actor, request.transactionId)
        }
        assertEquals(0, scalar("SELECT COUNT(*) FROM guild_bank_contribution_quests"))
        allowDeliveryWrites()
        restart()
        val resumed = QuestService(QuestRepositorySQLite(db), RecordingRewardSink(), 0)
        resumed.incrementBankDeposit(guild, actor, request.transactionId)
        resumed.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(1_000, resumed.progressFor(guild, "bank").currentCount)
    }

    private fun bankQuests(): Triple<QuestRepositorySQLite, QuestService, RecordingRewardSink> {
        db.connection.connection.use { QuestSchema.create(it, db.dialect == SqlDialect.MARIADB) }
        val definition = QuestDefinition(
            id = "bank", nameKey = "bank.name", descriptionKey = "bank.description", action = QuestAction.DEPOSIT_BANK,
            target = QuestTarget("lumaguilds:bank/coins", setOf(QuestAction.DEPOSIT_BANK), 1, 100_000),
            targetCount = 5_000, tier = QuestRewardTier.COMMON, experienceReward = 500,
        )
        val week =
            WeeklyQuestSet("2026-W43", occurredAt.minusSeconds(86_400), occurredAt.plusSeconds(604_800), listOf(definition))
        val repository = QuestRepositorySQLite(db)
        repository.saveActiveQuestSet(week)
        val rewards = RecordingRewardSink()
        return Triple(repository, QuestService(repository, rewards, fullSetBonusExperience = 0), rewards)
    }
}

internal class BankQuestRecyclingProbeTest : BankQuestContributionContract() {
    @TempDir lateinit var directory: Path
    override fun newStorage() = VirtualThreadSQLiteStorage(directory.toFile())
}

internal class BankQuestContributionMariaDbTest : BankQuestContributionContract() {
    override fun newStorage(): MariaDBStorage {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB XP test instance not configured")
        return MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
    }
}
