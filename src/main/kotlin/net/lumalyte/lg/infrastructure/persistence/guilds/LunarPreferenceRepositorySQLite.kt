package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.LunarPreferenceRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * SQLite-backed Lunar visibility preferences.
 *
 * Schema:
 * - `player_lunar_team_tracking(player_id PK, enabled INT)` — absence = opted out
 * - `guild_waypoint_settings(guild_id PK, visible INT)` — absence = visible (default true)
 *
 * Both maps are preloaded into memory and treated as authoritative caches; writes
 * update the cache atomically with the DB row.
 */
class LunarPreferenceRepositorySQLite(
    private val storage: Storage<Database>
) : LunarPreferenceRepository {

    private val playerOptIn = ConcurrentHashMap<UUID, Boolean>()
    private val guildWaypointVisible = ConcurrentHashMap<UUID, Boolean>()

    init {
        createTables()
        preload()
    }

    private fun createTables() {
        try {
            storage.connection.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_lunar_team_tracking (
                    player_id TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            storage.connection.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guild_waypoint_settings (
                    guild_id TEXT PRIMARY KEY,
                    visible INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create lunar preference tables", e)
        }
    }

    private fun preload() {
        try {
            storage.connection.getResults(
                "SELECT player_id, enabled FROM player_lunar_team_tracking"
            ).forEach { row ->
                val id = UUID.fromString(row.getString("player_id"))
                playerOptIn[id] = row.getInt("enabled") == 1
            }
            storage.connection.getResults(
                "SELECT guild_id, visible FROM guild_waypoint_settings"
            ).forEach { row ->
                val id = UUID.fromString(row.getString("guild_id"))
                guildWaypointVisible[id] = row.getInt("visible") == 1
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload lunar preferences", e)
        }
    }

    override fun isPlayerTeamTrackingEnabled(playerId: UUID): Boolean =
        playerOptIn[playerId] == true

    override fun setPlayerTeamTrackingEnabled(playerId: UUID, enabled: Boolean): Boolean {
        return try {
            storage.connection.executeUpdate(
                "INSERT OR REPLACE INTO player_lunar_team_tracking (player_id, enabled) VALUES (?, ?)",
                playerId.toString(),
                if (enabled) 1 else 0
            )
            playerOptIn[playerId] = enabled
            true
        } catch (e: SQLException) {
            false
        }
    }

    override fun getOptedInPlayerIds(): Set<UUID> =
        playerOptIn.entries.asSequence().filter { it.value }.map { it.key }.toSet()

    override fun isGuildWaypointsVisible(guildId: UUID): Boolean =
        guildWaypointVisible[guildId] ?: true

    override fun setGuildWaypointsVisible(guildId: UUID, visible: Boolean): Boolean {
        return try {
            storage.connection.executeUpdate(
                "INSERT OR REPLACE INTO guild_waypoint_settings (guild_id, visible) VALUES (?, ?)",
                guildId.toString(),
                if (visible) 1 else 0
            )
            guildWaypointVisible[guildId] = visible
            true
        } catch (e: SQLException) {
            false
        }
    }
}
