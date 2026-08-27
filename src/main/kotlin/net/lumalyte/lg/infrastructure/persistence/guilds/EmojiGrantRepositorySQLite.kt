package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.EmojiGrantRepository
import net.lumalyte.lg.domain.entities.EmojiPermissionGrant
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

class EmojiGrantRepositorySQLite(
    private val storage: Storage<Database>,
) : EmojiGrantRepository {
    private val logger = LoggerFactory.getLogger(EmojiGrantRepositorySQLite::class.java)
    private val grants = mutableMapOf<Pair<UUID, UUID>, EmojiPermissionGrant>()
    private val upsertSql = if (storage.javaClass.simpleName.contains("MariaDB")) {
        """
            INSERT INTO guild_emoji_grants_applied (player_id, guild_id, permission)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE permission = VALUES(permission)
        """.trimIndent()
    } else {
        """
            INSERT OR REPLACE INTO guild_emoji_grants_applied (player_id, guild_id, permission)
            VALUES (?, ?, ?)
        """.trimIndent()
    }

    init {
        createTable()
        preload()
    }

    override fun getAll(): List<EmojiPermissionGrant> = grants.values.toList()

    override fun getForGuild(guildId: UUID): List<EmojiPermissionGrant> =
        grants.values.filter { it.guildId == guildId }

    override fun getForPlayerAndGuild(playerId: UUID, guildId: UUID): EmojiPermissionGrant? =
        grants[playerId to guildId]

    override fun upsert(grant: EmojiPermissionGrant): Boolean {
        return try {
            val changed = storage.connection.executeUpdate(
                upsertSql,
                grant.playerId.toString(),
                grant.guildId.toString(),
                grant.permission,
            ) > 0
            if (changed) grants[grant.playerId to grant.guildId] = grant
            changed
        } catch (exception: SQLException) {
            logger.error("Failed to persist emoji grant for player ${grant.playerId} in guild ${grant.guildId}", exception)
            false
        }
    }

    override fun delete(playerId: UUID, guildId: UUID): Boolean {
        val sql = "DELETE FROM guild_emoji_grants_applied WHERE player_id = ? AND guild_id = ?"
        return try {
            storage.connection.executeUpdate(sql, playerId.toString(), guildId.toString())
            grants.remove(playerId to guildId)
            true
        } catch (exception: SQLException) {
            logger.error("Failed to delete emoji grant for player $playerId in guild $guildId", exception)
            false
        }
    }

    private fun createTable() {
        try {
            val isMariaDb = storage.javaClass.simpleName.contains("MariaDB")
            val createSql = if (isMariaDb) {
                """
                CREATE TABLE IF NOT EXISTS guild_emoji_grants_applied (
                    player_id VARCHAR(36) NOT NULL,
                    guild_id VARCHAR(36) NOT NULL,
                    permission VARCHAR(255) NOT NULL,
                    PRIMARY KEY (player_id, guild_id),
                    INDEX idx_guild_emoji_grants_guild (guild_id)
                )
                """.trimIndent()
            } else {
                """
                CREATE TABLE IF NOT EXISTS guild_emoji_grants_applied (
                    player_id TEXT NOT NULL,
                    guild_id TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    PRIMARY KEY (player_id, guild_id)
                )
                """.trimIndent()
            }
            storage.connection.executeUpdate(createSql)
            if (!isMariaDb) {
                storage.connection.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_guild_emoji_grants_guild ON guild_emoji_grants_applied(guild_id)",
                )
            }
        } catch (exception: SQLException) {
            throw DatabaseOperationException("Failed to create emoji grant ledger", exception)
        }
    }

    private fun preload() {
        try {
            storage.connection.getResults(
                "SELECT player_id, guild_id, permission FROM guild_emoji_grants_applied",
            ).forEach { row ->
                val grant = EmojiPermissionGrant(
                    playerId = UUID.fromString(row.getString("player_id")),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    permission = row.getString("permission"),
                )
                grants[grant.playerId to grant.guildId] = grant
            }
        } catch (exception: SQLException) {
            throw DatabaseOperationException("Failed to preload emoji grant ledger", exception)
        }
    }
}
