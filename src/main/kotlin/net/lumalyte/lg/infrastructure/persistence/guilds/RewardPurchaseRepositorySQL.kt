package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.RewardPurchaseRepository
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.util.UUID

/** Ownership, canonical gold journal/balance and receipt commit on exactly one connection. */
class RewardPurchaseRepositorySQL(
    private val storage: Storage<Database>,
    private val catalog: RewardCatalog
) : RewardPurchaseRepository {
    private val owners = RewardOwnershipRepositorySQL(storage, catalog)
    private val gold = GuildGoldRepositorySQL(storage)
    private val resolver = RewardEntitlementResolver(catalog)
    private val lockSuffix = if (storage.dialect == SqlDialect.MARIADB) " FOR UPDATE" else ""

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { connection -> connection.createStatement().use {
            it.execute("""CREATE TABLE IF NOT EXISTS guild_reward_purchases (
                transaction_id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                actor_id VARCHAR(36) NOT NULL,
                reward_id VARCHAR(64) NOT NULL,
                quoted_price BIGINT NOT NULL,
                expected_version BIGINT NOT NULL,
                outcome VARCHAR(32) NOT NULL,
                rejection VARCHAR(32) NULL,
                old_balance BIGINT NULL,
                new_balance BIGINT NULL,
                ownership_version BIGINT NULL
            )$engine""")
        } }
    }

    override fun purchase(request: RewardPurchaseRequest, guard: () -> RewardPurchaseRejection?): RewardPurchaseResult = try {
        storage.connection.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                // Write first: SQLite database lock / MariaDB row lock, with no read-lock upgrade.
                connection.prepareStatement("UPDATE guild_reward_accounts SET version = version WHERE guild_id = ?").use {
                    it.setString(1, request.guildId.toString())
                    it.executeUpdate()
                }
                val replay = replay(connection, request)
                val result = replay ?: execute(connection, request, guard).also { saveReceipt(connection, request, it) }
                connection.commit()
                result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = autoCommit
            }
        }
    } catch (_: Exception) {
        RewardPurchaseResult.Failed(request.transactionId)
    }

    private fun execute(connection: Connection, request: RewardPurchaseRequest,
        guard: () -> RewardPurchaseRejection?): RewardPurchaseResult {
        fun reject(reason: RewardPurchaseRejection) = RewardPurchaseResult.Rejected(reason)
        // A journal entry without a purchase receipt is not permission to grant a free perk.
        if (gold.findOperation(connection, request.transactionId, false) != null) return reject(RewardPurchaseRejection.ID_CONFLICT)
        guard()?.let { return reject(it) }
        val reward = catalog.find(request.rewardId) ?: return reject(RewardPurchaseRejection.UNKNOWN_REWARD)
        if (reward.price != request.quotedPrice) return reject(RewardPurchaseRejection.PRICE_CHANGED)
        val snapshot = owners.readSnapshot(connection, request.guildId) ?: return reject(RewardPurchaseRejection.UNINITIALIZED)
        if (snapshot.version != request.expectedVersion) return reject(RewardPurchaseRejection.STALE_STATE)
        val level = connection.prepareStatement("SELECT current_level FROM guild_progression WHERE guild_id = ?$lockSuffix").use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        } ?: return reject(RewardPurchaseRejection.UNINITIALIZED)
        when (resolver.resolve(level, snapshot.ownership).offer(request.rewardId).status) {
            RewardOfferStatus.LOCKED -> return reject(RewardPurchaseRejection.LOCKED)
            RewardOfferStatus.PURCHASED, RewardOfferStatus.PERMANENT -> return reject(RewardPurchaseRejection.ALREADY_OWNED)
            RewardOfferStatus.NO_IMPROVEMENT -> return reject(RewardPurchaseRejection.NO_IMPROVEMENT)
            RewardOfferStatus.AVAILABLE -> Unit
        }
        val frozen = connection.prepareStatement("SELECT frozen FROM guild_gold_security WHERE guild_id = ?$lockSuffix").use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
        }
        if (frozen) return reject(RewardPurchaseRejection.FROZEN)
        val pending = connection.prepareStatement("""SELECT transaction_id FROM guild_gold_operations
            WHERE guild_id = ? AND route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM')
            AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION') LIMIT 1$lockSuffix""").use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> rows.next() }
        }
        if (pending) return reject(RewardPurchaseRejection.PENDING_GOLD)
        val paid = gold.applyInTransaction(connection, mutation(request), Long.MAX_VALUE, null)
        if (paid is GuildGoldResult.Rejected) {
            check(paid.reason == GuildGoldRejection.INSUFFICIENT_FUNDS) { "Unexpected canonical debit rejection" }
            return reject(RewardPurchaseRejection.INSUFFICIENT_FUNDS)
        }
        check(paid is GuildGoldResult.Applied) { "Canonical debit did not complete" }
        val next = resolver.recordPurchase(level, snapshot.ownership, reward.id)
        val saved = owners.saveInTransaction(connection, request.guildId, snapshot.version, next)
        check(saved is RewardOwnershipWrite.Saved) { "Ownership changed during purchase" }
        return RewardPurchaseResult.Applied(request.transactionId, reward.id, reward.price,
            paid.oldBalance, paid.newBalance, saved.snapshot.version)
    }

    private fun replay(connection: Connection, request: RewardPurchaseRequest): RewardPurchaseResult? =
        connection.prepareStatement("SELECT * FROM guild_reward_purchases WHERE transaction_id = ?").use {
            it.setString(1, request.transactionId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) return null
                val original = RewardPurchaseRequest(request.transactionId,
                    UUID.fromString(rows.getString("guild_id")), UUID.fromString(rows.getString("actor_id")),
                    rows.getString("reward_id"), rows.getLong("quoted_price"), rows.getLong("expected_version"))
                if (original != request) return RewardPurchaseResult.Rejected(RewardPurchaseRejection.ID_CONFLICT)
                when (rows.getString("outcome")) {
                    "REJECTED" -> RewardPurchaseResult.Rejected(RewardPurchaseRejection.valueOf(rows.getString("rejection")))
                    "APPLIED" -> {
                        val old = rows.getLong("old_balance").also { check(!rows.wasNull()) }
                        val new = rows.getLong("new_balance").also { check(!rows.wasNull()) }
                        val version = rows.getLong("ownership_version").also { check(!rows.wasNull()) }
                        val operation = gold.findOperation(connection, request.transactionId, false)
                        check(operation?.mutation == mutation(request) && operation.status == GuildGoldOperationStatus.APPLIED &&
                            operation.oldBalance == old && operation.newBalance == new &&
                            old >= request.quotedPrice && new == old - request.quotedPrice &&
                            version == Math.addExact(request.expectedVersion, 1)) {
                            "Purchase receipt disagrees with canonical gold journal"
                        }
                        RewardPurchaseResult.Applied(request.transactionId, request.rewardId, request.quotedPrice, old, new, version)
                    }
                    else -> error("Invalid purchase receipt outcome")
                }
            }
        }

    private fun saveReceipt(connection: Connection, request: RewardPurchaseRequest, result: RewardPurchaseResult) {
        check(result !is RewardPurchaseResult.Failed)
        connection.prepareStatement("""INSERT INTO guild_reward_purchases
            (transaction_id, guild_id, actor_id, reward_id, quoted_price, expected_version, outcome,
             rejection, old_balance, new_balance, ownership_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""").use {
            it.setString(1, request.transactionId.toString())
            it.setString(2, request.guildId.toString())
            it.setString(3, request.actorId.toString())
            it.setString(4, request.rewardId)
            it.setLong(5, request.quotedPrice)
            it.setLong(6, request.expectedVersion)
            it.setString(7, if (result is RewardPurchaseResult.Applied) "APPLIED" else "REJECTED")
            it.setString(8, (result as? RewardPurchaseResult.Rejected)?.reason?.name)
            it.setObject(9, (result as? RewardPurchaseResult.Applied)?.oldBalance)
            it.setObject(10, (result as? RewardPurchaseResult.Applied)?.newBalance)
            it.setObject(11, (result as? RewardPurchaseResult.Applied)?.ownershipVersion)
            check(it.executeUpdate() == 1)
        }
    }

    private fun mutation(request: RewardPurchaseRequest) = GuildGoldMutation(request.transactionId,
        request.guildId, request.actorId, GuildGoldRoute.SYSTEM, GuildGoldDirection.DEBIT,
        request.quotedPrice, 0, "Reward purchase ${request.rewardId}")
}
