package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
abstract class BankQuestContributionContract : net.lumalyte.lg.infrastructure.persistence.guilds.BankDepositDeliveryFixture() {

    @Test
    fun `withdrawn raw gold cannot complete a quest through redeposit`() {
        val bank = QuestDefinition(
            id = "bank", nameKey = "bank.name", descriptionKey = "bank.description",
            action = QuestAction.DEPOSIT_BANK,
            target = QuestTarget("lumaguilds:bank/coins", setOf(QuestAction.DEPOSIT_BANK), 1, 100000),
            targetCount = 5000, tier = QuestRewardTier.COMMON, experienceReward = 500,
        )
        val week = WeeklyQuestSet("2026-W43", occurredAt.minusSeconds(86400), occurredAt.plusSeconds(604800), listOf(bank))
        db.connection.connection.use { net.lumalyte.lg.infrastructure.persistence.migrations.QuestSchema.create(it, db.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB) }
        val repository = net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite(db)
        repository.saveActiveQuestSet(week)
        val rewards = RecordingRewardSink()
        val quests = QuestService(repository, rewards, fullSetBonusExperience = 0)
        val listener = net.lumalyte.lg.infrastructure.listeners.QuestProgressListener(
            quests,
            io.mockk.mockk<MemberService>(relaxed = true),
            io.mockk.mockk<net.lumalyte.lg.application.persistence.BlockProvenanceRepository>(relaxed = true),
            net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue(java.util.concurrent.Executor { it.run() }),
        )
        val first = physicalRequest(amount = 2500)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(first))
        assertEquals(25, delivery.complete(first.transactionId)!!.acceptedXp)
        listener.onBankContribution(net.lumalyte.lg.api.events.GuildBankContributionEvent(guild, actor, first.transactionId))
        assertEquals(2500, quests.progressFor(guild, "bank").currentCount)
        // Duplicate and legacy gross notifications cannot inflate the leaderboard.
        listener.onBankContribution(net.lumalyte.lg.api.events.GuildBankContributionEvent(guild, actor, first.transactionId))
        assertTrue(listener.javaClass.declaredMethods.none { method ->
            method.getAnnotation(org.bukkit.event.EventHandler::class.java) != null &&
                method.parameterTypes.contains(net.lumalyte.lg.api.events.GuildBankDepositEvent::class.java)
        })
        quests.incrementProgress(guild, QuestAction.DEPOSIT_BANK, "lumaguilds:bank/coins", Long.MAX_VALUE)
        assertEquals(2500, quests.progressFor(guild, "bank").currentCount)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.withdrawPersonal(personalRequest(amount = 2500)))
        val recycled = physicalRequest(amount = 2500)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(recycled))
        assertEquals(net.lumalyte.lg.domain.entities.BankDepositXpStatus.NO_UNITS, delivery.complete(recycled.transactionId)!!.status)
        // BankService emits this gross post-credit event after each successful deposit.
        listener.onBankContribution(net.lumalyte.lg.api.events.GuildBankContributionEvent(guild, actor, recycled.transactionId))
        assertTrue(rewards.experienceAwards.isEmpty(), "Recycled input must not award the 500 XP quest")
        assertEquals(2500, quests.progressFor(guild, "bank").currentCount)
        restart()
        val fresh = physicalRequest(amount = 2500)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(fresh))
        val reopened = net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite(db)
        val resumed = QuestService(reopened, rewards, fullSetBonusExperience = 0)
        resumed.incrementBankDeposit(guild, actor, fresh.transactionId)
        resumed.incrementBankDeposit(guild, actor, fresh.transactionId)
        resumed.incrementBankDeposit(guild, actor, first.transactionId)
        assertEquals(5000, resumed.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `canonical identity week and run fences reject stale or fabricated quest input`() {
        val (repository, quests, rewards) = bankQuests()
        val request = physicalRequest(amount = 5000)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(request))
        quests.incrementBankDeposit(guild, UUID.randomUUID(), request.transactionId)
        quests.incrementBankDeposit(UUID.randomUUID(), actor, request.transactionId)
        quests.incrementBankDeposit(guild, actor, UUID.randomUUID())
        assertEquals(0, quests.progressFor(guild, "bank").currentCount)
        val original = repository.getActiveQuestSet()!!
        repository.saveActiveQuestSet(original.copy(weekId = "next", startsAt = occurredAt.plusSeconds(604800), endsAt = occurredAt.plusSeconds(1209600)))
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
        quote = quote.copy(eligibility = net.lumalyte.lg.domain.entities.BankDepositXpEligibility.AFK)
        val request = physicalRequest(amount = 5000)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(request))
        assertEquals(net.lumalyte.lg.domain.entities.BankDepositXpStatus.AFK, delivery.complete(request.transactionId)!!.status)
        quests.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(5000, quests.progressFor(guild, "bank").currentCount)
        assertEquals(listOf(500), rewards.experienceAwards.map { it.second })
    }

    @Test fun `concurrent contribution progress and replay have no lost or repeated increments`() {
        val (_, quests, _) = bankQuests()
        val ids = (1..4).map {
            val request = physicalRequest(amount = 1000)
            kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(request))
            request.transactionId
        }
        val tasks = (ids + ids).map { id -> java.util.concurrent.CompletableFuture.runAsync {
            quests.incrementBankDeposit(guild, actor, id)
        } }
        tasks.forEach { it.get(15, java.util.concurrent.TimeUnit.SECONDS) }
        assertEquals(4000, quests.progressFor(guild, "bank").currentCount)
    }

    @Test fun `progress failure rolls back consumption marker so supported retry counts once`() {
        val (_, quests, _) = bankQuests()
        val request = physicalRequest(amount = 1000)
        kotlin.test.assertIs<net.lumalyte.lg.domain.gold.GuildGoldResult.Applied>(service.depositPhysical(request))
        rejectInsert("guild_quest_progress")
        org.junit.jupiter.api.assertThrows<java.sql.SQLException> { quests.incrementBankDeposit(guild, actor, request.transactionId) }
        assertEquals(0, scalar("SELECT COUNT(*) FROM guild_bank_contribution_quests"))
        allowDeliveryWrites()
        restart()
        val resumed = QuestService(net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite(db), RecordingRewardSink(), 0)
        resumed.incrementBankDeposit(guild, actor, request.transactionId)
        resumed.incrementBankDeposit(guild, actor, request.transactionId)
        assertEquals(1000, resumed.progressFor(guild, "bank").currentCount)
    }

    private fun bankQuests(): Triple<net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite, QuestService, RecordingRewardSink> {
        db.connection.connection.use { net.lumalyte.lg.infrastructure.persistence.migrations.QuestSchema.create(it, db.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB) }
        val definition = QuestDefinition(
            id = "bank", nameKey = "bank.name", descriptionKey = "bank.description", action = QuestAction.DEPOSIT_BANK,
            target = QuestTarget("lumaguilds:bank/coins", setOf(QuestAction.DEPOSIT_BANK), 1, 100000),
            targetCount = 5000, tier = QuestRewardTier.COMMON, experienceReward = 500,
        )
        val week = WeeklyQuestSet("2026-W43", occurredAt.minusSeconds(86400), occurredAt.plusSeconds(604800), listOf(definition))
        val repository = net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite(db)
        repository.saveActiveQuestSet(week)
        val rewards = RecordingRewardSink()
        return Triple(repository, QuestService(repository, rewards, fullSetBonusExperience = 0), rewards)
    }
}

class BankQuestRecyclingProbeTest : BankQuestContributionContract() {
    @org.junit.jupiter.api.io.TempDir lateinit var directory: java.nio.file.Path
    override fun newStorage() = net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage(directory.toFile())
}

class BankQuestContributionMariaDbTest : BankQuestContributionContract() {
    override fun newStorage(): net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        org.junit.jupiter.api.Assumptions.assumeTrue(port != null, "Disposable loopback MariaDB XP test instance not configured")
        return net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
    }
}
