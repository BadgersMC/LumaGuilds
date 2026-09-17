package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import co.aikar.idb.Database
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class RewardOwnershipRepositorySQLTest : RewardSqlTestFixture() {
    private val guildId = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()

    private fun open(): Pair<Storage<Database>, RewardOwnershipRepositorySQL> {
        val storage = openStorage()
        return storage to RewardOwnershipRepositorySQL(storage, catalog)
    }
    @Test
    fun `missing state is explicit and initialization cannot replace migrated home capacity`() {
        val (_, repository) = open()
        assertEquals(RewardOwnershipRead.Missing, repository.read(guildId))
        assertIs<RewardOwnershipWrite.Saved>(repository.initialize(guildId, 8))
        assertEquals(RewardOwnershipWrite.Conflict, repository.initialize(guildId, 1))
        val snapshot = assertIs<RewardOwnershipRead.Found>(repository.read(guildId)).snapshot
        assertEquals(8, snapshot.ownership.initialHomeCapacity)
        assertEquals(0L, snapshot.version)
    }

    @Test
    fun `purchased and permanent identities survive database reopen`() {
        val (storage, repository) = open()
        repository.initialize(guildId, 6)
        val next = RewardOwnership(currentRun = setOf("bank-1", "cooldown-5"), permanent = setOf("home-1", "home-9"), initialHomeCapacity = 6)
        assertIs<RewardOwnershipWrite.Saved>(repository.save(guildId, 0, next))
        closeStorage(storage)
        val (_, reopened) = open()
        val snapshot = assertIs<RewardOwnershipRead.Found>(reopened.read(guildId)).snapshot
        assertEquals(1L, snapshot.version)
        assertEquals(next.currentRun, snapshot.ownership.currentRun)
        assertEquals(next.permanent, snapshot.ownership.permanent)
        assertEquals(8, RewardEntitlementResolver(catalog).resolve(100, snapshot.ownership).homeCapacity)
    }

    @Test
    fun `concurrent stale writers cannot overwrite purchased rewards`() {
        val (_, repository) = open()
        repository.initialize(guildId, 1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf("bank-1", "bank-2").map { id ->
                pool.submit<RewardOwnershipWrite> { repository.save(guildId, 0, RewardOwnership(currentRun = setOf(id))) }
            }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is RewardOwnershipWrite.Saved })
            assertEquals(1, results.count { it == RewardOwnershipWrite.Conflict })
            assertEquals(1, assertIs<RewardOwnershipRead.Found>(repository.read(guildId)).snapshot.ownership.currentRun.size)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `storage failure is distinct from missing and corrupt assets never become empty ownership`() {
        val (storage, repository) = open()
        repository.initialize(guildId, 1)
        storage.connection.executeUpdate("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES (?, ?, ?)", guildId.toString(), "unknown-reward", true)
        assertIs<RewardOwnershipRead.Failed>(repository.read(guildId))
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 0, RewardOwnership()))
        storage.connection.executeUpdate("DROP TABLE guild_reward_accounts")
        assertIs<RewardOwnershipRead.Failed>(repository.read(UUID.randomUUID()))
    }

    @Test
    fun `permanent assets and ordinary purchases cannot silently disappear`() {
        val (_, repository) = open()
        repository.initialize(guildId, 6)
        val state = RewardOwnership(currentRun = setOf("bank-1"), permanent = setOf("home-1"), initialHomeCapacity = 6)
        assertIs<RewardOwnershipWrite.Saved>(repository.save(guildId, 0, state))
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 1, state.copy(permanent = emptySet())))
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 1, state.copy(currentRun = emptySet())))
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 1, state.copy(initialHomeCapacity = 1)))
        assertEquals(1L, assertIs<RewardOwnershipRead.Found>(repository.read(guildId)).snapshot.version)
    }

    @Test
    fun `invalid transition rolls back version and all ownership rows`() {
        val (storage, repository) = open()
        repository.initialize(guildId, 1)
        // Real database failure after the account update, before the new ownership is stored.
        rejectInserts(storage, "guild_reward_ownership")
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 0, RewardOwnership(currentRun = setOf("bank-1"))))
        val snapshot = assertIs<RewardOwnershipRead.Found>(repository.read(guildId)).snapshot
        assertEquals(0L, snapshot.version)
        assertTrue(snapshot.ownership.currentRun.isEmpty())
    }

    @Test
    fun `prestige ownership snapshot preserves homes and retains exactly one purchased perk`() {
        val (_, repository) = open()
        repository.initialize(guildId, 6)
        val before = RewardOwnership(currentRun = setOf("bank-1", "cooldown-5"), permanent = setOf("home-1"), initialHomeCapacity = 6)
        assertIs<RewardOwnershipWrite.Saved>(repository.save(guildId, 0, before))
        val unpurchased = before.copy(currentRun = emptySet(), permanent = before.permanent + "bank-10", prestigeCount = 1)
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 1, unpurchased))
        val after = before.copy(currentRun = emptySet(), permanent = before.permanent + "bank-1", prestigeCount = 1)
        assertIs<RewardOwnershipWrite.Saved>(repository.save(guildId, 1, after))
        val saved = assertIs<RewardOwnershipRead.Found>(repository.read(guildId)).snapshot
        assertEquals(2L, saved.version)
        assertEquals(9_000L, RewardEntitlementResolver(catalog).resolve(1, saved.ownership).bankCapacity)
        assertEquals(8, RewardEntitlementResolver(catalog).resolve(1, saved.ownership).homeCapacity)
        assertIs<RewardOwnershipWrite.Failed>(repository.save(guildId, 2, before))
    }

    @Test
    fun `orphaned reward rows cannot be mistaken for an uninitialized guild`() {
        val (storage, repository) = open()
        storage.connection.executeUpdate("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES (?, ?, ?)", guildId.toString(), "home-1", true)
        assertIs<RewardOwnershipRead.Failed>(repository.read(guildId))
        assertIs<RewardOwnershipWrite.Failed>(repository.initialize(guildId, 1))
        assertIs<RewardOwnershipRead.Failed>(repository.read(guildId))
    }
}
