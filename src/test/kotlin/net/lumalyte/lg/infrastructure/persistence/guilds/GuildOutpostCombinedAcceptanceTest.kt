package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildHomeAccessState
import net.lumalyte.lg.application.persistence.VerifiedGuildHomeGrant
import net.lumalyte.lg.application.services.GuildGoldService
import net.lumalyte.lg.application.services.GuildGoldSettings
import net.lumalyte.lg.application.services.GuildGoldSettingsProvider
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/** End-to-end grant and financial acceptance gates across the v50/v51 boundaries. */
class GuildOutpostCombinedAcceptanceTest : RewardSqlTestFixture() {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val reviewerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()

    @Test
    fun `earned base slots follow live level milestones and survive prestige reset`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        assertTrue(GuildRepositorySQLite(storage).add(Guild(guildId, "TierProgress", level = 1, createdAt = Instant.now())))
        RewardOwnershipRepositorySQL(storage, catalog).initialize(guildId, 1)
        storage.connection.executeUpdate(
            "INSERT INTO guild_progression (guild_id, current_level, created_at, last_updated) VALUES (?, 1, ?, ?)",
            guildId.toString(), "2026-11-03 00:00:00", "2026-11-03 00:00:00",
        )
        val access = GuildHomeAccessGrantRepositorySQL(storage)
        assertTrue(access.prepareEmptyGuild(guildId))

        var created = 0
        for ((level, expectedSlots) in listOf(1 to 1, 7 to 2, 15 to 3, 20 to 4, 30 to 6)) {
            storage.connection.executeUpdate(
                "UPDATE guild_progression SET current_level = ? WHERE guild_id = ?",
                level, guildId.toString(),
            )
            while (created < expectedSlots) {
                assertTrue(access.canGrantNewHome(guildId, expectedSlots), "level $level should earn $expectedSlots homes")
                val name = "earned-${++created}"
                insertHome(storage, name)
                assertTrue(access.grantNewHome(guildId, name, expectedSlots))
                assertEquals(GuildHomeAccessState.ACTIVE_BASE, access.state(guildId, name))
            }
            assertFalse(access.canGrantNewHome(guildId, expectedSlots), "level $level must not exceed free slots")
        }
        // The durable account stores 1; the shared resolver must honor verified progression.
        assertEquals(1, storage.connection.getFirstRow(
            "SELECT base_home_slots_earned FROM guild_reward_accounts WHERE guild_id = ?",
            guildId.toString(),
        )!!.getInt("base_home_slots_earned"))

        storage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET prestige_count = 1 WHERE guild_id = ?", guildId.toString(),
        )
        storage.connection.executeUpdate(
            "UPDATE guild_progression SET current_level = 1 WHERE guild_id = ?", guildId.toString(),
        )
        assertTrue(access.canGrantNewHome(guildId, 7))
        insertHome(storage, "prestige-free")
        assertTrue(access.grantNewHome(guildId, "prestige-free", 7))
        assertEquals(GuildHomeAccessState.ACTIVE_PRESTIGE, access.state(guildId, "prestige-free"))
        (1..6).forEach { assertEquals(GuildHomeAccessState.ACTIVE_BASE, access.state(guildId, "earned-$it")) }
        assertFalse(access.canGrantNewHome(guildId, 7))
    }

    @Test
    fun `real paid outpost acquisition restores one locked legacy home without a second charge`() {
        val subject = openHistoricalGuild()
        val before = savedRows(subject.storage)
        val grants = buildMap {
            (1..6).forEach { put("legacy-$it", VerifiedGuildHomeGrant(GuildHomeAccessState.ACTIVE_BASE, "BASE:$it")) }
            (1..9).forEach { put("outpost-$it", VerifiedGuildHomeGrant(GuildHomeAccessState.LOCKED_PREMATURE, null)) }
        }
        subject.assertReconciliationPreconditions(grants)
        assertTrue(subject.access.reconcileVerified(guildId, grants, "sha256:audited-ch1-backup", reviewerId))
        assertEquals(before, savedRows(subject.storage))
        assertEquals(6, (1..6).count { subject.access.state(guildId, "legacy-$it") == GuildHomeAccessState.ACTIVE_BASE })
        assertEquals(9, (1..9).count { subject.access.state(guildId, "outpost-$it") == GuildHomeAccessState.LOCKED_PREMATURE })

        val request = purchaseRequest()
        val first = assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(request))
        assertEquals(500L, first.price)
        assertEquals(19_500L, subject.gold.getBalance(guildId))
        assertEquals(1, count(subject.storage, "guild_reward_purchases"))
        assertEquals(1, count(subject.storage, "guild_outpost_acquisitions"))
        assertNotNull(subject.gold.findOperation(request.transactionId))
        assertEquals(null, subject.storage.connection.getFirstRow(
            "SELECT assigned_home_name FROM guild_outpost_acquisitions WHERE transaction_id = ?",
            request.transactionId.toString(),
        )?.getString("assigned_home_name"))

        assertTrue(subject.access.assignPaidOutpost(guildId, request.transactionId, "outpost-1"))
        assertTrue(subject.access.assignPaidOutpost(guildId, request.transactionId, "outpost-1"))
        assertFalse(subject.access.assignPaidOutpost(guildId, request.transactionId, "outpost-2"))
        assertEquals(GuildHomeAccessState.ACTIVE_OUTPOST, subject.access.state(guildId, "outpost-1"))
        assertEquals(GuildHomeAccessState.LOCKED_PREMATURE, subject.access.state(guildId, "outpost-2"))
        assertEquals("outpost-1", subject.storage.connection.getFirstRow(
            "SELECT assigned_home_name FROM guild_outpost_acquisitions WHERE transaction_id = ?",
            request.transactionId.toString(),
        )?.getString("assigned_home_name"))
        assertEquals(first, subject.service.purchaseReward(request))
        assertEquals(19_500L, subject.gold.getBalance(guildId))
        assertEquals(1, count(subject.storage, "guild_reward_purchases"))
        assertEquals(1, count(subject.storage, "guild_outpost_acquisitions"))
        assertEquals(before, savedRows(subject.storage))
    }

    @Test
    fun `unreconciled legacy guild cannot pay for outpost until verified READY`() {
        val subject = openHistoricalGuild()
        val request = purchaseRequest()
        assertEquals(
            RewardPurchaseResult.Rejected(RewardPurchaseRejection.HOME_AUDIT_PENDING),
            subject.service.purchaseReward(request),
        )
        assertEquals(20_000L, subject.gold.getBalance(guildId))
        assertNull(subject.gold.findOperation(request.transactionId))
        assertEquals(0, count(subject.storage, "guild_reward_purchases"))
        assertEquals(0, count(subject.storage, "guild_outpost_acquisitions"))

        val grants = buildMap {
            (1..6).forEach { put("legacy-$it", VerifiedGuildHomeGrant(GuildHomeAccessState.ACTIVE_BASE, "BASE:$it")) }
            (1..9).forEach { put("outpost-$it", VerifiedGuildHomeGrant(GuildHomeAccessState.LOCKED_PREMATURE, null)) }
        }
        subject.assertReconciliationPreconditions(grants)
        assertTrue(subject.access.reconcileVerified(guildId, grants, "sha256:audited-ch1-backup", reviewerId))
        assertIs<RewardPurchaseResult.Applied>(subject.service.purchaseReward(request))
        assertEquals(19_500L, subject.gold.getBalance(guildId))
        assertEquals(1, count(subject.storage, "guild_reward_purchases"))
        assertEquals(1, count(subject.storage, "guild_outpost_acquisitions"))
    }

    private data class Subject(
        val storage: Storage<Database>,
        val access: GuildHomeAccessGrantRepositorySQL,
        val gold: GuildGoldRepositorySQL,
        val service: GuildGoldService,
    )

    private fun Subject.assertReconciliationPreconditions(grants: Map<String, VerifiedGuildHomeGrant>) {
        val savedNames = storage.connection.getResults(
            "SELECT name FROM guild_homes WHERE guild_id = ?", guildId.toString(),
        ).map { it.getString("name") }.toSet()
        assertEquals(grants.keys, savedNames, "Saved names must exactly match operator evidence")
        val policyRows = storage.connection.getResults(
            "SELECT state FROM guild_home_access_policies WHERE guild_id = ?", guildId.toString(),
        )
        assertTrue(policyRows.isEmpty(), "Historical guild must start UNRECONCILED")
        val grantRows = storage.connection.getResults(
            "SELECT home_name, state FROM guild_home_access_grants WHERE guild_id = ?", guildId.toString(),
        )
        assertTrue(grantRows.isEmpty(), "Historical guild must not have prior grants")
        val account = assertNotNull(storage.connection.getFirstRow(
            "SELECT prestige_count, base_home_slots_earned FROM guild_reward_accounts WHERE guild_id = ?",
            guildId.toString(),
        ))
        assertEquals(1, account.getInt("prestige_count"))
        assertEquals(6, account.getInt("base_home_slots_earned"))
    }

    private fun openHistoricalGuild(): Subject {
        val storage = openStorage()
        migrateProductionSchema(storage)
        assertTrue(GuildRepositorySQLite(storage).add(Guild(guildId, "HistoricalHome", level = 100, createdAt = Instant.now())))
        RewardOwnershipRepositorySQL(storage, catalog).initialize(guildId, 6)
        storage.connection.executeUpdate(
            "INSERT INTO guild_progression (guild_id, current_level, created_at, last_updated) VALUES (?, 100, ?, ?)",
            guildId.toString(), "2026-10-01 00:00:00", "2026-10-01 00:00:00",
        )
        storage.connection.executeUpdate(
            "UPDATE guild_reward_accounts SET prestige_count = 1, base_home_slots_earned = 6 WHERE guild_id = ?",
            guildId.toString(),
        )
        val resolvedBase = storage.connection.connection.use { EarnedBaseHomeSlots.resolve(it, guildId) }
        assertEquals(6, resolvedBase, "P1 six verified legacy BASE credits must survive reset")
        (listOf("bank-1") + (1..9).map { "home-$it" }).forEach { id ->
            storage.connection.executeUpdate(
                "INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES (?, ?, 1)",
                guildId.toString(), id,
            )
        }
        (1..6).forEach { insertHome(storage, "legacy-$it") }
        (1..9).forEach { insertHome(storage, "outpost-$it") }
        val gold = GuildGoldRepositorySQL(storage)
        gold.apply(
            GuildGoldMutation(UUID.randomUUID(), guildId, actorId, GuildGoldRoute.SYSTEM,
                GuildGoldDirection.CREDIT, 20_000, 0, "combined acceptance seed"),
            100_000, null,
        )
        val service = GuildGoldService(
            gold,
            GuildGoldSettingsProvider {
                GuildGoldSettings(
                    GuildGoldPolicy(1, 100_000, 1.0, 100_000, 0.0, 0.0, 0, 0, 100_000, 100_000, false),
                    GuildGoldCapacity(100_000, 0),
                )
            },
            rewardPurchases = RewardPurchaseRepositorySQL(storage, catalog, gold),
            rewardPurchaseAuthorization = { actor, guild -> actor == actorId && guild == guildId },
            rewardPurchasesEnabled = { true },
        )
        return Subject(storage, GuildHomeAccessGrantRepositorySQL(storage), gold, service)
    }

    private fun purchaseRequest() = RewardPurchaseRequest(
        UUID.randomUUID(), guildId, actorId, "home-1", requireNotNull(catalog.find("home-1")).price, 0,
    )

    private fun insertHome(storage: Storage<Database>, name: String) {
        storage.connection.executeUpdate(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, 12, 64, 34)",
            guildId.toString(), name, worldId.toString(),
        )
    }

    private fun savedRows(storage: Storage<Database>): List<String> =
        storage.connection.getResults(
            "SELECT name, world_id, x, y, z FROM guild_homes WHERE guild_id = ? ORDER BY name",
            guildId.toString(),
        ).map { "${it.getString("name")}|${it.getString("world_id")}|${it.getInt("x")}|${it.getInt("y")}|${it.getInt("z")}" }

    private fun count(storage: Storage<Database>, table: String): Int {
        require(table in setOf("guild_reward_purchases", "guild_outpost_acquisitions"))
        return storage.connection.getFirstRow(
            "SELECT COUNT(*) AS n FROM $table WHERE guild_id = ?", guildId.toString(),
        )!!.getInt("n")
    }
}
