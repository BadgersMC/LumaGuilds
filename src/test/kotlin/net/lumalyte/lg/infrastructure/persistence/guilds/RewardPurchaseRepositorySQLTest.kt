package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class RewardPurchaseRepositorySQLTest : RewardSqlTestFixture() {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()
    private var allowed = true
    private var extraFrozen = false
    private var purchasesEnabled = true

    private data class Subject(val storage: Storage<Database>, val owners: RewardOwnershipRepositorySQL,
        val gold: GuildGoldRepositorySQL, val service: GuildGoldService)

    private fun open(seed: Boolean = true): Subject {
        val storage = openStorage()
        val owners = RewardOwnershipRepositorySQL(storage, catalog)
        val gold = GuildGoldRepositorySQL(storage)
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS guild_progression (guild_id VARCHAR(36) PRIMARY KEY, current_level INTEGER NOT NULL)")
        if (seed) {
            owners.initialize(guildId, 6)
            storage.connection.executeUpdate("INSERT INTO guild_progression (guild_id, current_level) VALUES (?, 100)", guildId.toString())
            gold.apply(GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
                GuildGoldDirection.CREDIT, 20_000, 0, "test seed"), 100_000, null)
        }
        val service = GuildGoldService(gold, GuildGoldSettingsProvider {
            GuildGoldSettings(GuildGoldPolicy(1, 100_000, 1.0, 100_000, 0.0, 0.0, 0, 0, 100_000, 100_000, false),
                GuildGoldCapacity(100_000, 0))
        }, additionalFrozen = { extraFrozen }, rewardPurchases = RewardPurchaseRepositorySQL(storage, catalog),
            rewardPurchaseAuthorization = { actor, guild -> allowed && actor == actorId && guild == guildId },
            rewardPurchasesEnabled = { purchasesEnabled })
        return Subject(storage, owners, gold, service)
    }

    private fun request(id: String = "bank-1", version: Long = 0) = RewardPurchaseRequest(
        UUID.randomUUID(), guildId, actorId, id, requireNotNull(catalog.find(id)).price, version)

    private fun Subject.snapshot() = assertIs<RewardOwnershipRead.Found>(owners.read(guildId)).snapshot

    @Test fun `confirmation gate reload blocks writes and reopening permits identical request`() {
        val subject = open()
        val quote = request()
        purchasesEnabled = false
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE), subject.service.purchaseReward(quote))
        assertEquals(20_000L, subject.gold.getBalance(guildId))
        assertEquals(0L, subject.snapshot().version)
        assertEquals(0, subject.storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_reward_purchases")!!.getInt("n"))
        purchasesEnabled = true
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(quote))
        assertEquals(19_900L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `bank purchase debits canonical gold and saves ownership and receipt once across restart`() {
        val first = open()
        val request = request()
        val receipt = assertIs<RewardPurchaseResult.Applied>(first.service.purchaseReward(request))
        assertEquals(100L, receipt.price)
        assertEquals(19_900L, first.gold.getBalance(guildId))
        assertEquals(setOf("bank-1"), first.snapshot().ownership.currentRun)
        closeStorage(first.storage)
        val restarted = open(false)
        allowed = false // Historical receipt does not debit again under changed permissions.
        assertEquals(receipt, restarted.service.purchaseReward(request))
        assertEquals(19_900L, restarted.gold.getBalance(guildId))
        assertEquals(1L, restarted.snapshot().version)
    }

    @Test
    fun `home purchase is permanent without activating a location`() {
        val subject = open()
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(request("home-1")))
        assertEquals(setOf("home-1"), subject.snapshot().ownership.permanent)
        assertEquals(7, RewardEntitlementResolver(catalog).resolve(1, subject.snapshot().ownership).homeCapacity)
        assertEquals(19_500L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `request identity cannot be reused for another actor reward price or version`() {
        val subject = open()
        val request = request()
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(request))
        listOf(request.copy(actorId = UUID.randomUUID()), request.copy(guildId = UUID.randomUUID()), request.copy(rewardId = "bank-2"),
            request.copy(quotedPrice = 999), request.copy(expectedVersion = 1)).forEach {
            assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.ID_CONFLICT), subject.service.purchaseReward(it))
        }
        assertEquals(19_900L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `locked stale duplicate and dominated offers do not charge`() {
        val subject = open()
        subject.storage.connection.executeUpdate("UPDATE guild_progression SET current_level = 1")
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.LOCKED), subject.service.purchaseReward(request()))
        subject.storage.connection.executeUpdate("UPDATE guild_progression SET current_level = 100")
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(request("cooldown-5")))
        val balance = subject.gold.getBalance(guildId)
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.STALE_STATE), subject.service.purchaseReward(request()))
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.ALREADY_OWNED), subject.service.purchaseReward(request("cooldown-5", 1)))
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.NO_IMPROVEMENT), subject.service.purchaseReward(request("cooldown-1", 1)))
        assertEquals(balance, subject.gold.getBalance(guildId))
    }

    @Test
    fun `authority frozen pending and price checks reject without ownership changes`() {
        val subject = open()
        allowed = false
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAUTHORIZED), subject.service.purchaseReward(request()))
        allowed = true
        extraFrozen = true
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.FROZEN), subject.service.purchaseReward(request()))
        extraFrozen = false
        subject.gold.setFrozen(guildId, true, actorId, "test")
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.FROZEN), subject.service.purchaseReward(request()))
        subject.gold.setFrozen(guildId, false, actorId, "test")
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.PRICE_CHANGED), subject.service.purchaseReward(request().copy(quotedPrice = 1)))
        subject.gold.prepare(GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.PHYSICAL_ITEM,
            GuildGoldDirection.CREDIT, 1, 0, "pending transfer"))
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.PENDING_GOLD), subject.service.purchaseReward(request()))
        assertEquals(20_000L, subject.gold.getBalance(guildId))
        assertEquals(0L, subject.snapshot().version)
    }

    @Test
    fun `insufficient gold produces a terminal receipt and no entitlement`() {
        val subject = open()
        subject.gold.apply(GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
            GuildGoldDirection.DEBIT, 20_000, 0, "empty bank"), 100_000, null)
        val request = request()
        val result = RewardPurchaseResult.Rejected(RewardPurchaseRejection.INSUFFICIENT_FUNDS)
        assertEquals(result, subject.service.purchaseReward(request))
        subject.gold.apply(GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
            GuildGoldDirection.CREDIT, 1_000, 0, "refill"), 100_000, null)
        assertEquals(result, subject.service.purchaseReward(request))
        assertEquals(1_000L, subject.gold.getBalance(guildId))
        assertEquals(0L, subject.snapshot().version)
    }

    @Test
    fun `missing progression or ownership never defaults to an eligible guild`() {
        val subject = open()
        subject.storage.connection.executeUpdate("DELETE FROM guild_progression")
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNINITIALIZED), subject.service.purchaseReward(request()))
        subject.storage.connection.executeUpdate("DELETE FROM guild_reward_accounts")
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNINITIALIZED), subject.service.purchaseReward(request()))
        assertEquals(20_000L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `concurrent new requests cannot charge twice against the same ownership version`() {
        val subject = open()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(request(), request("bank-2")).map { command ->
                pool.submit<RewardPurchaseResult> { subject.service.purchaseReward(command) }
            }.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is RewardPurchaseResult.Applied })
            assertEquals(1, results.count { it == RewardPurchaseResult.Rejected(RewardPurchaseRejection.STALE_STATE) })
            assertEquals(1L, subject.snapshot().version)
            assertEquals(1, subject.snapshot().ownership.currentRun.size)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `concurrent identical retries return the same receipt`() {
        val subject = open()
        val command = request()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit<RewardPurchaseResult> { subject.service.purchaseReward(command) } }
                .map { it.get(15, TimeUnit.SECONDS) }
            assertIs<RewardPurchaseResult.Applied>(results.first())
            assertEquals(results.first(), results.last())
            assertEquals(19_900L, subject.gold.getBalance(guildId))
            assertEquals(1L, subject.snapshot().version)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `failure after debit rolls back gold journal ownership and receipt before retry`() {
        val subject = open()
        var lastAttempt = request()
        for (table in listOf("guild_reward_ownership", "guild_reward_purchases")) {
            val command = request()
            lastAttempt = command
            rejectInserts(subject.storage, table)
            assertIs<RewardPurchaseResult.Failed>(subject.service.purchaseReward(command))
            assertEquals(20_000L, subject.gold.getBalance(guildId))
            assertEquals(0L, subject.snapshot().version)
            assertNull(subject.gold.findOperation(command.transactionId))
            subject.storage.connection.executeUpdate("DROP TRIGGER reject_reward_insert")
        }
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(lastAttempt))
    }

    @Test
    fun `receipt cannot replay as success if its canonical gold journal is missing`() {
        val subject = open()
        val command = request()
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(command))
        subject.storage.connection.executeUpdate("DELETE FROM guild_gold_operations WHERE transaction_id = ?", command.transactionId.toString())
        assertIs<RewardPurchaseResult.Failed>(subject.service.purchaseReward(command))
        assertEquals(19_900L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `purchase entry point defaults to unavailable and authorization defaults to deny`() {
        val subject = open()
        val settings = GuildGoldSettingsProvider { error("Disabled purchase must not read bank settings") }
        val disabled = GuildGoldService(subject.gold, settings)
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE), disabled.purchaseReward(request()))
        val unauthorized = GuildGoldService(subject.gold, settings,
            rewardPurchases = RewardPurchaseRepositorySQL(subject.storage, catalog))
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAUTHORIZED), unauthorized.purchaseReward(request()))
        assertEquals(20_000L, subject.gold.getBalance(guildId))
    }

    @Test
    fun `a preexisting gold transaction cannot be adopted as a new reward purchase`() {
        val subject = open()
        val command = request()
        subject.gold.apply(GuildGoldMutation(command.transactionId, guildId, actorId, GuildGoldRoute.SYSTEM,
            GuildGoldDirection.DEBIT, 100, 0, "Reward purchase bank-1"), 100_000, null)
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.ID_CONFLICT), subject.service.purchaseReward(command))
        assertEquals(0L, subject.snapshot().version)
        assertTrue(subject.snapshot().ownership.currentRun.isEmpty())
        assertEquals(19_900L, subject.gold.getBalance(guildId))
    }
}
