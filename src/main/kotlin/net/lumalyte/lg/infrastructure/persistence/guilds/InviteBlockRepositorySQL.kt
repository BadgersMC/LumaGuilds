package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.persistence.InviteBlockWriteOutcome
import net.lumalyte.lg.application.persistence.InviteBlockWriteResult
import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

/** Personal blocks with legacy cache reads, authoritative durable reads, and serialized SQL/cache publication. */
internal class InviteBlockRepositorySQL(
    private val storage: Storage<Database>,
    private val blocks: InviteBlockCache = InviteBlockCache(),
) : InviteBlockRepository {
    private val transactions = InviteBlockSqlTransactions(storage)
    private val logger = LoggerFactory.getLogger(InviteBlockRepositorySQL::class.java)

    @Volatile
    override var isAvailable: Boolean = false
        private set

    init {
        try {
            loadBlocks()
            isAvailable = true
        } catch (error: SQLException) {
            blocks.clear()
            logger.error(
                "Unable to load invite blocks; guild invites disabled until storage repair and plugin restart",
                error,
            )
        }
    }

    private fun loadBlocks() {
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT owner_id, target_id FROM ${InviteBlockSchema.TABLE}",
            ).use { statement ->
                statement.executeQuery().use { readBlocks(it) }
            }
        }
    }

    private fun readBlocks(rows: ResultSet) {
        while (rows.next()) {
            val owner = readBlockIdentity(rows.getString(OWNER_COLUMN))
            val target = readBlockIdentity(rows.getString(TARGET_COLUMN))
            blocks.publish(owner, target, true)
        }
    }

    /** Reads the directional block from memory without acquiring a database connection. */
    override fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks.isBlocked(ownerId, targetId)

    /** Copies the owner cache so callers cannot mutate stored blocks. */
    override fun blockedBy(ownerId: UUID): Set<UUID> = blocks.blockedBy(ownerId)

    /** Keeps v1 failure availability behavior while routing every attempted write through authoritative SQL. */
    @Synchronized
    override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
        return isAvailable && setBlockedDurable(ownerId, targetId, blocked).succeeded
    }

    /** A successful read is committed storage evidence, not a cache or availability guess. */
    @Synchronized
    override fun isBlockedDurable(ownerId: UUID, targetId: UUID): Boolean {
        val result = transactions.execute { hasStoredBlock(it, ownerId, targetId) }
        if (result.outcome != InviteBlockWriteOutcome.COMMITTED) {
            throw IllegalStateException("Unable to read committed invite block state", result.failure)
        }
        val stored = checkNotNull(result.value)
        blocks.publish(ownerId, targetId, stored)
        return stored
    }

    /** No cache fast path: compensation must remove a stored row even when this instance never cached it. */
    @Synchronized
    override fun setBlockedDurable(ownerId: UUID, targetId: UUID, blocked: Boolean): InviteBlockWriteResult {
        if (ownerId == targetId) {
            return InviteBlockWriteResult(
                InviteBlockWriteOutcome.NOT_APPLIED,
                failure = IllegalArgumentException("A player cannot block themselves"),
            )
        }
        val result = transactions.execute { connection ->
            if (blocked) insertBlock(connection, ownerId, targetId) else deleteBlock(connection, ownerId, targetId)
        }
        if (result.outcome != InviteBlockWriteOutcome.COMMITTED) {
            logger.error(
                "Unable to confirm invite block {} -> {} ({})",
                ownerId,
                targetId,
                result.outcome,
                result.failure,
            )
            return InviteBlockWriteResult(result.outcome, failure = result.failure)
        }
        return try {
            blocks.publish(ownerId, targetId, blocked)
            InviteBlockWriteResult(result.outcome, cacheReconciled = true, failure = result.failure)
        } catch (publication: Exception) {
            // The database commit remains known, but V2 completion cannot succeed with an unreconciled cache.
            logger.error(
                "Invite block committed but cache reconciliation failed {} -> {}",
                ownerId,
                targetId,
                publication,
            )
            InviteBlockWriteResult(result.outcome, failure = publication)
        }
    }

    private fun insertBlock(connection: Connection, ownerId: UUID, targetId: UUID) {
        try {
            connection.prepareStatement(
                "INSERT INTO ${InviteBlockSchema.TABLE} (owner_id, target_id, created_at) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setString(OWNER_COLUMN, ownerId.toString())
                statement.setString(TARGET_COLUMN, targetId.toString())
                statement.setLong(CREATED_AT_COLUMN, System.currentTimeMillis())
                statement.executeUpdate()
            }
        } catch (error: SQLException) {
            // Another writer may have saved this exact primary key first.
            if (error.errorCode !in DUPLICATE_KEY_CODES || !hasStoredBlock(connection, ownerId, targetId)) throw error
        }
    }

    private fun hasStoredBlock(connection: Connection, ownerId: UUID, targetId: UUID): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?",
        ).use { statement ->
            statement.setString(OWNER_COLUMN, ownerId.toString())
            statement.setString(TARGET_COLUMN, targetId.toString())
            statement.executeQuery().use { it.next() }
        }

    private fun deleteBlock(connection: Connection, ownerId: UUID, targetId: UUID) {
        connection.prepareStatement(
            "DELETE FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?",
        ).use { statement ->
            statement.setString(OWNER_COLUMN, ownerId.toString())
            statement.setString(TARGET_COLUMN, targetId.toString())
            statement.executeUpdate()
        }
    }

    private companion object {
        const val OWNER_COLUMN = 1
        const val TARGET_COLUMN = 2
        const val CREATED_AT_COLUMN = 3
        val DUPLICATE_KEY_CODES = setOf(19, 1062, 1555, 2067)
    }
}

private fun readBlockIdentity(value: String?): UUID {
    try {
        return UUID.fromString(value)
    } catch (error: IllegalArgumentException) {
        throw SQLException("Invalid identity in invite block storage", error)
    }
}
