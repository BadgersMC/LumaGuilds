package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database

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
            SELECT id, name, guild_ids, leader_id, status, created_at, expires_at, restricted_roles
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

        return Party(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            guildIds = guildIds,
            leaderId = UUID.fromString(rs.getString("leader_id")),
            status = PartyStatus.valueOf(rs.getString("status")),
            createdAt = rs.getInstantNotNull("created_at"),
            expiresAt = rs.getInstant("expires_at"),
            restrictedRoles = restrictedRoles
        )
    }
    
    override fun add(party: Party): Boolean {
        val sql = """
            INSERT INTO parties (id, name, guild_ids, leader_id, status, created_at, expires_at, restricted_roles)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val guildIdsString = party.guildIds.joinToString(",") { it.toString() }
            val restrictedRolesString = party.restrictedRoles?.joinToString(",") { it.toString() }
            val rowsAffected = storage.connection.executeUpdate(sql,
                party.id.toString(),
                party.name,
                guildIdsString,
                party.leaderId.toString(),
                party.status.name,
                party.createdAt.toString(),
                party.expiresAt?.toString(),
                restrictedRolesString
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
            SET name = ?, guild_ids = ?, leader_id = ?, status = ?, expires_at = ?, restricted_roles = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val guildIdsString = party.guildIds.joinToString(",") { it.toString() }
            val restrictedRolesString = party.restrictedRoles?.joinToString(",") { it.toString() }
            val rowsAffected = storage.connection.executeUpdate(sql,
                party.name,
                guildIdsString,
                party.leaderId.toString(),
                party.status.name,
                party.expiresAt?.toString(),
                restrictedRolesString,
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
}
