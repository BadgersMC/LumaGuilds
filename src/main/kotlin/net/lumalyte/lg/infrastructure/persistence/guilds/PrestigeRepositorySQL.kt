package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.WarStatus
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/**
 * Atomic prestige transaction: canonical guild gold, reward ownership, progression reset,
 * guild-level mirror and idempotent receipt commit on one database connection.
 */
class PrestigeRepositorySQL(
    private val storage: Storage<Database>,
    catalog: RewardCatalog,
    private val owners: RewardOwnershipRepositorySQL,
    private val gold: GuildGoldRepositorySQL,
    private val guildActions: GuildActionCoordinator,
    private val config: () -> MainConfig,
) : PrestigeRepository {
    private val resolver = RewardEntitlementResolver(catalog)
    private val lockSuffix = if (storage.dialect == SqlDialect.MARIADB) " FOR UPDATE" else ""

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { connection ->
            connection.createStatement().use {
                it.execute("""CREATE TABLE IF NOT EXISTS guild_prestige_transactions (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    retained_reward_id VARCHAR(64) NOT NULL,
                    quoted_fee BIGINT NOT NULL,
                    expected_version BIGINT NOT NULL,
                    expected_prestige_count INTEGER NOT NULL,
                    outcome VARCHAR(32) NOT NULL,
                    rejection VARCHAR(64) NULL,
                    old_balance BIGINT NULL,
                    new_balance BIGINT NULL,
                    ownership_version BIGINT NULL,
                    prestige_count INTEGER NULL,
                    post_bank_capacity BIGINT NULL,
                    home_capacity INTEGER NULL
                )$engine""")
            }
        }
    }

    override fun confirm(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeResult = try {
        guildActions.withGuilds(request.guildId) {
            gold.withGuildLock(request.guildId) {
                storage.connection.connection.use { connection ->
                    transaction(connection) {
                        replay(connection, request)
                            ?: execute(connection, request, guard).also { saveReceipt(connection, request, it) }
                    }
                }
            }
        }
    } catch (_: Exception) {
        PrestigeResult.Failed(request.transactionId)
    }

    private fun execute(
        connection: Connection,
        request: PrestigeQuote,
        guard: () -> PrestigeRejection?,
    ): PrestigeResult {
        fun reject(reason: PrestigeRejection) = PrestigeResult.Rejected(reason)

        if (gold.findOperation(connection, request.transactionId, false) != null) {
            return reject(PrestigeRejection.ID_CONFLICT)
        }

        val accountLocked = connection.prepareStatement(
            "UPDATE guild_reward_accounts SET version = version WHERE guild_id = ?"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeUpdate() == 1
        }
        if (!accountLocked) return reject(PrestigeRejection.UNINITIALIZED)

        guard()?.let { return reject(it) }

        val settings = config()
        val prestige = settings.progression.prestige
        if (!prestige.enabled) return reject(PrestigeRejection.UNAVAILABLE)

        val snapshot = owners.readSnapshot(connection, request.guildId)
            ?: return reject(PrestigeRejection.UNINITIALIZED)
        if (snapshot.version != request.expectedOwnershipVersion ||
            snapshot.ownership.prestigeCount != request.expectedPrestigeCount
        ) {
            return reject(PrestigeRejection.STALE_STATE)
        }
        if (snapshot.ownership.prestigeCount >= prestige.maxCount) {
            return reject(PrestigeRejection.MAX_PRESTIGE)
        }

        val expectedFee = prestige.feeFor(snapshot.ownership.prestigeCount)
        if (request.quotedFee != expectedFee) return reject(PrestigeRejection.FEE_CHANGED)

        val level = connection.prepareStatement(
            "SELECT current_level FROM guild_progression WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        } ?: return reject(PrestigeRejection.UNINITIALIZED)
        if (level != 100) return reject(PrestigeRejection.NOT_LEVEL_100)

        val currentEntitlements = resolver.resolve(
            level,
            snapshot.ownership,
            settings.bank.maxBankBalance.toLong(),
            settings.guild.maxMembersPerGuild,
        )
        if (currentEntitlements.prestigeChoices.none { it.id == request.retainedRewardId }) {
            return reject(PrestigeRejection.INVALID_SELECTION)
        }

        if (hasBlockingWar(connection, request.guildId)) {
            return reject(PrestigeRejection.ACTIVE_WAR)
        }

        val frozen = connection.prepareStatement(
            "SELECT frozen FROM guild_gold_security WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
        }
        if (frozen) return reject(PrestigeRejection.FROZEN)

        val pendingGold = connection.prepareStatement(
            """SELECT transaction_id FROM guild_gold_operations
               WHERE guild_id = ? AND transaction_id <> ?
                 AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION')
               LIMIT 1$lockSuffix"""
        ).use {
            it.setString(1, request.guildId.toString())
            it.setString(2, request.transactionId.toString())
            it.executeQuery().use { rows -> rows.next() }
        }
        if (pendingGold) return reject(PrestigeRejection.PENDING_GOLD)

        val balance = connection.prepareStatement(
            "SELECT balance FROM vault_gold WHERE guild_id = ?$lockSuffix"
        ).use {
            it.setString(1, request.guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
        }
        if (balance < expectedFee) return reject(PrestigeRejection.INSUFFICIENT_FUNDS)

        val nextOwnership = snapshot.ownership.copy(
            currentRun = emptySet(),
            permanent = snapshot.ownership.permanent + request.retainedRewardId,
            prestigeCount = snapshot.ownership.prestigeCount + 1,
        )
        val postEntitlements = resolver.resolve(
            1,
            nextOwnership,
            settings.bank.maxBankBalance.toLong(),
            settings.guild.maxMembersPerGuild,
        )
        val postBalance = balance - expectedFee
        if (postBalance > postEntitlements.bankCapacity) {
            return reject(PrestigeRejection.POST_PRESTIGE_CAPACITY)
        }

        val mutation = mutation(request)
        val paid = gold.applyInTransaction(connection, mutation, Long.MAX_VALUE, null)
        if (paid is GuildGoldResult.Rejected) {
            check(paid.reason == GuildGoldRejection.INSUFFICIENT_FUNDS) {
                "Unexpected canonical prestige debit rejection: ${paid.reason}"
            }
            return reject(PrestigeRejection.INSUFFICIENT_FUNDS)
        }
        check(paid is GuildGoldResult.Applied) { "Canonical prestige debit did not complete" }

        val nextLevelXp = ProgressionCurve.from(settings.progression).experienceForNextLevel(1)
        val now = Instant.now().toEpochMilli()
        val progressionUpdated = connection.prepareStatement(
            """UPDATE guild_progression
               SET total_experience = 0,
                   current_level = 1,
                   experience_this_level = 0,
                   experience_for_next_level = ?,
                   last_level_up = NULL,
                   unlocked_perks = '[]',
                   last_updated = ?
               WHERE guild_id = ? AND current_level = 100"""
        ).use {
            it.setInt(1, nextLevelXp)
            it.setLong(2, now)
            it.setString(3, request.guildId.toString())
            it.executeUpdate() == 1
        }
        check(progressionUpdated) { "Progression changed during prestige" }

        connection.prepareStatement("UPDATE guilds SET level = 1 WHERE id = ?").use {
            it.setString(1, request.guildId.toString())
            check(it.executeUpdate() == 1) { "Guild disappeared during prestige" }
        }

        val ownershipSaved = owners.saveInTransaction(
            connection,
            request.guildId,
            snapshot.version,
            nextOwnership,
        )
        check(ownershipSaved is RewardOwnershipWrite.Saved) { "Ownership changed during prestige" }

        return PrestigeResult.Applied(
            transactionId = request.transactionId,
            retainedRewardId = request.retainedRewardId,
            fee = expectedFee,
            oldBalance = paid.oldBalance,
            newBalance = paid.newBalance,
            ownershipVersion = ownershipSaved.snapshot.version,
            prestigeCount = nextOwnership.prestigeCount,
            postBankCapacity = postEntitlements.bankCapacity,
            homeCapacity = postEntitlements.homeCapacity,
        )
    }

    private fun hasBlockingWar(connection: Connection, guildId: UUID): Boolean =
        connection.prepareStatement("SELECT payload FROM guild_war_records").use { statement ->
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val record = WarRecordCodec.decode(rows.getString("payload"))
                    val war = record.war
                    if (war != null &&
                        guildId in setOf(war.declaringGuildId, war.defendingGuildId) &&
                        war.status in setOf(WarStatus.DECLARED, WarStatus.ACTIVE)
                    ) {
                        return true
                    }
                    val declaration = record.declaration
                    if (declaration != null && !declaration.accepted && !declaration.rejected &&
                        guildId in setOf(declaration.declaringGuildId, declaration.defendingGuildId)
                    ) {
                        return true
                    }
                }
                false
            }
        }

    private fun replay(connection: Connection, request: PrestigeQuote): PrestigeResult? =
        connection.prepareStatement(
            "SELECT * FROM guild_prestige_transactions WHERE transaction_id = ?"
        ).use {
            it.setString(1, request.transactionId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) return null
                val original = PrestigeQuote(
                    transactionId = request.transactionId,
                    guildId = UUID.fromString(rows.getString("guild_id")),
                    actorId = UUID.fromString(rows.getString("actor_id")),
                    retainedRewardId = rows.getString("retained_reward_id"),
                    quotedFee = rows.getLong("quoted_fee"),
                    expectedOwnershipVersion = rows.getLong("expected_version"),
                    expectedPrestigeCount = rows.getInt("expected_prestige_count"),
                )
                if (original != request) {
                    return PrestigeResult.Rejected(PrestigeRejection.ID_CONFLICT)
                }
                when (rows.getString("outcome")) {
                    "REJECTED" -> PrestigeResult.Rejected(
                        PrestigeRejection.valueOf(rows.getString("rejection"))
                    )
                    "APPLIED" -> {
                        val operation = gold.findOperation(connection, request.transactionId, false)
                        check(operation?.mutation == mutation(request) &&
                            operation.status == GuildGoldOperationStatus.APPLIED) {
                            "Prestige receipt disagrees with canonical gold journal"
                        }
                        PrestigeResult.Applied(
                            transactionId = request.transactionId,
                            retainedRewardId = request.retainedRewardId,
                            fee = request.quotedFee,
                            oldBalance = rows.getLong("old_balance"),
                            newBalance = rows.getLong("new_balance"),
                            ownershipVersion = rows.getLong("ownership_version"),
                            prestigeCount = rows.getInt("prestige_count"),
                            postBankCapacity = rows.getLong("post_bank_capacity"),
                            homeCapacity = rows.getInt("home_capacity"),
                        )
                    }
                    else -> error("Invalid prestige receipt outcome")
                }
            }
        }

    private fun saveReceipt(connection: Connection, request: PrestigeQuote, result: PrestigeResult) {
        check(result !is PrestigeResult.Failed)
        val applied = result as? PrestigeResult.Applied
        connection.prepareStatement(
            """INSERT INTO guild_prestige_transactions
               (transaction_id, guild_id, actor_id, retained_reward_id, quoted_fee,
                expected_version, expected_prestige_count, outcome, rejection,
                old_balance, new_balance, ownership_version, prestige_count,
                post_bank_capacity, home_capacity)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use {
            it.setString(1, request.transactionId.toString())
            it.setString(2, request.guildId.toString())
            it.setString(3, request.actorId.toString())
            it.setString(4, request.retainedRewardId)
            it.setLong(5, request.quotedFee)
            it.setLong(6, request.expectedOwnershipVersion)
            it.setInt(7, request.expectedPrestigeCount)
            it.setString(8, if (applied != null) "APPLIED" else "REJECTED")
            it.setString(9, (result as? PrestigeResult.Rejected)?.reason?.name)
            it.setObject(10, applied?.oldBalance)
            it.setObject(11, applied?.newBalance)
            it.setObject(12, applied?.ownershipVersion)
            it.setObject(13, applied?.prestigeCount)
            it.setObject(14, applied?.postBankCapacity)
            it.setObject(15, applied?.homeCapacity)
            check(it.executeUpdate() == 1)
        }
    }

    private fun mutation(request: PrestigeQuote) = GuildGoldMutation(
        transactionId = request.transactionId,
        guildId = request.guildId,
        actorId = request.actorId,
        route = GuildGoldRoute.SYSTEM,
        direction = GuildGoldDirection.DEBIT,
        amount = request.quotedFee,
        fee = 0,
        description = "Guild prestige retaining ${request.retainedRewardId}",
    )

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = autoCommit
        }
    }
}