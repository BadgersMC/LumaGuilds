package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [InviteBlockRepository] on SQLite or MariaDB. Every block is loaded once at start and kept in memory; changes are
 * written first and only then applied in memory, so a failed write never shows as a block that is not saved.
 */
class InviteBlockRepositorySQL(private val storage: Storage<Database>) : InviteBlockRepository {
    private val blocks = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    @Volatile override var isAvailable: Boolean = false
        private set

    init {
        try {
            storage.connection.connection.use { connection ->
                connection.prepareStatement("SELECT owner_id, target_id FROM ${InviteBlockSchema.TABLE}").use { statement ->
                    statement.executeQuery().use { rows ->
                        while (rows.next()) {
                            val owner = runCatching { UUID.fromString(rows.getString(1)) }.getOrNull() ?: continue
                            val target = runCatching { UUID.fromString(rows.getString(2)) }.getOrNull() ?: continue
                            set(owner).add(target)
                        }
                    }
                }
            }
            isAvailable = true
        } catch (_: SQLException) {
            // Left unavailable: invites are refused until the plugin restarts with working storage.
        }
    }

    override fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks[ownerId]?.contains(targetId) == true
    override fun blockedBy(ownerId: UUID): Set<UUID> = blocks[ownerId]?.toSet().orEmpty()

    override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
        if (!isAvailable || ownerId == targetId) return false
        if (isBlocked(ownerId, targetId) == blocked) return true
        return try {
            storage.connection.connection.use { connection ->
                if (blocked) {
                    try {
                        connection.prepareStatement(
                            "INSERT INTO ${InviteBlockSchema.TABLE} (owner_id, target_id, created_at) VALUES (?, ?, ?)"
                        ).use { statement ->
                            statement.setString(1, ownerId.toString())
                            statement.setString(2, targetId.toString())
                            statement.setLong(3, System.currentTimeMillis())
                            statement.executeUpdate()
                        }
                    } catch (error: SQLException) {
                        // Another server (shared MariaDB) saved the same block first.
                        if (error.errorCode !in DUPLICATE_KEY) throw error
                    }
                } else {
                    connection.prepareStatement("DELETE FROM ${InviteBlockSchema.TABLE} WHERE owner_id = ? AND target_id = ?").use { statement ->
                        statement.setString(1, ownerId.toString())
                        statement.setString(2, targetId.toString())
                        statement.executeUpdate()
                    }
                }
            }
            if (blocked) set(ownerId).add(targetId) else blocks[ownerId]?.remove(targetId)
            true
        } catch (_: SQLException) {
            false
        }
    }

    private fun set(owner: UUID): MutableSet<UUID> = blocks.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }

    private companion object {
        val DUPLICATE_KEY = setOf(19, 1062, 1555, 2067)
    }
}
