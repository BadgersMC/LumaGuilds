package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class GuildShopXpRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildShopXpRepositorySQL
    private val guild = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val at = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli()

    @BeforeEach fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INT)")
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        storage.connection.executeUpdate("CREATE TABLE members (player_id VARCHAR(36), guild_id VARCHAR(36))")
        storage.connection.executeUpdate("CREATE TABLE guild_reward_accounts (guild_id VARCHAR(36) PRIMARY KEY, prestige_count INT)")
        storage.connection.executeUpdate("INSERT INTO guild_reward_accounts VALUES (?, 0)", guild.toString())
        val awards = ExperienceAwardRepositorySQL(storage, ProgressionCurve(500.0, 1.15, 150, 100))
        repository = GuildShopXpRepositorySQL(storage, awards)
    }

    @AfterEach fun close() {
        storage.connection.close(5, TimeUnit.SECONDS)
    }

    @DisplayName("duplicate delivery and acknowledgement loss grant only one award")
    @Test
    fun duplicateDeliveryOnce() {
        val id = prepare()
        assertEquals("AWARDED:5", repository.complete(id).status)
        assertEquals("AWARDED:5", repository.complete(id).status)
        assertEquals(5, xp())
    }

    @DisplayName("membership is captured before payment and own guild cannot earn XP")
    @Test
    fun ownGuildSnapshot() {
        storage.connection.executeUpdate("INSERT INTO members VALUES (?, ?)", buyer.toString(), guild.toString())
        val id = prepare()
        storage.connection.executeUpdate("DELETE FROM members")
        assertEquals("OWN_GUILD", repository.complete(id).status)
        assertEquals(0, xp())
        val outside = prepare()
        storage.connection.executeUpdate("INSERT INTO members VALUES (?, ?)", buyer.toString(), guild.toString())
        assertEquals("AWARDED:5", repository.complete(outside).status)
    }

    @DisplayName("caps and cooldown span all shops and denials remain consumed")
    @Test
    fun sharedCapsAndCooldown() {
        val policy = GuildShopXpPolicy(guildDailyCap = 12, buyerDailyCap = 7)
        assertEquals("AWARDED:5", repository.complete(prepare(policy = policy)).status)
        val cooldown = prepare(time = at + 299_999, policy = policy)
        assertEquals("COOLDOWN", repository.complete(cooldown).status)
        assertEquals("AWARDED:2", repository.complete(prepare(time = at + 300_000, policy = policy)).status)
        assertEquals("CAPPED", repository.complete(prepare(time = at + 600_000, policy = policy)).status)
        assertEquals("AWARDED:5", repository.complete(prepare(who = UUID.randomUUID(), policy = policy)).status)
        assertEquals(12, xp())
        assertEquals("COOLDOWN", repository.complete(cooldown).status)
    }

    @DisplayName("prestige rejects pending sale without affecting new run")
    @Test
    fun utcBoundary() {
        val id = prepare()
        storage.connection.executeUpdate("UPDATE guild_reward_accounts SET prestige_count = 1")
        assertEquals("STALE_RUN", repository.complete(id).status)
        assertEquals(0, xp())
        assertEquals("AWARDED:5", repository.complete(prepare()).status)
    }

    @DisplayName("daily limits reset at UTC midnight while cooldown crosses midnight")
    @Test
    fun prestigeRunGuard() {
        val midnight = Instant.parse("2026-10-08T00:00:00Z").toEpochMilli()
        val policy = GuildShopXpPolicy(guildDailyCap = 5, buyerDailyCap = 5)
        repository.complete(prepare(time = midnight - 1, policy = policy))
        assertEquals("COOLDOWN", repository.complete(prepare(time = midnight, policy = policy)).status)
        assertEquals("AWARDED:5", repository.complete(prepare(time = midnight + 300_000, policy = policy)).status)
        assertEquals(10, xp())
    }

    @DisplayName("failed SQL rolls back caps and consumption and can retry")
    @Test
    fun rollbackIsRetryable() {
        val id = prepare()
        storage.connection.executeUpdate(
            "CREATE TRIGGER reject_xp BEFORE INSERT ON experience_transactions BEGIN SELECT RAISE(ABORT, 'test fault'); END",
        )
        assertFails { repository.complete(id) }
        assertEquals(0, xp())
        storage.connection.executeUpdate("DROP TRIGGER reject_xp")
        assertEquals("AWARDED:5", repository.complete(id).status)
    }

    @DisplayName("concurrent distinct buyers cannot exceed shared guild cap")
    @Test
    fun concurrentGuildCap() {
        val policy = GuildShopXpPolicy(guildDailyCap = 12)
        val ids = List(12) { prepare(who = UUID.randomUUID(), policy = policy) }
        val start = CountDownLatch(1)
        val results =
            ids.map { id ->
                CompletableFuture.supplyAsync {
                    start.await()
                    repository.complete(id)
                }
            }
        start.countDown()
        results.forEach { it.get(10, TimeUnit.SECONDS) }
        assertEquals(12, xp())
        assertEquals(3, results.count { it.get().status.startsWith("AWARDED:") })
        ids.forEach { repository.complete(it) }
        assertEquals(12, xp())
    }

    @DisplayName("concurrent same buyer across shops and duplicate ID cannot bypass cooldown")
    @Test
    fun concurrentPairCooldown() {
        val ids = List(8) { prepare() }
        val start = CountDownLatch(1)
        val results =
            (ids + ids).map { id ->
                CompletableFuture.supplyAsync {
                    start.await()
                    repository.complete(id)
                }
            }
        start.countDown()
        results.forEach { it.get(10, TimeUnit.SECONDS) }
        assertEquals(5, xp())
    }

    @DisplayName("consumption survives XP history retention")
    @Test
    fun retainedConsumption() {
        val id = prepare()
        repository.complete(id)
        storage.connection.executeUpdate("DELETE FROM experience_transactions")
        assertEquals("AWARDED:5", repository.complete(id).status)
        assertEquals(5, xp())
    }

    @DisplayName("disbanded guild terminally rejects prepared sale")
    @Test
    fun disbandConsumes() {
        val id = prepare()
        storage.connection.executeUpdate("DELETE FROM guilds")
        assertEquals("STALE_RUN", repository.complete(id).status)
        assertEquals(0, xp())
    }

    @DisplayName("sale identity cannot be rebound to another buyer")
    @Test
    fun identityConflict() {
        val id = prepare()
        assertFails { repository.prepare(GuildShopXpSale(id, guild, UUID.randomUUID(), at), GuildShopXpPolicy()) }
        assertEquals("AWARDED:5", repository.complete(id).status)
    }

    @DisplayName("disabled and zero cap policies are terminal without XP")
    @Test
    fun disabledZeroCap() {
        assertEquals("DISABLED", repository.complete(prepare(policy = GuildShopXpPolicy(enabled = false))).status)
        assertEquals("CAPPED", repository.complete(prepare(policy = GuildShopXpPolicy(guildDailyCap = 0))).status)
        assertEquals(0, xp())
    }

    @DisplayName("missing authoritative account fails preparation instead of assuming eligibility")
    @Test
    fun missingAccountRejected() {
        storage.connection.executeUpdate("DELETE FROM guild_reward_accounts")
        assertFails { prepare() }
        assertEquals(0, storage.connection.getResults("SELECT id FROM guild_shop_xp_sales").size)
    }

    private fun prepare(
        who: UUID = buyer,
        time: Long = at,
        policy: GuildShopXpPolicy = GuildShopXpPolicy(),
    ): UUID {
        val id = UUID.randomUUID()
        assertEquals("PREPARED", repository.prepare(GuildShopXpSale(id, guild, who, time), policy))
        return id
    }

    private fun xp(): Int =
        storage.connection.getResults("SELECT total_experience FROM guild_progression").sumOf {
            it.getInt("total_experience")
        }
}
