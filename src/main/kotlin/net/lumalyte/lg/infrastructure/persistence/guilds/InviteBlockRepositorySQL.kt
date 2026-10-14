package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Durable personal blocks with memory-only reads and serialized write/cache publication. */
internal class InviteBlockRepositorySQL(private val storage: Storage<Database>) : InviteBlockRepository {
    private val blocks = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    @Volatile
    override var isAvailable: Boolean = false
        private set

    init {
        try {
            loadBlocks()
            isAvailable = true
        } catch (error: SQLException) {
            blocks.clear()
            LoggerFactory.getLogger(InviteBlockRepositorySQL::class.java).error(
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
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        blockSet(
                            readIdentity(rows.getString(OWNER_COLUMN)),
                        ).add(readIdentity(rows.getString(TARGET_COLUMN)))
                    }
                }
            }
        }
    }

    override fun isBlocked(
        ownerId: UUID,
        targetId: UUID,
    ): Boolean = blocks[ownerId]?.contains(targetId) == true

    override fun blockedBy(ownerId: UUID): Set<UUID> = blocks[ownerId]?.toSet().orEmpty()

    @Synchronized
    override fun setBlocked(
        ownerId: UUID,
        targetId: UUID,
        blocked: Boolean,
    ): Boolean =
        when {
            !isAvailable || ownerId == targetId -> false
            isBlocked(ownerId, targetId) == blocked -> true
            else -> saveBlock(ownerId, targetId, blocked)
        }

    private fun saveBlock(
        ownerId: UUID,
        targetId: UUID,
        blocked: Boolean,
    ): Boolean =
        try {
            storage.connection.connection.use { connection ->
                if (blocked) insertBlock(connection, ownerId, targetId) else deleteBlock(connection, ownerId, targetId)
            }
            if (blocked) blockSet(ownerId).add(targetId) else blocks[ownerId]?.remove(targetId)
            true
        } catch (_: SQLException) {
            false
        }

    private fun insertBlock(
        connection: Connection,
        ownerId: UUID,
        targetId: UUID,
    ) {
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

    private fun hasStoredBlock(
        connection: Connection,
        ownerId: UUID,
        targetId: UUID,
    ): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?",
        ).use { statement ->
            statement.setString(OWNER_COLUMN, ownerId.toString())
            statement.setString(TARGET_COLUMN, targetId.toString())
            statement.executeQuery().use { it.next() }
        }

    private fun deleteBlock(
        connection: Connection,
        ownerId: UUID,
        targetId: UUID,
    ) {
        connection.prepareStatement(
            "DELETE FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?",
        ).use { statement ->
            statement.setString(OWNER_COLUMN, ownerId.toString())
            statement.setString(TARGET_COLUMN, targetId.toString())
            statement.executeUpdate()
        }
    }

    private fun blockSet(owner: UUID): MutableSet<UUID> =
        blocks.computeIfAbsent(
            owner,
        ) { ConcurrentHashMap.newKeySet() }

    private fun readIdentity(value: String?): UUID =
        try {
            UUID.fromString(value)
        } catch (error: IllegalArgumentException) {
            throw SQLException("Invalid identity in invite block storage", error)
        }

    private companion object {
        const val OWNER_COLUMN = 1
        const val TARGET_COLUMN = 2
        const val CREATED_AT_COLUMN = 3
        val DUPLICATE_KEY_CODES = setOf(19, 1062, 1555, 2067)
    }
}
