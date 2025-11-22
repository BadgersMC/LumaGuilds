package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.infrastructure.persistence.getInstant
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PartyRepositorySQLite(private val storage: Storage<Database>) : PartyRepository {

    private val parties: MutableMap<UUID, Party> = mutableMapOf()
    private val gson = Gson()

    init {
        createPartyTable()
        preload()
    }
    
    private fun createPartyTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS parties (
                id TEXT PRIMARY KEY,
                name TEXT,
                guild_ids TEXT NOT NULL,
                leader_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT,
                restricted_roles TEXT
            )
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create parties table", e)
        }
    }
    
    private fun preload() {
        val sql = """
            SELECT id, name, guild_ids, leader_id, status, created_at, expires_at, restricted_roles,
                   muted_players, banned_players
            FROM parties
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val party = mapResultSetToParty(result)
                parties[party.id] = party
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload parties", e)
        }
    }
    
    private fun mapResultSetToParty(rs: co.aikar.idb.DbRow): Party {
        val guildIdsString = rs.getString("guild_ids")
        val guildIds = guildIdsString.split(",").map { UUID.fromString(it.trim()) }.toSet()

        val restrictedRolesString = rs.getString("restricted_roles")
        val restrictedRoles = restrictedRolesString?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { UUID.fromString(it.trim()) }
            ?.toSet()

        // Parse muted_players JSON: {"playerId": "epochSeconds"|null}
        val mutedPlayersJson = rs.getString("muted_players") ?: "{}"
        val mutedPlayers = parseMutedPlayersJson(mutedPlayersJson)

        // Parse banned_players JSON: ["playerId1", "playerId2"]
        val bannedPlayersJson = rs.getString("banned_players") ?: "[]"
        val bannedPlayers = parseBannedPlayersJson(bannedPlayersJson)

        return Party(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            guildIds = guildIds,
            leaderId = UUID.fromString(rs.getString("leader_id")),
            status = PartyStatus.valueOf(rs.getString("status")),
            createdAt = rs.getInstantNotNull("created_at"),
            expiresAt = rs.getInstant("expires_at"),
            restrictedRoles = restrictedRoles,
            mutedPlayers = mutedPlayers,
            bannedPlayers = bannedPlayers
        )
    }
    
    override fun add(party: Party): Boolean {
        val sql = """
            INSERT INTO parties (id, name, guild_ids, leader_id, status, created_at, expires_at, restricted_roles,
                                 muted_players, banned_players)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val guildIdsString = party.guildIds.joinToString(",") { it.toString() }
            val restrictedRolesString = party.restrictedRoles?.joinToString(",") { it.toString() }
            val mutedPlayersJson = serializeMutedPlayers(party.mutedPlayers)
            val bannedPlayersJson = serializeBannedPlayers(party.bannedPlayers)

            val rowsAffected = storage.connection.executeUpdate(sql,
                party.id.toString(),
                party.name,
                guildIdsString,
                party.leaderId.toString(),
                party.status.name,
                party.createdAt.toString(),
                party.expiresAt?.toString(),
                restrictedRolesString,
                mutedPlayersJson,
                bannedPlayersJson
            )

            parties[party.id] = party
            rowsAffected > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to add party", e)
        }
    }
    
    override fun update(party: Party): Boolean {
        val sql = """
            UPDATE parties
            SET name = ?, guild_ids = ?, leader_id = ?, status = ?, expires_at = ?, restricted_roles = ?,
                muted_players = ?, banned_players = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val guildIdsString = party.guildIds.joinToString(",") { it.toString() }
            val restrictedRolesString = party.restrictedRoles?.joinToString(",") { it.toString() }
            val mutedPlayersJson = serializeMutedPlayers(party.mutedPlayers)
            val bannedPlayersJson = serializeBannedPlayers(party.bannedPlayers)

            val rowsAffected = storage.connection.executeUpdate(sql,
                party.name,
                guildIdsString,
                party.leaderId.toString(),
                party.status.name,
                party.expiresAt?.toString(),
                restrictedRolesString,
                mutedPlayersJson,
                bannedPlayersJson,
                party.id.toString()
            )

            if (rowsAffected > 0) {
                parties[party.id] = party
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update party", e)
        }
    }
    
    override fun remove(partyId: UUID): Boolean {
        val sql = "DELETE FROM parties WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, partyId.toString())
            
            if (rowsAffected > 0) {
                parties.remove(partyId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to remove party", e)
        }
    }
    
    override fun getById(partyId: UUID): Party? {
        return parties[partyId]
    }
    
    override fun getActivePartiesByGuild(guildId: UUID): Set<Party> {
        return parties.values.filter { party ->
            party.includesGuild(guildId) && party.isActive()
        }.toSet()
    }

    override fun getAllPartiesForGuild(guildId: UUID): Set<Party> {
        return parties.values.filter { party ->
            party.includesGuild(guildId)
        }.toSet()
    }
    
    override fun getPartiesByLeader(playerId: UUID): Set<Party> {
        return parties.values.filter { it.leaderId == playerId }.toSet()
    }
    
    override fun getPartiesByStatus(status: PartyStatus): Set<Party> {
        return parties.values.filter { it.status == status }.toSet()
    }
    
    override fun getExpiredParties(): Set<Party> {
        val now = Instant.now()
        return parties.values.filter { party ->
            party.expiresAt != null && party.expiresAt.isBefore(now) && 
            party.status == PartyStatus.ACTIVE
        }.toSet()
    }
    
    override fun findPartyByGuilds(guildIds: Set<UUID>): Party? {
        return parties.values.find { party ->
            party.guildIds == guildIds && party.isActive()
        }
    }
    
    override fun getAllActiveParties(): Set<Party> {
        return parties.values.filter { it.isActive() }.toSet()
    }
    
    override fun getAll(): Set<Party> {
        return parties.values.toSet()
    }

    /**
     * Parses the muted_players JSON string into a Map<UUID, Instant?>.
     * Format: {"playerId": "epochSeconds"} or {"playerId": null}
     * Returns empty map if parsing fails.
     */
    private fun parseMutedPlayersJson(json: String): Map<UUID, Instant?> {
        return try {
            if (json.isBlank() || json == "{}") return emptyMap()

            val type = object : TypeToken<Map<String, Long?>>() {}.type
            val jsonMap: Map<String, Long?> = gson.fromJson(json, type)

            val result = mutableMapOf<UUID, Instant?>()
            for ((key, value) in jsonMap) {
                val playerId = UUID.fromString(key)
                val expiration = value?.let { Instant.ofEpochSecond(it) }
                result[playerId] = expiration
            }

            result
        } catch (e: Exception) {
            // Log error but don't fail - return empty map for graceful degradation
            System.err.println("Failed to parse muted_players JSON: $json - ${e.message}")
            emptyMap()
        }
    }

    /**
     * Parses the banned_players JSON string into a Set<UUID>.
     * Format: ["playerId1", "playerId2"]
     * Returns empty set if parsing fails.
     */
    private fun parseBannedPlayersJson(json: String): Set<UUID> {
        return try {
            if (json.isBlank() || json == "[]") return emptySet()

            val type = object : TypeToken<List<String>>() {}.type
            val jsonList: List<String> = gson.fromJson(json, type)
            jsonList.map { UUID.fromString(it) }.toSet()
        } catch (e: Exception) {
            // Log error but don't fail - return empty set for graceful degradation
            System.err.println("Failed to parse banned_players JSON: $json - ${e.message}")
            emptySet()
        }
    }

    /**
     * Serializes the muted_players map into a JSON string.
     * Format: {"playerId": "epochSeconds"} or {"playerId": null}
     */
    private fun serializeMutedPlayers(mutes: Map<UUID, Instant?>): String {
        return try {
            if (mutes.isEmpty()) return "{}"

            val jsonObject = mutableMapOf<String, Long?>()
            for ((playerId, expiration) in mutes) {
                jsonObject[playerId.toString()] = expiration?.epochSecond
            }

            gson.toJson(jsonObject)
        } catch (e: Exception) {
            System.err.println("Failed to serialize muted_players: ${e.message}")
            "{}"
        }
    }

    /**
     * Serializes the banned_players set into a JSON array string.
     * Format: ["playerId1", "playerId2"]
     */
    private fun serializeBannedPlayers(bans: Set<UUID>): String {
        return try {
            if (bans.isEmpty()) return "[]"

            val playerIds = bans.map { it.toString() }
            gson.toJson(playerIds)
        } catch (e: Exception) {
            System.err.println("Failed to serialize banned_players: ${e.message}")
            "[]"
        }
    }
}
