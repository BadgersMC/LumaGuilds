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
    fun `post prestige bank capacity guard rejects before debit`() {
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

        assertEquals(
            PrestigeResult.Rejected(PrestigeRejection.POST_PRESTIGE_CAPACITY),
            subject.repository.confirm(quote()) { null },
        )
        assertEquals(targetBalance, subject.gold.getBalance(guildId))
        assertEquals(0, subject.snapshot().ownership.prestigeCount)
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
}
