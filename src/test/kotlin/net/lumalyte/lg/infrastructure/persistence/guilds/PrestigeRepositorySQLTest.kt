package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.PrestigeConfig
import net.lumalyte.lg.config.ProgressionConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.time.Instant
import java.sql.Timestamp
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.util.UUID
import kotlin.test.*

class PrestigeRepositorySQLTest : RewardSqlTestFixture() {
    private val guildId = UUID.randomUUID()
    private val enemyGuildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()
    private val coordinator = GuildActionCoordinator()
    private val settings = MainConfig().apply {
        progression = ProgressionConfig(
            maxLevel = 100,
            prestige = PrestigeConfig(enabled = true, maxCount = 3, fees = listOf(10_000L, 20_000L, 30_000L)),
        )
        bank.maxBankBalance = 100_000
    }

    private data class Subject(
        val storage: Storage<Database>,
        val owners: RewardOwnershipRepositorySQL,
        val gold: GuildGoldRepositorySQL,
        val wars: WarRepositorySQL,
        val repository: PrestigeRepositorySQL,
    )

    private fun open(seed: Boolean = true): Subject {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val owners = RewardOwnershipRepositorySQL(storage, catalog)
        val gold = GuildGoldRepositorySQL(storage)
        val wars = WarRepositorySQL(storage)
        if (seed) {
            GuildRepositorySQLite(storage).add(Guild(guildId, "Prestige", level = 100, createdAt = Instant.now()))
            GuildRepositorySQLite(storage).add(Guild(enemyGuildId, "Enemy", level = 100, createdAt = Instant.now()))
            val initialized = assertIs<RewardOwnershipWrite.Saved>(owners.initialize(guildId, 6))
            assertIs<RewardOwnershipWrite.Saved>(
                owners.save(
                    guildId,
                    initialized.snapshot.version,
                    initialized.snapshot.ownership.copy(currentRun = setOf("bank-1")),
                )
            )
            storage.connection.executeUpdate(
                """INSERT INTO guild_progression
                   (guild_id, total_experience, current_level, experience_this_level,
                    experience_for_next_level, total_level_ups, unlocked_perks, created_at, last_updated)
                   VALUES (?, 500000, 100, 0, 0, 99, '[]', ?, ?)""",
                guildId.toString(), progressionTimestamp(storage), progressionTimestamp(storage),
            )
            gold.apply(
                GuildGoldMutation(
                    UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
                    GuildGoldDirection.CREDIT, 19_000, 0, "prestige test seed",
                ),
                100_000,
                null,
            )
        }
        return Subject(
            storage,
            owners,
            gold,
            wars,
            PrestigeRepositorySQL(storage, catalog, owners, gold, coordinator) { settings },
        )
    }

    private fun progressionTimestamp(storage: Storage<Database>): Any =
        if (storage.dialect == SqlDialect.MARIADB) Timestamp.from(Instant.now()) else Instant.now().toEpochMilli()

    private fun quote(id: UUID = UUID.randomUUID()) = PrestigeQuote(
        transactionId = id,
        guildId = guildId,
        actorId = actorId,
        retainedRewardId = "bank-1",
        quotedFee = 10_000,
        expectedOwnershipVersion = 1,
        expectedPrestigeCount = 0,
    )

    private fun Subject.snapshot() =
        assertIs<RewardOwnershipRead.Found>(owners.read(guildId)).snapshot

    @Test
    fun `only new committed confirmation permits public announcement after reopen`() {
        val subject = open()
        val request = quote()
        val first = subject.repository.confirmWithCommitStatus(request) { null }
        assertIs<PrestigeResult.Applied>(first.result)
        assertTrue(first.newlyCommitted)
        assertFalse(subject.repository.confirmWithCommitStatus(request) { null }.newlyCommitted)
        closeStorage(subject.storage)
        val reopened = open(seed = false)
        val replay = reopened.repository.confirmWithCommitStatus(request) { null }
        assertEquals(first.result, replay.result)
        assertFalse(replay.newlyCommitted)
    }

    @Test
    fun `concurrent confirmations permit a single announcement and charge`() {
        val subject = open()
        val request = quote()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val calls = (1..2).map {
                executor.submit<net.lumalyte.lg.application.persistence.PrestigeConfirmation> {
                    check(start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    subject.repository.confirmWithCommitStatus(request) { null }
                }
            }
            start.countDown()
            val results = calls.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            assertTrue(results.all { it.result is PrestigeResult.Applied })
            assertEquals(1, results.count { it.newlyCommitted })
            assertEquals(9_000L, subject.gold.getBalance(guildId))
            assertEquals(1, subject.snapshot().ownership.prestigeCount)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `prestige preserves purchased homes locations paid activations and credits after reopen`() {
        val subject = open()
        val worldId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID()
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
            guildId.toString(), "workshop", worldId, 12, 72, -34,
        )
        val activations = GuildHomeActivationRepositorySQL(subject.storage)
        assertTrue(activations.activate(guildId, "workshop", transactionId))
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_home_activation_credits (guild_id, credits) VALUES (?, ?)", guildId.toString(), 2,
        )
        assertIs<PrestigeResult.Applied>(subject.repository.confirm(quote()) { null })
        closeStorage(subject.storage)

        val reopened = open(seed = false)
        val home = assertNotNull(reopened.storage.connection.getFirstRow(
            "SELECT world_id, x, y, z FROM guild_homes WHERE guild_id = ? AND name = ?", guildId.toString(), "workshop",
        ))
        assertEquals(worldId, home.getString("world_id"))
        assertEquals(12, home.getInt("x"))
        assertEquals(72, home.getInt("y"))
        assertEquals(-34, home.getInt("z"))
        val restored = GuildHomeActivationRepositorySQL(reopened.storage)
        assertTrue(restored.isActive(guildId, "workshop"))
        assertEquals(1, restored.activeCount(guildId))
        assertEquals(2, restored.availableCredits(guildId))
        assertEquals(transactionId.toString(), reopened.storage.connection.getFirstRow(
            "SELECT transaction_id FROM guild_home_activations WHERE guild_id = ? AND home_name = ?",
            guildId.toString(), "workshop",
        )!!.getString("transaction_id"))
        val ownership = reopened.snapshot().ownership
        assertEquals(6, ownership.initialHomeCapacity)
        assertEquals(7, RewardEntitlementResolver(catalog).resolve(
            1, ownership, settings.bank.maxBankBalance.toLong(), settings.guild.maxMembersPerGuild,
        ).homeCapacity)
    }

    @Test
    fun `fifteen saved legacy and outpost homes survive prestige without granting unearned slots`() {
        val subject = open()
        val snapshot = subject.snapshot()
        val outposts = (1..9).map { "home-$it" }.toSet()
        val purchased = assertIs<RewardOwnershipWrite.Saved>(subject.owners.save(
            guildId,
            snapshot.version,
            snapshot.ownership.copy(permanent = outposts),
        ))
        val worldId = UUID.randomUUID().toString()
        (1..15).forEach { index ->
            subject.storage.connection.executeUpdate(
                "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
                guildId.toString(), "legacy-$index", worldId, index, 70, index,
            )
        }

        val request = quote().copy(expectedOwnershipVersion = purchased.snapshot.version)
        val applied = assertIs<PrestigeResult.Applied>(subject.repository.confirm(request) { null })
        assertEquals(7, applied.homeCapacity) // Six earned base + one free prestige; historical Outposts grant none.
        closeStorage(subject.storage)

        val reopened = open(seed = false)
        val saved = reopened.storage.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM guild_homes WHERE guild_id = ?", guildId.toString(),
        )!!
        assertEquals(15, saved.getInt("n")) // Preserve over-cap records; never truncate or delete.
        assertEquals(15, assertNotNull(GuildRepositorySQLite(reopened.storage).getById(guildId)).homes.size)
        val ownership = reopened.snapshot().ownership
        assertEquals(6, ownership.initialHomeCapacity) // Retained only as migration evidence.
        assertEquals(1, ownership.prestigeCount)
        assertEquals(7, RewardEntitlementResolver(catalog).resolve(1, ownership).homeCapacity)
    }

    @Test
    fun `committed prestige receipt replays after reopen without a second charge`() {
        val subject = open()
        val request = quote()
        val first = assertIs<PrestigeResult.Applied>(subject.repository.confirm(request) { null })
        closeStorage(subject.storage)
        val reopened = open(seed = false)
        assertEquals(first, reopened.repository.confirm(request) { PrestigeRejection.UNAUTHORIZED })
        assertEquals(9_000L, reopened.gold.getBalance(guildId))
        assertEquals(1, reopened.snapshot().ownership.prestigeCount)
        assertEquals(1, reopened.storage.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM guild_prestige_transactions WHERE guild_id = ?", guildId.toString(),
        )!!.getInt("n"))
    }

    @Test
    fun `historical prestige receipt capacity remains immutable while live entitlements use current rule`() {
        val subject = open()
        val request = quote()
        assertIs<PrestigeResult.Applied>(subject.repository.confirm(request) { null })
        // Model the old one-base-slot formula: 1 base + 1 prestige = 2.
        // The original stored receipt must replay verbatim after the new six-base rule.
        subject.storage.connection.executeUpdate(
            "UPDATE guild_prestige_transactions SET home_capacity = ? WHERE transaction_id = ?",
            2, request.transactionId.toString(),
        )
        closeStorage(subject.storage)

        val reopened = open(seed = false)
        val replay = assertIs<PrestigeResult.Applied>(reopened.repository.confirm(request) { null })
        assertEquals(2, replay.homeCapacity) // Immutable historical audit snapshot.
        val current = RewardEntitlementResolver(catalog).resolve(1, reopened.snapshot().ownership)
        assertEquals(7, current.homeCapacity) // Live UI re-evaluates base and active grants.
        assertEquals(9_000L, reopened.gold.getBalance(guildId)) // No replay charge.
    }

    @Test
    fun `prestige atomically charges resets current run and retains exactly one selected perk`() {
        val subject = open()
        val result = assertIs<PrestigeResult.Applied>(subject.repository.confirm(quote()) { null })

        assertEquals(9_000L, result.newBalance)
        assertEquals(1, result.prestigeCount)
        assertEquals(7, result.homeCapacity)
        assertEquals(9_000L, subject.gold.getBalance(guildId))

        val ownership = subject.snapshot().ownership
        assertEquals(1, ownership.prestigeCount)
        assertEquals(setOf("bank-1"), ownership.permanent)
        assertTrue(ownership.currentRun.isEmpty())

        val progression = subject.storage.connection.getFirstRow(
            "SELECT * FROM guild_progression WHERE guild_id = ?", guildId.toString()
        )!!
        assertEquals(1, progression.getInt("current_level"))
        assertEquals(0, progression.getInt("total_experience"))
        assertEquals(0, progression.getInt("experience_this_level"))
        assertEquals("[]", progression.getString("unlocked_perks"))
        assertEquals(99, progression.getInt("total_level_ups"))
        assertEquals(
            1,
            subject.storage.connection.getFirstRow("SELECT level FROM guilds WHERE id = ?", guildId.toString())!!.getInt("level")
        )
    }

    @Test
    fun `carry-over starts the next climb with XP earned past level 100`() {
        val curve = net.lumalyte.lg.domain.values.ProgressionCurve.from(settings.progression)
        val landing = curve.totalExperienceForLevel(3)
        val original = settings.progression
        settings.progression = original.copy(prestige = original.prestige.copy(
            carryOver = net.lumalyte.lg.config.PrestigeCarryOverConfig(enabled = true, landingLevel = 3),
        ))
        val subject = open()
        subject.storage.connection.executeUpdate(
            "UPDATE guild_progression SET total_experience = ? WHERE guild_id = ?",
            curve.totalExperienceForLevel(100) + landing + 500, guildId.toString(),
        )
        val result = assertIs<PrestigeResult.Applied>(subject.repository.confirm(quote()) { null })

        assertEquals(landing, result.carriedExperience)
        assertEquals(3, result.postLevel)
        val progression = subject.storage.connection.getFirstRow(
            "SELECT * FROM guild_progression WHERE guild_id = ?", guildId.toString()
        )!!
        assertEquals(landing, progression.getInt("total_experience"))
        assertEquals(3, progression.getInt("current_level"))
        assertEquals(curve.experienceForNextLevel(3), progression.getInt("experience_for_next_level"))
        assertEquals(
            3,
            subject.storage.connection.getFirstRow("SELECT level FROM guilds WHERE id = ?", guildId.toString())!!.getInt("level"),
        )
        settings.progression = original
    }

    @Test
    fun `identical confirmation replays receipt without double charge or second prestige`() {
        val subject = open()
        val request = quote()
        val first = assertIs<PrestigeResult.Applied>(subject.repository.confirm(request) { null })
        val second = subject.repository.confirm(request) { PrestigeRejection.UNAUTHORIZED }

        assertEquals(first, second)
        assertEquals(9_000L, subject.gold.getBalance(guildId))
        assertEquals(1, subject.snapshot().ownership.prestigeCount)
        assertEquals(1, subject.storage.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM guild_prestige_transactions"
        )!!.getInt("n"))
    }

    @Test
    fun `active war blocks prestige without charging or mutating ownership`() {
        val subject = open()
        val warId = UUID.randomUUID()
        assertTrue(
            subject.wars.save(
                DurableWarRecord(
                    warId,
                    war = War(
                        id = warId,
                        declaringGuildId = guildId,
                        defendingGuildId = enemyGuildId,
                        status = WarStatus.ACTIVE,
                        startedAt = Instant.now(),
                    ),
                )
            )
        )

        assertEquals(
            PrestigeResult.Rejected(PrestigeRejection.ACTIVE_WAR),
            subject.repository.confirm(quote()) { null },
        )
        assertEquals(19_000L, subject.gold.getBalance(guildId))
        assertEquals(0, subject.snapshot().ownership.prestigeCount)
        assertEquals(100, subject.storage.connection.getFirstRow(
            "SELECT current_level FROM guild_progression WHERE guild_id = ?", guildId.toString()
        )!!.getInt("current_level"))
    }

    @Test
    fun `prestige above post prestige capacity keeps overflow withdraw only`() {
        val subject = open()
        val ownership = subject.snapshot().ownership
        val next = ownership.copy(
            currentRun = emptySet(),
            permanent = ownership.permanent + "bank-1",
            prestigeCount = 1,
        )
        val postCapacity = RewardEntitlementResolver(catalog).resolve(
            1, next, settings.bank.maxBankBalance.toLong(), settings.guild.maxMembersPerGuild
        ).bankCapacity
        val targetBalance = postCapacity + 10_000L + 1
        val extra = targetBalance - subject.gold.getBalance(guildId)
        if (extra > 0) {
            subject.gold.apply(
                GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
                    GuildGoldDirection.CREDIT, extra, 0, "capacity guard seed"),
                1_000_000,
                null,
            )
        }

        // REQ-157: the prestige applies and the whole post-fee balance is kept.
        val result = assertIs<PrestigeResult.Applied>(subject.repository.confirm(quote()) { null })
        val kept = targetBalance - 10_000L
        assertEquals(kept, result.newBalance)
        assertEquals(postCapacity, result.postBankCapacity)
        assertEquals(kept, subject.gold.getBalance(guildId))
        assertEquals(1, subject.snapshot().ownership.prestigeCount)

        // Credits are refused while the balance is above capacity.
        assertEquals(
            GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED),
            subject.gold.apply(
                GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.PERSONAL_ACCOUNT,
                    GuildGoldDirection.CREDIT, 1, 0, "overflow deposit"),
                postCapacity,
                null,
            ),
        )
        assertEquals(kept, subject.gold.getBalance(guildId))

        // Debits are allowed.
        val withdrawal = GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.PERSONAL_ACCOUNT,
            GuildGoldDirection.DEBIT, 500, 0, "overflow withdrawal")
        assertIs<GuildGoldResult.Applied>(subject.gold.apply(withdrawal, postCapacity, 0L))
        assertEquals(kept - 500, subject.gold.getBalance(guildId))

        // A refund of that withdrawal restores the pre-withdrawal balance, still above capacity.
        val refund = GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.PERSONAL_ACCOUNT,
            GuildGoldDirection.CREDIT, 500, 0, "overflow refund")
        assertIs<GuildGoldResult.Applied>(
            subject.gold.compensateDebit(withdrawal.transactionId, refund, postCapacity, 0L, "refund")
        )
        assertEquals(kept, subject.gold.getBalance(guildId))
    }

    @Test
    fun `receipt failure rolls back gold ownership and progression`() {
        val subject = open()
        rejectInserts(subject.storage, "guild_prestige_transactions")
        val request = quote()

        val confirmation = subject.repository.confirmWithCommitStatus(request) { null }
        assertIs<PrestigeResult.Failed>(confirmation.result)
        assertFalse(confirmation.newlyCommitted)
        assertEquals(19_000L, subject.gold.getBalance(guildId))
        assertEquals(0, subject.snapshot().ownership.prestigeCount)
        assertEquals(100, subject.storage.connection.getFirstRow(
            "SELECT current_level FROM guild_progression WHERE guild_id = ?", guildId.toString()
        )!!.getInt("current_level"))
        assertNull(subject.gold.findOperation(request.transactionId))
    }

    @Test
    fun `stale selection authorization and disabled gate reject without charge`() {
        val subject = open()
        assertEquals(
            PrestigeResult.Rejected(PrestigeRejection.UNAUTHORIZED),
            subject.repository.confirm(quote()) { PrestigeRejection.UNAUTHORIZED },
        )
        assertEquals(19_000L, subject.gold.getBalance(guildId))

        settings.progression = ProgressionConfig(
            prestige = PrestigeConfig(enabled = false, maxCount = 3, fees = listOf(10_000L, 20_000L, 30_000L))
        )
        assertEquals(
            PrestigeResult.Rejected(PrestigeRejection.UNAVAILABLE),
            subject.repository.confirm(quote()) { null },
        )
        assertEquals(19_000L, subject.gold.getBalance(guildId))
    }

    // REQ-152: receipts joined to the canonical gold journal, no writes.
    private fun Subject.insertAppliedReceipt(
        guild: UUID,
        tier: Int,
        createdAt: Long,
        transactionId: UUID = UUID.randomUUID(),
        actor: UUID = actorId,
        retained: String = "bank-1",
    ): UUID {
        storage.connection.executeUpdate(
            """INSERT INTO guild_gold_operations
               (transaction_id, guild_id, actor_id, route, direction, amount, fee, status, description, created_at)
               VALUES (?, ?, ?, 'SYSTEM', 'DEBIT', 10000, 0, 'APPLIED', 'legacy prestige', ?)""",
            transactionId.toString(), guild.toString(), actor.toString(), createdAt,
        )
        storage.connection.executeUpdate(
            """INSERT INTO guild_prestige_transactions
               (transaction_id, guild_id, actor_id, retained_reward_id, quoted_fee,
                expected_version, expected_prestige_count, outcome, rejection,
                old_balance, new_balance, ownership_version, prestige_count,
                post_bank_capacity, home_capacity)
               VALUES (?, ?, ?, ?, 10000, 1, ?, 'APPLIED', NULL, 20000, 10000, 2, ?, 10000, 2)""",
            transactionId.toString(), guild.toString(), actor.toString(), retained, tier - 1, tier,
        )
        return transactionId
    }

    @Test
    fun `history lists an applied prestige with its journal commit time and omits rejections`() {
        val subject = open()
        val before = Instant.now().toEpochMilli()
        val request = quote()
        assertIs<PrestigeResult.Applied>(subject.repository.confirm(request) { null })
        val after = Instant.now().toEpochMilli()
        assertIs<PrestigeResult.Rejected>(subject.repository.confirm(quote()) { null })

        val history = subject.repository.history(guildId)
        assertEquals(1, history.size)
        val record = history.single()
        assertEquals(request.transactionId, record.transactionId)
        assertEquals(guildId, record.guildId)
        assertEquals(actorId, record.actorId)
        assertEquals(1, record.tier)
        assertEquals("bank-1", record.retainedRewardId)
        val journal = subject.storage.connection.getFirstColumn<Number>(
            "SELECT created_at FROM guild_gold_operations WHERE transaction_id = ?",
            request.transactionId.toString(),
        ).toLong()
        assertEquals(journal, record.appliedAt.toEpochMilli())
        assertTrue(record.appliedAt.toEpochMilli() in before..after)
        assertEquals(emptyList(), subject.repository.history(enemyGuildId))
    }

    @Test
    fun `history includes receipts made before this change in tier order`() {
        val subject = open()
        val first = subject.insertAppliedReceipt(enemyGuildId, 1, 1_000L)
        val second = subject.insertAppliedReceipt(enemyGuildId, 2, 5_000L, retained = "travel-50")
        val history = subject.repository.history(enemyGuildId)
        assertEquals(listOf(first, second), history.map { it.transactionId })
        assertEquals(listOf(1, 2), history.map { it.tier })
        assertEquals(Instant.ofEpochMilli(5_000L), history.last().appliedAt)
        assertEquals("travel-50", history.last().retainedRewardId)
    }

    @Test
    fun `first to reach a tier is ordered by commit time then transaction id`() {
        val subject = open()
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        subject.insertAppliedReceipt(guildId, 1, 9_000L)
        subject.insertAppliedReceipt(enemyGuildId, 1, 2_000L, transactionId = highId)
        subject.insertAppliedReceipt(UUID.randomUUID(), 1, 2_000L, transactionId = lowId)
        subject.insertAppliedReceipt(guildId, 2, 3_000L)

        assertEquals(lowId, subject.repository.firstToReach(1)?.transactionId)
        assertEquals(guildId, subject.repository.firstToReach(2)?.guildId)
        assertNull(subject.repository.firstToReach(3))
    }

    @Test
    fun `reading history does not write receipts or the journal`() {
        val subject = open()
        assertIs<PrestigeResult.Applied>(subject.repository.confirm(quote()) { null })
        fun count(table: String) = subject.storage.connection.getFirstColumn<Number>("SELECT COUNT(*) FROM $table").toLong()
        val receipts = count("guild_prestige_transactions")
        val journal = count("guild_gold_operations")
        subject.repository.history(guildId)
        subject.repository.firstToReach(1)
        assertEquals(receipts, count("guild_prestige_transactions"))
        assertEquals(journal, count("guild_gold_operations"))
    }

    // REQ-186: a staff grant is a real prestige with no fee and no level 100 requirement.
    private fun grant(reward: String = "bank-1", id: UUID = UUID.randomUUID()) = PrestigeQuote(
        transactionId = id, guildId = guildId, actorId = actorId, retainedRewardId = reward,
        quotedFee = 0, expectedOwnershipVersion = 1, expectedPrestigeCount = 0,
    )

    @Test
    fun `a staff grant prestiges below level 100 without charging and shows in history`() {
        val subject = open()
        subject.storage.connection.executeUpdate(
            "UPDATE guild_progression SET current_level = 40 WHERE guild_id = ?", guildId.toString(),
        )
        val granted = subject.repository.grantWithCommitStatus(grant())
        val applied = assertIs<PrestigeResult.Applied>(granted.result)
        assertTrue(granted.newlyCommitted)
        assertEquals(1, applied.prestigeCount)
        assertEquals(0L, applied.fee)
        assertEquals(19_000L, subject.gold.getBalance(guildId))
        assertTrue("bank-1" in subject.snapshot().ownership.permanent)
        assertEquals(listOf(1), subject.repository.history(guildId).map { it.tier })
    }

    @Test
    fun `a staff grant still refuses an unknown reward and the maximum`() {
        val subject = open()
        assertEquals(
            PrestigeRejection.INVALID_SELECTION,
            assertIs<PrestigeResult.Rejected>(subject.repository.grantWithCommitStatus(grant("nope")).result).reason,
        )
        assertIs<PrestigeResult.Applied>(subject.repository.grantWithCommitStatus(grant()).result)
        settings.progression = settings.progression.copy(prestige = settings.progression.prestige.copy(maxCount = 1))
        val snap = subject.snapshot()
        val maxed = grant().copy(expectedOwnershipVersion = snap.version, expectedPrestigeCount = 1)
        assertEquals(
            PrestigeRejection.MAX_PRESTIGE,
            assertIs<PrestigeResult.Rejected>(subject.repository.grantWithCommitStatus(maxed).result).reason,
        )
    }

    @Test
    fun `a grant cannot be passed off as a paid prestige`() {
        val subject = open()
        val paid = quote()
        assertIs<PrestigeResult.Applied>(subject.repository.confirm(paid) { null })
        assertEquals(
            PrestigeRejection.ID_CONFLICT,
            assertIs<PrestigeResult.Rejected>(subject.repository.grantWithCommitStatus(paid.copy(quotedFee = 0)).result).reason,
        )
    }

    // REQ-185: lowering drops the newest kept rewards with their tiers and is recorded.
    @Test
    fun `lowering the count drops the newest kept reward and keeps everything else`() {
        val subject = open()
        assertIs<PrestigeResult.Applied>(subject.repository.grantWithCommitStatus(grant()).result)
        val staff = UUID.randomUUID()
        assertEquals(listOf("bank-1"), subject.repository.rewardsDroppedBy(guildId, 0))
        val result = subject.repository.setPrestigeCount(guildId, 0, staff, Instant.ofEpochMilli(5_000))
        assertEquals(PrestigeAdjustmentResult.Applied(oldCount = 1, newCount = 0, removedRewards = listOf("bank-1")), result)
        assertEquals(0, subject.snapshot().ownership.prestigeCount)
        assertTrue("bank-1" !in subject.snapshot().ownership.permanent)
        assertEquals(19_000L, subject.gold.getBalance(guildId))
        assertEquals(1, subject.repository.history(guildId).size, "history stays as it was")
        closeStorage(subject.storage)
        val reopened = open(seed = false)
        assertEquals(
            listOf(PrestigeAdjustment(guildId, staff, 1, 0, Instant.ofEpochMilli(5_000))),
            reopened.repository.adjustments(guildId),
        )
    }

    @Test
    fun `the count must stay between 0 and the maximum`() {
        val subject = open()
        assertEquals(PrestigeAdjustmentResult.OutOfRange, subject.repository.setPrestigeCount(guildId, 4, actorId, Instant.now()))
        assertEquals(PrestigeAdjustmentResult.OutOfRange, subject.repository.setPrestigeCount(guildId, -1, actorId, Instant.now()))
        assertEquals(PrestigeAdjustmentResult.Unchanged, subject.repository.setPrestigeCount(guildId, 0, actorId, Instant.now()))
        assertEquals(PrestigeAdjustmentResult.RaiseNeedsGrant, subject.repository.setPrestigeCount(guildId, 2, actorId, Instant.now()))
        assertEquals(PrestigeAdjustmentResult.NotFound, subject.repository.setPrestigeCount(UUID.randomUUID(), 1, actorId, Instant.now()))
    }

    // Audit 2026-11-10: REQ-186 waives only the fee and the level; other guards stay.
    @Test
    fun `a staff grant is still refused while the guild's gold is frozen`() {
        val subject = open()
        subject.storage.connection.executeUpdate(
            "INSERT INTO guild_gold_security (guild_id, frozen, reason, updated_by, updated_at) VALUES (?, ?, ?, ?, ?)",
            guildId.toString(), true, "audit test", actorId.toString(), 1L,
        )
        assertEquals(
            PrestigeRejection.FROZEN,
            assertIs<PrestigeResult.Rejected>(subject.repository.grantWithCommitStatus(grant()).result).reason,
        )
    }
}
