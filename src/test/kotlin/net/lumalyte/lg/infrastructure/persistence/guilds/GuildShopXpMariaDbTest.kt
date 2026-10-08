package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Optional native loopback integration; never accepts an operator/production database name. */
internal class GuildShopXpMariaDbTest {
    private var storage: MariaDBStorage? = null
    private lateinit var repository: GuildShopXpRepositorySQL
    private val guild = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val at = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli()

    @BeforeEach fun setup() {
        val port = System.getenv("GUILD_SHOP_XP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB test instance not configured")
        val db = MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
        storage = db
        listOf(
            "guild_shop_xp_sales",
            "guild_shop_xp_pairs",
            "guild_experience_source_usage",
            "experience_transactions",
            "guild_progression",
            "guild_reward_accounts",
            "members",
            "guilds",
        ).forEach {
            db.connection.executeUpdate("DROP TABLE IF EXISTS $it")
        }
        db.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INT) ENGINE=InnoDB")
        db.connection.executeUpdate("INSERT INTO guilds VALUES (?, 1)", guild.toString())
        db.connection.executeUpdate("CREATE TABLE members (player_id VARCHAR(36), guild_id VARCHAR(36)) ENGINE=InnoDB")
        db.connection.executeUpdate(
            "CREATE TABLE guild_reward_accounts (guild_id VARCHAR(36) PRIMARY KEY, prestige_count INT) ENGINE=InnoDB",
        )
        db.connection.executeUpdate("INSERT INTO guild_reward_accounts VALUES (?, 0)", guild.toString())
        repository =
            GuildShopXpRepositorySQL(db, ExperienceAwardRepositorySQL(db, ProgressionCurve(500.0, 1.15, 150, 100)))
    }

    @AfterEach fun close() {
        storage?.connection?.close(5, TimeUnit.SECONDS)
    }

    @DisplayName("concurrent buyers obey atomic shared guild cap")
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
    }

    @DisplayName("same buyer cannot evade shared cooldown through simultaneous sales")
    @Test
    fun pairCooldown() {
        val ids = List(8) { prepare() }
        val results = ids.map { CompletableFuture.supplyAsync { repository.complete(it) } }
        results.forEach { it.get(10, TimeUnit.SECONDS) }
        assertEquals(5, xp())
    }

    @DisplayName("own membership snapshot and prestige receipt prevent ineligible delivery")
    @Test
    fun membershipAndPrestige() {
        val db = checkNotNull(storage)
        db.connection.executeUpdate("INSERT INTO members VALUES (?, ?)", buyer.toString(), guild.toString())
        val own = prepare()
        db.connection.executeUpdate("DELETE FROM members")
        assertEquals("OWN_GUILD", repository.complete(own).status)
        val outside = prepare()
        db.connection.executeUpdate("UPDATE guild_reward_accounts SET prestige_count = 1")
        assertEquals("STALE_RUN", repository.complete(outside).status)
        assertEquals(0, xp())
    }

    @DisplayName("duplicate consumption survives ordinary history cleanup")
    @Test
    fun retainedReceipt() {
        val id = prepare()
        assertEquals("AWARDED:5", repository.complete(id).status)
        checkNotNull(storage).connection.executeUpdate("DELETE FROM experience_transactions")
        assertEquals("AWARDED:5", repository.complete(id).status)
        assertEquals(5, xp())
    }

    @DisplayName("buyer daily cap and UTC rollover use persisted sale time")
    @Test
    fun utcBuyerCap() {
        val midnight = Instant.parse("2026-10-08T00:00:00Z").toEpochMilli()
        val policy = GuildShopXpPolicy(buyerDailyCap = 7, pairCooldownSeconds = 0)
        assertEquals("AWARDED:5", repository.complete(prepare(time = midnight - 2, policy = policy)).status)
        assertEquals("AWARDED:2", repository.complete(prepare(time = midnight - 1, policy = policy)).status)
        assertEquals("AWARDED:5", repository.complete(prepare(time = midnight, policy = policy)).status)
        assertEquals(12, xp())
    }

    @DisplayName("SQL failure rolls back usage progression and receipt then retries")
    @Test
    fun rollbackRetry() {
        val db = checkNotNull(storage)
        val id = prepare()
        db.connection.executeUpdate(
            "CREATE TRIGGER reject_shop_xp BEFORE INSERT ON experience_transactions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test fault'",
        )
        assertFails { repository.complete(id) }
        assertEquals(0, xp())
        db.connection.executeUpdate("DROP TRIGGER reject_shop_xp")
        assertEquals("AWARDED:5", repository.complete(id).status)
    }

    @DisplayName("waiting for guild lock does not retain a stale repeatable read snapshot")
    @Test
    fun freshSnapshotAfterWait() {
        val policy = GuildShopXpPolicy(guildDailyCap = 12)
        repository.complete(prepare(policy = policy))
        val id = prepare(who = UUID.randomUUID(), policy = policy)
        checkNotNull(storage).connection.connection.use { c ->
            c.autoCommit = false
            c.prepareStatement("SELECT level FROM guilds WHERE id = ? FOR UPDATE").use { s ->
                s.setString(1, guild.toString())
                s.executeQuery().close()
            }
            val started = CountDownLatch(1)
            val result =
                CompletableFuture.supplyAsync {
                    started.countDown()
                    repository.complete(id)
                }
            check(started.await(5, TimeUnit.SECONDS))
            c.createStatement().use { s ->
                s.executeUpdate(
                    "UPDATE guild_experience_source_usage SET awarded_xp = 10 WHERE source_pool = 'SHOP_SALE'",
                )
                s.executeUpdate("UPDATE guild_progression SET total_experience = 10")
            }
            c.commit()
            assertEquals("AWARDED:2", result.get(10, TimeUnit.SECONDS).status)
        }
        assertEquals(12, xp())
    }

    private fun prepare(who: UUID = buyer, time: Long = at, policy: GuildShopXpPolicy = GuildShopXpPolicy()): UUID {
        val id = UUID.randomUUID()
        repository.prepare(GuildShopXpSale(id, guild, who, time), policy)
        return id
    }

    private fun xp(): Int =
        checkNotNull(storage).connection.getResults("SELECT total_experience FROM guild_progression").sumOf {
            it.getInt("total_experience")
        }
}
