package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.values.GuildCreationCooldown
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** Callback writes must use the provided connection; guild mutation and history commit together. */
class GuildCreationHistorySQL(private val storage: Storage<Database>) {
    private val maria = storage.dialect == SqlDialect.MARIADB
    init {
        val engine = if (maria) " ENGINE=InnoDB" else ""
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_creators (
            guild_id VARCHAR(36) PRIMARY KEY, creator_id VARCHAR(36) NOT NULL,
            created_at BIGINT NOT NULL, deleted_at BIGINT NULL)$engine""")
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_creation_cooldowns (
            player_id VARCHAR(36) PRIMARY KEY, blocked_until BIGINT NULL)$engine""")
    }

    fun cooldownUntil(playerId: UUID): Instant? = storage.connection.connection.use { readCooldown(it, playerId) }

    fun create(guildId: UUID, creatorId: UUID, at: Instant, insert: (Connection) -> Boolean): Boolean =
        transaction { connection ->
            lockCreator(connection, creatorId)
            val blocked = readCooldown(connection, creatorId)
            if (blocked != null && at < blocked) return@transaction false
            if (!insert(connection)) return@transaction false
            execute(connection, "INSERT INTO guild_creators (guild_id, creator_id, created_at) VALUES (?, ?, ?)",
                guildId.toString(), creatorId.toString(), at.toEpochMilli())
            true
        }

    fun delete(guildId: UUID, policy: GuildCreationCooldown, at: Instant, delete: (Connection) -> Boolean): Boolean {
        // Attribution is immutable. Read before acquiring the creator lock to keep lock ordering stable.
        val creator = storage.connection.connection.use { connection ->
            connection.prepareStatement("SELECT creator_id FROM guild_creators WHERE guild_id = ?").use {
                it.setString(1, guildId.toString())
                it.executeQuery().use { rows -> if (rows.next()) UUID.fromString(rows.getString(1)) else null }
            }
        }
        return transaction { connection ->
            if (creator == null) return@transaction delete(connection) // Legacy creator is unknown, never inferred.
            lockCreator(connection, creator)
            val created = connection.prepareStatement("SELECT created_at, deleted_at FROM guild_creators WHERE guild_id = ?").use {
                it.setString(1, guildId.toString())
                it.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong("deleted_at")
                    if (!rows.wasNull()) return@transaction false
                    Instant.ofEpochMilli(rows.getLong("created_at"))
                }
            }
            val expires = policy.expiresAt(created, at)
            if (!delete(connection)) return@transaction false
            execute(connection, "UPDATE guild_creators SET deleted_at = ? WHERE guild_id = ?", at.toEpochMilli(), guildId.toString())
            val previous = readCooldown(connection, creator)
            if (expires != null && (previous == null || expires > previous))
                execute(connection, "UPDATE guild_creation_cooldowns SET blocked_until = ? WHERE player_id = ?", expires.toEpochMilli(), creator.toString())
            true
        }
    }

    private fun lockCreator(connection: Connection, playerId: UUID) {
        val sql = if (maria) "INSERT INTO guild_creation_cooldowns (player_id) VALUES (?) ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)"
            else "INSERT OR IGNORE INTO guild_creation_cooldowns (player_id) VALUES (?)"
        execute(connection, sql, playerId.toString())
        execute(connection, "UPDATE guild_creation_cooldowns SET player_id = player_id WHERE player_id = ?", playerId.toString())
    }

    private fun readCooldown(connection: Connection, playerId: UUID): Instant? =
        connection.prepareStatement("SELECT blocked_until FROM guild_creation_cooldowns WHERE player_id = ?").use {
            it.setString(1, playerId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) null else {
                    val millis = rows.getLong(1)
                    if (rows.wasNull()) null else Instant.ofEpochMilli(millis)
                }
            }
        }

    private fun execute(connection: Connection, sql: String, vararg args: Any?) =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }

    private fun transaction(action: (Connection) -> Boolean): Boolean = storage.connection.connection.use { connection ->
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val changed = action(connection)
            if (changed) connection.commit() else connection.rollback()
            changed
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally { connection.autoCommit = autoCommit }
    }
}
