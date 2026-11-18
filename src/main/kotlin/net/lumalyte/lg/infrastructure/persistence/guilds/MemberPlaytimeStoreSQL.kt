package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.values.MemberPlaytimeStore
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** REQ-300: each player's last known play ticks. Saves run off the server thread. */
class MemberPlaytimeStoreSQL(
    private val storage: Storage<Database>,
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "LumaGuilds-playtime").apply { isDaemon = true } },
) : MemberPlaytimeStore {
    private val logger = LoggerFactory.getLogger(MemberPlaytimeStoreSQL::class.java)

    private val upsertSql = if (storage.dialect == SqlDialect.MARIADB) {
        "INSERT INTO $TABLE (player_id, play_ticks, updated_at) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE play_ticks = VALUES(play_ticks), updated_at = VALUES(updated_at)"
    } else {
        "INSERT INTO $TABLE (player_id, play_ticks, updated_at) VALUES (?, ?, ?) " +
            "ON CONFLICT(player_id) DO UPDATE SET play_ticks = excluded.play_ticks, updated_at = excluded.updated_at"
    }

    init {
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS $TABLE (" +
            "player_id VARCHAR(36) NOT NULL PRIMARY KEY, play_ticks BIGINT NOT NULL, updated_at BIGINT NOT NULL)")
    }

    override fun loadAll(): Map<UUID, Long> = try {
        storage.connection.getResults("SELECT player_id, play_ticks FROM $TABLE").mapNotNull { row ->
            // SQLite hands back small numbers as Integer, so read any Number.
            runCatching { UUID.fromString(row.getString("player_id")) to (row.get<Any>("play_ticks") as Number).toLong() }.getOrNull()
        }.toMap()
    } catch (e: Exception) {
        logger.error("Could not load stored playtime; members will be read again", e)
        emptyMap()
    }

    override fun save(playerId: UUID, ticks: Long) {
        val at = System.currentTimeMillis()
        runCatching {
            writer.execute {
                runCatching { storage.connection.executeUpdate(upsertSql, playerId.toString(), ticks, at) }
                    .onFailure { logger.warn("Could not store playtime for $playerId: ${it.message}") }
            }
        }
    }

    /** Lets queued saves finish (used on shutdown). */
    fun close() {
        writer.shutdown()
        runCatching { writer.awaitTermination(5, TimeUnit.SECONDS) }
    }

    companion object {
        const val TABLE = "guild_member_playtime"
    }
}
