package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import co.aikar.idb.DbRow
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildCosmeticUnlockRepository
import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildCosmeticSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Guild cosmetic ownership ledger (REQ-121), preloaded like the emoji grant ledger. */
@Suppress("LibraryEntitiesShouldNotBePublic")
class GuildCosmeticUnlockRepositorySQLite(private val storage: Storage<Database>) : GuildCosmeticUnlockRepository {
    private val logger = LoggerFactory.getLogger(GuildCosmeticUnlockRepositorySQLite::class.java)
    private val unlocks = ConcurrentHashMap<Triple<UUID, String, String>, GuildCosmeticUnlock>()
    private val insertSql =
        if (storage.dialect == SqlDialect.MARIADB) {
            "INSERT IGNORE INTO guild_cosmetic_unlocks " +
                "(guild_id, cosmetic_type, cosmetic_key, display_name, source, unlocked_at) VALUES (?, ?, ?, ?, ?, ?)"
        } else {
            "INSERT OR IGNORE INTO guild_cosmetic_unlocks " +
                "(guild_id, cosmetic_type, cosmetic_key, display_name, source, unlocked_at) VALUES (?, ?, ?, ?, ?, ?)"
        }

    init {
        createTable()
        preload()
    }

    override fun getForGuild(guildId: UUID): List<GuildCosmeticUnlock> {
        val owned = unlocks.values.filter { it.guildId == guildId }
        return owned.sortedBy { it.unlockedAt }
    }

    override fun get(guildId: UUID, type: String, key: String): GuildCosmeticUnlock? {
        val id = Triple(guildId, type, key)
        return unlocks[id]
    }

    override fun saveIfAbsent(unlock: GuildCosmeticUnlock): Boolean {
        val id = Triple(unlock.guildId, unlock.type, unlock.key)
        if (unlocks.containsKey(id)) return true
        return try {
            insertUnlock(unlock)
            // A concurrent writer may have inserted first; the cache keeps whichever record landed.
            unlocks.putIfAbsent(id, readRow(unlock.guildId, unlock.type, unlock.key) ?: unlock)
            true
        } catch (exception: SQLException) {
            logger.error(
                "Failed to persist cosmetic {}:{} for guild {}",
                unlock.type,
                unlock.key,
                unlock.guildId,
                exception,
            )
            false
        }
    }

    private fun insertUnlock(unlock: GuildCosmeticUnlock) {
        storage.connection.executeUpdate(
            insertSql,
            unlock.guildId.toString(),
            unlock.type,
            unlock.key,
            unlock.displayName,
            unlock.source,
            unlock.unlockedAt.toEpochMilli(),
        )
    }

    override fun delete(guildId: UUID, type: String, key: String): Boolean {
        return try {
            storage.connection.executeUpdate(
                "DELETE FROM guild_cosmetic_unlocks WHERE guild_id = ? AND cosmetic_type = ? AND cosmetic_key = ?",
                guildId.toString(),
                type,
                key,
            )
            unlocks.remove(Triple(guildId, type, key))
            true
        } catch (exception: SQLException) {
            logger.error("Failed to delete cosmetic $type:$key for guild $guildId", exception)
            false
        }
    }

    private fun readRow(guildId: UUID, type: String, key: String): GuildCosmeticUnlock? =
        storage.connection.getFirstRow(
            "SELECT guild_id, cosmetic_type, cosmetic_key, display_name, source, unlocked_at " +
                "FROM guild_cosmetic_unlocks " +
                "WHERE guild_id = ? AND cosmetic_type = ? AND cosmetic_key = ?",
            guildId.toString(),
            type,
            key,
        )?.toUnlock()

    private fun DbRow.toUnlock(): GuildCosmeticUnlock {
        return GuildCosmeticUnlock(
            guildId = UUID.fromString(getString("guild_id")),
            type = getString("cosmetic_type"),
            key = getString("cosmetic_key"),
            displayName = getString("display_name"),
            source = getString("source"),
            unlockedAt = Instant.ofEpochMilli((get<Any>("unlocked_at") as Number).toLong()),
        )
    }

    private fun createTable() {
        try {
            storage.connection.connection.use { connection ->
                GuildCosmeticSchema.create(connection, mariaDb = storage.dialect == SqlDialect.MARIADB)
            }
        } catch (exception: SQLException) {
            throw DatabaseOperationException("Failed to create guild cosmetic unlock ledger", exception)
        }
    }

    private fun preload() {
        try {
            storage.connection.getResults(
                "SELECT guild_id, cosmetic_type, cosmetic_key, display_name, source, unlocked_at " +
                    "FROM guild_cosmetic_unlocks",
            ).forEach { row ->
                val unlock = row.toUnlock()
                unlocks[Triple(unlock.guildId, unlock.type, unlock.key)] = unlock
            }
        } catch (exception: SQLException) {
            throw DatabaseOperationException("Failed to preload guild cosmetic unlock ledger", exception)
        } catch (exception: IllegalArgumentException) {
            throw DatabaseOperationException("Failed to preload guild cosmetic unlock ledger", exception)
        }
    }
}
