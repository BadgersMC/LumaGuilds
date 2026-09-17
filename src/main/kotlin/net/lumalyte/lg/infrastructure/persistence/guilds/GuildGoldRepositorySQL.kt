package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.domain.gold.GuildGoldDirection
import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldOperationRecord
import net.lumalyte.lg.domain.gold.GuildGoldOperationStatus
import net.lumalyte.lg.domain.gold.GuildGoldPreparation
import net.lumalyte.lg.domain.gold.GuildGoldRejection
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.gold.GuildGoldRoute
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildGoldSchema
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class GuildGoldRepositorySQL(
    private val storage: Storage<Database>
) : GuildGoldRepository {
    private val mariaDb = storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB
    private val guildLocks = ConcurrentHashMap<UUID, ReentrantLock>()

    init {
        createTables()
    }

    override fun getBalance(guildId: UUID): Long = storage.connection.connection.use { connection ->
        selectBalance(connection, guildId, forUpdate = false)
    }

    override fun getTopBalances(limit: Int): List<Pair<UUID, Long>> {
        if (limit <= 0) return emptyList()
        return storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT guild_id, balance FROM vault_gold ORDER BY balance DESC LIMIT ?"
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            add(UUID.fromString(results.getString("guild_id")) to results.getLong("balance"))
                        }
                    }
                }
            }
        }
    }

    override fun getDailyWithdrawn(guildId: UUID, periodStartEpochMs: Long): Long =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT amount FROM guild_gold_withdrawal_usage WHERE guild_id = ? AND period_start = ?"
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.setLong(2, periodStartEpochMs)
                statement.executeQuery().use { results -> if (results.next()) results.getLong("amount") else 0L }
            }
        }

    override fun isFrozen(guildId: UUID): Boolean = storage.connection.connection.use { connection ->
        connection.prepareStatement(
            "SELECT frozen FROM guild_gold_security WHERE guild_id = ?"
        ).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { results -> results.next() && results.getBoolean("frozen") }
        }
    }

    override fun setFrozen(
        guildId: UUID,
        frozen: Boolean,
        actorId: UUID,
        reason: String
    ): Boolean = storage.connection.connection.use { connection ->
        connection.prepareStatement(securityUpsertSql()).use { statement ->
            statement.setString(1, guildId.toString())
            statement.setBoolean(2, frozen)
            statement.setString(3, reason)
            statement.setString(4, actorId.toString())
            statement.setLong(5, System.currentTimeMillis())
            statement.executeUpdate() > 0
        }
    }

    override fun prepare(mutation: GuildGoldMutation): GuildGoldPreparation =
        guildLock(mutation.guildId).withLock {
            storage.connection.connection.use { connection ->
                transaction(connection) {
                    val existing = findOperation(connection, mutation.transactionId, forUpdate = true)
                    if (existing != null) {
                        return@transaction if (existing.mutation == mutation) {
                            GuildGoldPreparation.Existing(existing)
                        } else {
                            GuildGoldPreparation.FingerprintMismatch
                        }
                    }
                    if (mutation.route == GuildGoldRoute.PERSONAL_ACCOUNT || mutation.route == GuildGoldRoute.PHYSICAL_ITEM) {
                        connection.prepareStatement(
                            "SELECT transaction_id FROM guild_gold_operations WHERE guild_id = ? " +
                                "AND route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM') " +
                                "AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION') LIMIT 1"
                        ).use { statement ->
                            statement.setString(1, mutation.guildId.toString())
                            statement.executeQuery().use { rows ->
                                if (rows.next()) return@transaction GuildGoldPreparation.Pending(
                                    UUID.fromString(rows.getString("transaction_id"))
                                )
                            }
                        }
                    }
                    insertPrepared(connection, mutation)
                    GuildGoldPreparation.New(
                        GuildGoldOperationRecord(
                            mutation = mutation,
                            status = GuildGoldOperationStatus.PREPARED,
                            oldBalance = null,
                            newBalance = null
                        )
                    )
                }
            }
        }

    override fun findOperation(transactionId: UUID): GuildGoldOperationRecord? =
        storage.connection.connection.use { connection -> findOperation(connection, transactionId, false) }

    override fun beginExternal(transactionId: UUID, purpose: String): Boolean {
        require(purpose in setOf("DEPOSIT", "ADMISSION", "WITHDRAWAL"))
        return storage.connection.connection.use { connection -> transaction(connection) {
            val operation = findOperation(connection, transactionId, true) ?: return@transaction false
            if (operation.status !in setOf(GuildGoldOperationStatus.PREPARED, GuildGoldOperationStatus.BALANCE_APPLIED)) return@transaction false
            connection.prepareStatement("UPDATE guild_gold_external_attempts SET phase = 'STARTED', purpose = ? " +
                "WHERE transaction_id = ? AND phase = 'READY'").use { statement ->
                statement.setString(1, purpose)
                statement.setString(2, transactionId.toString())
                statement.executeUpdate() == 1
            }
        } }
    }

    override fun recordExternalOutcome(transactionId: UUID, applied: Boolean): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement("UPDATE guild_gold_external_attempts SET phase = ? WHERE transaction_id = ? AND phase = 'STARTED'").use { statement ->
                statement.setString(1, if (applied) "CONFIRMED" else "NO_EFFECT")
                statement.setString(2, transactionId.toString())
                statement.executeUpdate() == 1
            }
        }

    override fun reconcileUnstarted(createdBeforeEpochMs: Long): Int = storage.connection.connection.use { connection ->
        transaction(connection) {
            val candidates = connection.prepareStatement("SELECT transaction_id FROM guild_gold_operations WHERE status = 'PREPARED' AND created_at <= ?").use {
                it.setLong(1, createdBeforeEpochMs)
                it.executeQuery().use { rows -> buildList { while (rows.next()) add(UUID.fromString(rows.getString(1))) } }
            }
            var resolved = 0
            for (id in candidates) {
                // Same lock order as beginExternal: operation first, then receipt. Re-read under
                // lock rather than treating an earlier READY snapshot as proof of no effect.
                val record = findOperation(connection, id, true) ?: continue
                if (record.status != GuildGoldOperationStatus.PREPARED) continue
                val suffix = if (mariaDb) " FOR UPDATE" else ""
                val phase = connection.prepareStatement("SELECT phase FROM guild_gold_external_attempts WHERE transaction_id = ?$suffix").use {
                    it.setString(1, id.toString())
                    it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
                if (phase !in setOf("READY", "NO_EFFECT")) continue // includes legacy rows without evidence
                finishRejected(connection, id, selectBalance(connection, record.mutation.guildId, false), GuildGoldRejection.EXTERNAL_REJECTED)
                resolved++
            }
            resolved
        }
    }

    override fun confirmedCredits(): List<GuildGoldOperationRecord> = storage.connection.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT o.* FROM guild_gold_operations o JOIN guild_gold_external_attempts e " +
                "ON e.transaction_id = o.transaction_id WHERE o.direction = 'CREDIT' " +
                "AND (o.status = 'PREPARED' OR (o.status = 'BALANCE_APPLIED' AND e.purpose = 'DEPOSIT')) AND e.phase = 'CONFIRMED' " +
                "AND e.purpose IN ('DEPOSIT', 'ADMISSION')").use { rows ->
                buildList { while (rows.next()) add(rows.toOperation()) }
            }
        }
    }

    override fun recoverConfirmedCredit(transactionId: UUID, capacity: Long): GuildGoldResult =
        storage.connection.connection.use { connection -> transaction(connection) {
            val record = findOperation(connection, transactionId, true)
                ?: return@transaction GuildGoldResult.Failed(transactionId, false)
            record.toFinalResult()?.let { return@transaction it }
            val purpose = connection.prepareStatement("SELECT purpose FROM guild_gold_external_attempts WHERE transaction_id = ? AND phase = 'CONFIRMED'").use { statement ->
                statement.setString(1, transactionId.toString())
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
            if (record.mutation.direction != GuildGoldDirection.CREDIT || purpose !in setOf("DEPOSIT", "ADMISSION"))
                return@transaction GuildGoldResult.Failed(transactionId, false)
            if (record.status == GuildGoldOperationStatus.BALANCE_APPLIED) {
                if (purpose == "DEPOSIT") finishApplied(connection, transactionId, requireNotNull(record.oldBalance), requireNotNull(record.newBalance))
                return@transaction GuildGoldResult.Applied(transactionId, requireNotNull(record.oldBalance), requireNotNull(record.newBalance), record.mutation.fee)
            }
            if (record.status != GuildGoldOperationStatus.PREPARED) return@transaction GuildGoldResult.Failed(transactionId, false)
            val old = selectBalance(connection, record.mutation.guildId, true)
            val next = calculateNewBalance(record.mutation, old, capacity)
            // Capacity may have changed since the confirmed external debit. Hold, never discard it.
            if (next !is BalanceCalculation.Accepted) return@transaction GuildGoldResult.Failed(transactionId, false)
            upsertBalance(connection, record.mutation.guildId, next.value)
            finishApplied(connection, transactionId, old, next.value,
                if (purpose == "ADMISSION") GuildGoldOperationStatus.BALANCE_APPLIED else GuildGoldOperationStatus.APPLIED)
            GuildGoldResult.Applied(transactionId, old, next.value, record.mutation.fee)
        } }

    override fun pendingPhysicalCredits(): List<GuildGoldOperationRecord> = storage.connection.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM guild_gold_operations WHERE route = 'PHYSICAL_ITEM' AND direction = 'CREDIT' " +
                "AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION')").use { rows ->
                buildList { while (rows.next()) add(rows.toOperation()) }
            }
        }
    }

    override fun apply(
        mutation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long?
    ): GuildGoldResult = applyWithStatus(mutation, capacity, periodStartEpochMs, GuildGoldOperationStatus.APPLIED)

    override fun reverseExternalCredit(transactionId: UUID): Boolean = storage.connection.connection.use { connection ->
        transaction(connection) {
            val original = findOperation(connection, transactionId, true) ?: return@transaction false
            val reversalId = UUID.nameUUIDFromBytes("physical-credit-reversal:$transactionId".toByteArray(Charsets.UTF_8))
            val reversal = original.mutation.copy(transactionId = reversalId, route = GuildGoldRoute.SYSTEM,
                direction = GuildGoldDirection.DEBIT, fee = 0, description = "Reverse physical credit $transactionId")
            val existing = findOperation(connection, reversalId, true)
            if (existing != null) return@transaction existing.mutation == reversal && existing.status == GuildGoldOperationStatus.APPLIED
            if (original.status == GuildGoldOperationStatus.FAILED_COMPENSATION && original.oldBalance != null &&
                original.oldBalance == original.newBalance && original.mutation.direction == GuildGoldDirection.CREDIT &&
                original.mutation.route == GuildGoldRoute.PHYSICAL_ITEM) return@transaction true
            if (original.status != GuildGoldOperationStatus.BALANCE_APPLIED || original.mutation.direction != GuildGoldDirection.CREDIT ||
                original.mutation.route != GuildGoldRoute.PHYSICAL_ITEM) return@transaction false
            val balance = selectBalance(connection, original.mutation.guildId, true)
            if (balance < original.mutation.amount) return@transaction false
            insertPrepared(connection, reversal)
            upsertBalance(connection, original.mutation.guildId, balance - original.mutation.amount)
            finishApplied(connection, reversalId, balance, balance - original.mutation.amount)
            connection.prepareStatement("UPDATE guild_gold_operations SET status = 'FAILED_COMPENSATION', " +
                "compensation_details = 'Credit reversed; physical reservation restoration pending' WHERE transaction_id = ?").use {
                it.setString(1, transactionId.toString()); it.executeUpdate()
            }
            true
        }
    }

    override fun applyExternalDebit(
        mutation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long
    ): GuildGoldResult = applyWithStatus(mutation, capacity, periodStartEpochMs, GuildGoldOperationStatus.BALANCE_APPLIED)

    override fun applyExternalCredit(mutation: GuildGoldMutation, capacity: Long): GuildGoldResult {
        require(mutation.direction == GuildGoldDirection.CREDIT)
        return applyWithStatus(mutation, capacity, null, GuildGoldOperationStatus.BALANCE_APPLIED)
    }

    private fun applyWithStatus(
        mutation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long?,
        finalStatus: GuildGoldOperationStatus
    ): GuildGoldResult = guildLock(mutation.guildId).withLock {
        storage.connection.connection.use { connection ->
            transaction(connection) { applyInTransaction(connection, mutation, capacity, periodStartEpochMs, finalStatus) }
        }
    }

    /** Shared canonical mutation, on the caller's transaction; never commits independently. */
    internal fun applyInTransaction(
        connection: Connection,
        mutation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long?,
        finalStatus: GuildGoldOperationStatus = GuildGoldOperationStatus.APPLIED
    ): GuildGoldResult {
        check(!connection.autoCommit) { "Canonical mutation requires an active transaction" }
        val existing = findOperation(connection, mutation.transactionId, forUpdate = true)
        if (existing != null && existing.mutation != mutation) {
            return GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
        }
        existing?.toFinalResult()?.let { return it }
        if (existing?.status == GuildGoldOperationStatus.BALANCE_APPLIED) {
            return GuildGoldResult.Applied(mutation.transactionId,
                requireNotNull(existing.oldBalance), requireNotNull(existing.newBalance), mutation.fee)
        }
        if (existing == null) insertPrepared(connection, mutation)
        val oldBalance = selectBalance(connection, mutation.guildId, forUpdate = true)
        val newBalance = calculateNewBalance(mutation, oldBalance, capacity)
        if (newBalance is BalanceCalculation.Rejected) {
            finishRejected(connection, mutation.transactionId, oldBalance, newBalance.reason)
            return GuildGoldResult.Rejected(newBalance.reason)
        }
        newBalance as BalanceCalculation.Accepted
        upsertBalance(connection, mutation.guildId, newBalance.value)
        if (mutation.direction == GuildGoldDirection.DEBIT && periodStartEpochMs != null) {
            addWithdrawalUsage(connection, mutation.guildId, periodStartEpochMs, mutation.amount)
        }
        finishApplied(connection, mutation.transactionId, oldBalance, newBalance.value, finalStatus)
        return GuildGoldResult.Applied(mutation.transactionId, oldBalance, newBalance.value, mutation.fee)
    }
    override fun completeExternal(transactionId: UUID): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE guild_gold_operations SET status = ? WHERE transaction_id = ? AND status = ?"
            ).use { statement ->
                statement.setString(1, GuildGoldOperationStatus.APPLIED.name)
                statement.setString(2, transactionId.toString())
                statement.setString(3, GuildGoldOperationStatus.BALANCE_APPLIED.name)
                statement.executeUpdate() == 1
            }
        }

    override fun recordCompensation(
        transactionId: UUID,
        succeeded: Boolean,
        details: String
    ): Boolean = storage.connection.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE guild_gold_operations SET status = ?, compensation_details = ? WHERE transaction_id = ?"
        ).use { statement ->
            statement.setString(
                1,
                if (succeeded) GuildGoldOperationStatus.COMPENSATED.name
                else GuildGoldOperationStatus.FAILED_COMPENSATION.name
            )
            statement.setString(2, details)
            statement.setString(3, transactionId.toString())
            statement.executeUpdate() == 1
        }
    }

    override fun rejectPrepared(transactionId: UUID, reason: GuildGoldRejection): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE guild_gold_operations SET status = ?, rejection_reason = ? WHERE transaction_id = ? AND status = ?"
            ).use { statement ->
                statement.setString(1, GuildGoldOperationStatus.REJECTED.name)
                statement.setString(2, reason.name)
                statement.setString(3, transactionId.toString())
                statement.setString(4, GuildGoldOperationStatus.PREPARED.name)
                statement.executeUpdate() == 1
            }
        }

    override fun compensateDebit(
        originalTransactionId: UUID,
        compensation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long,
        details: String
    ): GuildGoldResult = guildLock(compensation.guildId).withLock {
        storage.connection.connection.use { connection ->
            transaction(connection) {
                val original = findOperation(connection, originalTransactionId, forUpdate = true)
                    ?: return@transaction GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
                if (original.status == GuildGoldOperationStatus.COMPENSATED) {
                    return@transaction findOperation(connection, compensation.transactionId, true)?.toFinalResult()
                        ?: GuildGoldResult.Failed(originalTransactionId, true)
                }
                if (original.status !in setOf(GuildGoldOperationStatus.APPLIED, GuildGoldOperationStatus.BALANCE_APPLIED) ||
                    original.mutation.direction != GuildGoldDirection.DEBIT ||
                    original.mutation.guildId != compensation.guildId
                ) {
                    return@transaction GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
                }
                val existingCompensation = findOperation(connection, compensation.transactionId, true)
                if (existingCompensation != null) {
                    return@transaction existingCompensation.toFinalResult()
                        ?: GuildGoldResult.Rejected(GuildGoldRejection.DUPLICATE_PENDING)
                }
                insertPrepared(connection, compensation)
                val oldBalance = selectBalance(connection, compensation.guildId, forUpdate = true)
                val restored = try {
                    Math.addExact(oldBalance, compensation.amount)
                } catch (_: ArithmeticException) {
                    return@transaction GuildGoldResult.Rejected(GuildGoldRejection.INVALID_AMOUNT)
                }
                if (restored > capacity) {
                    return@transaction GuildGoldResult.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
                }
                upsertBalance(connection, compensation.guildId, restored)
                subtractWithdrawalUsage(
                    connection,
                    compensation.guildId,
                    periodStartEpochMs,
                    original.mutation.amount
                )
                finishApplied(connection, compensation.transactionId, oldBalance, restored)
                markCompensated(connection, originalTransactionId, details)
                GuildGoldResult.Applied(compensation.transactionId, oldBalance, restored, 0)
            }
        }
    }

    private fun createTables() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS vault_gold (
                guild_id VARCHAR(36) PRIMARY KEY,
                balance BIGINT NOT NULL DEFAULT 0,
                last_modified BIGINT NOT NULL
            )
            """.trimIndent()
        )
        storage.connection.connection.use { connection -> GuildGoldSchema.create(connection, mariaDb) }
    }

    private fun calculateNewBalance(
        mutation: GuildGoldMutation,
        oldBalance: Long,
        capacity: Long
    ): BalanceCalculation = try {
        when (mutation.direction) {
            GuildGoldDirection.CREDIT -> {
                val next = Math.addExact(oldBalance, mutation.amount)
                if (next > capacity) BalanceCalculation.Rejected(GuildGoldRejection.CAPACITY_EXCEEDED)
                else BalanceCalculation.Accepted(next)
            }
            GuildGoldDirection.DEBIT -> {
                val total = Math.addExact(mutation.amount, mutation.fee)
                if (oldBalance < total) BalanceCalculation.Rejected(GuildGoldRejection.INSUFFICIENT_FUNDS)
                else BalanceCalculation.Accepted(oldBalance - total)
            }
        }
    } catch (_: ArithmeticException) {
        BalanceCalculation.Rejected(GuildGoldRejection.INVALID_AMOUNT)
    }

    internal fun findOperation(
        connection: Connection,
        transactionId: UUID,
        forUpdate: Boolean
    ): GuildGoldOperationRecord? {
        val suffix = if (forUpdate && mariaDb) " FOR UPDATE" else ""
        return connection.prepareStatement(
            "SELECT * FROM guild_gold_operations WHERE transaction_id = ?$suffix"
        ).use { statement ->
            statement.setString(1, transactionId.toString())
            statement.executeQuery().use { results -> if (results.next()) results.toOperation() else null }
        }
    }

    private fun ResultSet.toOperation(): GuildGoldOperationRecord = GuildGoldOperationRecord(
        mutation = GuildGoldMutation(
            transactionId = UUID.fromString(getString("transaction_id")),
            guildId = UUID.fromString(getString("guild_id")),
            actorId = UUID.fromString(getString("actor_id")),
            route = GuildGoldRoute.valueOf(getString("route")),
            direction = GuildGoldDirection.valueOf(getString("direction")),
            amount = getLong("amount"),
            fee = getLong("fee"),
            description = getString("description")
        ),
        status = GuildGoldOperationStatus.valueOf(getString("status")),
        oldBalance = getNullableLong("old_balance"),
        newBalance = getNullableLong("new_balance"),
        rejection = getString("rejection_reason")?.let(GuildGoldRejection::valueOf)
    )

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun GuildGoldOperationRecord.toFinalResult(): GuildGoldResult? = when (status) {
        GuildGoldOperationStatus.APPLIED -> GuildGoldResult.Applied(
            transactionId = mutation.transactionId,
            oldBalance = requireNotNull(oldBalance),
            newBalance = requireNotNull(newBalance),
            fee = mutation.fee
        )
        GuildGoldOperationStatus.REJECTED -> GuildGoldResult.Rejected(requireNotNull(rejection))
        GuildGoldOperationStatus.COMPENSATED -> GuildGoldResult.Failed(mutation.transactionId, true)
        GuildGoldOperationStatus.FAILED_COMPENSATION -> GuildGoldResult.Failed(mutation.transactionId, false)
        GuildGoldOperationStatus.BALANCE_APPLIED -> null
        GuildGoldOperationStatus.PREPARED -> null
    }

    private fun insertPrepared(connection: Connection, mutation: GuildGoldMutation) {
        connection.prepareStatement(
            """
            INSERT INTO guild_gold_operations
                (transaction_id, guild_id, actor_id, route, direction, amount, fee, status, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, mutation.transactionId.toString())
            statement.setString(2, mutation.guildId.toString())
            statement.setString(3, mutation.actorId.toString())
            statement.setString(4, mutation.route.name)
            statement.setString(5, mutation.direction.name)
            statement.setLong(6, mutation.amount)
            statement.setLong(7, mutation.fee)
            statement.setString(8, GuildGoldOperationStatus.PREPARED.name)
            statement.setString(9, mutation.description)
            statement.setLong(10, System.currentTimeMillis())
            statement.executeUpdate()
        }
        if (mutation.route in setOf(GuildGoldRoute.PERSONAL_ACCOUNT, GuildGoldRoute.PHYSICAL_ITEM)) {
            connection.prepareStatement("INSERT INTO guild_gold_external_attempts (transaction_id, phase) VALUES (?, 'READY')").use { statement ->
                statement.setString(1, mutation.transactionId.toString())
                statement.executeUpdate()
            }
        }
    }

    private fun finishApplied(
        connection: Connection,
        transactionId: UUID,
        oldBalance: Long,
        newBalance: Long,
        status: GuildGoldOperationStatus = GuildGoldOperationStatus.APPLIED
    ) {
        connection.prepareStatement(
            "UPDATE guild_gold_operations SET status = ?, old_balance = ?, new_balance = ? WHERE transaction_id = ?"
        ).use { statement ->
            statement.setString(1, status.name)
            statement.setLong(2, oldBalance)
            statement.setLong(3, newBalance)
            statement.setString(4, transactionId.toString())
            check(statement.executeUpdate() == 1) { "Gold operation was not finalized" }
        }
    }

    private fun finishRejected(
        connection: Connection,
        transactionId: UUID,
        oldBalance: Long,
        reason: GuildGoldRejection
    ) {
        connection.prepareStatement(
            "UPDATE guild_gold_operations SET status = ?, old_balance = ?, new_balance = ?, rejection_reason = ? WHERE transaction_id = ?"
        ).use { statement ->
            statement.setString(1, GuildGoldOperationStatus.REJECTED.name)
            statement.setLong(2, oldBalance)
            statement.setLong(3, oldBalance)
            statement.setString(4, reason.name)
            statement.setString(5, transactionId.toString())
            check(statement.executeUpdate() == 1) { "Gold rejection was not finalized" }
        }
    }

    private fun selectBalance(connection: Connection, guildId: UUID, forUpdate: Boolean): Long {
        val suffix = if (forUpdate && mariaDb) " FOR UPDATE" else ""
        return connection.prepareStatement(
            "SELECT balance FROM vault_gold WHERE guild_id = ?$suffix"
        ).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { results -> if (results.next()) results.getLong("balance") else 0L }
        }
    }

    private fun upsertBalance(connection: Connection, guildId: UUID, balance: Long) {
        connection.prepareStatement(balanceUpsertSql()).use { statement ->
            statement.setString(1, guildId.toString())
            statement.setLong(2, balance)
            statement.setLong(3, System.currentTimeMillis())
            statement.executeUpdate()
        }
    }

    private fun addWithdrawalUsage(
        connection: Connection,
        guildId: UUID,
        periodStartEpochMs: Long,
        amount: Long
    ) {
        connection.prepareStatement(withdrawalUsageUpsertSql()).use { statement ->
            statement.setString(1, guildId.toString())
            statement.setLong(2, periodStartEpochMs)
            statement.setLong(3, amount)
            statement.executeUpdate()
        }
    }

    private fun subtractWithdrawalUsage(
        connection: Connection,
        guildId: UUID,
        periodStartEpochMs: Long,
        amount: Long
    ) {
        connection.prepareStatement(
            "UPDATE guild_gold_withdrawal_usage SET amount = CASE WHEN amount > ? THEN amount - ? ELSE 0 END WHERE guild_id = ? AND period_start = ?"
        ).use { statement ->
            statement.setLong(1, amount)
            statement.setLong(2, amount)
            statement.setString(3, guildId.toString())
            statement.setLong(4, periodStartEpochMs)
            statement.executeUpdate()
        }
    }

    private fun markCompensated(connection: Connection, transactionId: UUID, details: String) {
        connection.prepareStatement(
            "UPDATE guild_gold_operations SET status = ?, compensation_details = ? WHERE transaction_id = ?"
        ).use { statement ->
            statement.setString(1, GuildGoldOperationStatus.COMPENSATED.name)
            statement.setString(2, details)
            statement.setString(3, transactionId.toString())
            check(statement.executeUpdate() == 1) { "Original debit was not marked compensated" }
        }
    }

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun guildLock(guildId: UUID): ReentrantLock =
        guildLocks.computeIfAbsent(guildId) { ReentrantLock() }

    private fun balanceUpsertSql(): String = if (mariaDb) {
        "INSERT INTO vault_gold (guild_id, balance, last_modified) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE balance = VALUES(balance), last_modified = VALUES(last_modified)"
    } else {
        "INSERT INTO vault_gold (guild_id, balance, last_modified) VALUES (?, ?, ?) ON CONFLICT(guild_id) DO UPDATE SET balance = excluded.balance, last_modified = excluded.last_modified"
    }

    private fun withdrawalUsageUpsertSql(): String = if (mariaDb) {
        "INSERT INTO guild_gold_withdrawal_usage (guild_id, period_start, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
    } else {
        "INSERT INTO guild_gold_withdrawal_usage (guild_id, period_start, amount) VALUES (?, ?, ?) ON CONFLICT(guild_id, period_start) DO UPDATE SET amount = guild_gold_withdrawal_usage.amount + excluded.amount"
    }

    private fun securityUpsertSql(): String = if (mariaDb) {
        "INSERT INTO guild_gold_security (guild_id, frozen, reason, updated_by, updated_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE frozen = VALUES(frozen), reason = VALUES(reason), updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)"
    } else {
        "INSERT INTO guild_gold_security (guild_id, frozen, reason, updated_by, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(guild_id) DO UPDATE SET frozen = excluded.frozen, reason = excluded.reason, updated_by = excluded.updated_by, updated_at = excluded.updated_at"
    }

    private sealed interface BalanceCalculation {
        data class Accepted(val value: Long) : BalanceCalculation
        data class Rejected(val reason: GuildGoldRejection) : BalanceCalculation
    }
}
