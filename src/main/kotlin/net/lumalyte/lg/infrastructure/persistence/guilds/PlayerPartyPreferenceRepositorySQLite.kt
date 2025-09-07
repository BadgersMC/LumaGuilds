package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PlayerPartyPreferenceRepositorySQLite(private val storage: SQLiteStorage) : PlayerPartyPreferenceRepository {

    private val preferences = mutableMapOf<UUID, PlayerPartyPreference>()

    init {
        createPlayerPartyPreferencesTable()
        preload()
    }

    private fun createPlayerPartyPreferencesTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS player_party_preferences (
                player_id TEXT PRIMARY KEY,
                party_id TEXT NOT NULL,
                set_at TEXT NOT NULL
            )
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create player_party_preferences table", e)
        }
    }

    private fun preload() {
        val sql = "SELECT player_id, party_id, set_at FROM player_party_preferences"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val preference = mapResultSetToPreference(result)
                preferences[preference.playerId] = preference
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload player party preferences", e)
        }
    }

    private fun mapResultSetToPreference(rs: co.aikar.idb.DbRow): PlayerPartyPreference {
        return PlayerPartyPreference(
            playerId = UUID.fromString(rs.getString("player_id")),
            partyId = UUID.fromString(rs.getString("party_id")),
            setAt = Instant.parse(rs.getString("set_at"))
        )
    }

    override fun getByPlayerId(playerId: UUID): PlayerPartyPreference? {
        return preferences[playerId]
    }

    override fun save(preference: PlayerPartyPreference): Boolean {
        val sql = """
            INSERT OR REPLACE INTO player_party_preferences (player_id, party_id, set_at)
            VALUES (?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                preference.playerId.toString(),
                preference.partyId.toString(),
                preference.setAt.toString()
            )
            preferences[preference.playerId] = preference
            true
        } catch (e: SQLException) {
            false
        }
    }

    override fun removeByPlayerId(playerId: UUID): Boolean {
        val sql = "DELETE FROM player_party_preferences WHERE player_id = ?"

        return try {
            storage.connection.executeUpdate(sql, playerId.toString())
            preferences.remove(playerId)
            true
        } catch (e: SQLException) {
            false
        }
    }

    override fun getAll(): Set<PlayerPartyPreference> {
        return preferences.values.toSet()
    }

    override fun removeInvalidPreferences(validPartyIds: Set<UUID>): Int {
        val invalidPreferences = preferences.filter { (_, preference) ->
            !validPartyIds.contains(preference.partyId)
        }

        var removedCount = 0
        for ((playerId, _) in invalidPreferences) {
            if (removeByPlayerId(playerId)) {
                removedCount++
            }
        }

        return removedCount
    }
}
