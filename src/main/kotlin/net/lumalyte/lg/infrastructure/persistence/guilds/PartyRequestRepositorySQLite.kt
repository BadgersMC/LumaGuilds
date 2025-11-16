package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.PartyRequestStatus
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PartyRequestRepositorySQLite(private val storage: Storage<Database>) : PartyRequestRepository {
    
    private val requests: MutableMap<UUID, PartyRequest> = mutableMapOf()
    
    init {
        createPartyRequestTable()
        preload()
    }
    
    private fun createPartyRequestTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS party_requests (
                id TEXT PRIMARY KEY,
                from_guild_id TEXT NOT NULL,
                to_guild_id TEXT NOT NULL,
                requester_id TEXT NOT NULL,
                message TEXT,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL
            )
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create party_requests table", e)
        }
    }
    
    private fun preload() {
        val sql = """
            SELECT id, from_guild_id, to_guild_id, requester_id, message, status, created_at, expires_at
            FROM party_requests
        """.trimIndent()
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val request = mapResultSetToRequest(result)
                requests[request.id] = request
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload party requests", e)
        }
    }
    
    private fun mapResultSetToRequest(rs: co.aikar.idb.DbRow): PartyRequest {
        return PartyRequest(
            id = UUID.fromString(rs.getString("id")),
            fromGuildId = UUID.fromString(rs.getString("from_guild_id")),
            toGuildId = UUID.fromString(rs.getString("to_guild_id")),
            requesterId = UUID.fromString(rs.getString("requester_id")),
            message = rs.getString("message"),
            status = PartyRequestStatus.valueOf(rs.getString("status")),
            createdAt = rs.getInstantNotNull("created_at"),
            expiresAt = rs.getInstantNotNull("expires_at")
        )
    }
    
    override fun add(request: PartyRequest): Boolean {
        val sql = """
            INSERT INTO party_requests (id, from_guild_id, to_guild_id, requester_id, message, status, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                request.id.toString(),
                request.fromGuildId.toString(),
                request.toGuildId.toString(),
                request.requesterId.toString(),
                request.message,
                request.status.name,
                request.createdAt.toString(),
                request.expiresAt.toString()
            )
            
            requests[request.id] = request
            rowsAffected > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to add party request", e)
        }
    }
    
    override fun update(request: PartyRequest): Boolean {
        val sql = """
            UPDATE party_requests 
            SET from_guild_id = ?, to_guild_id = ?, requester_id = ?, message = ?, status = ?, expires_at = ?
            WHERE id = ?
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                request.fromGuildId.toString(),
                request.toGuildId.toString(),
                request.requesterId.toString(),
                request.message,
                request.status.name,
                request.expiresAt.toString(),
                request.id.toString()
            )
            
            if (rowsAffected > 0) {
                requests[request.id] = request
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update party request", e)
        }
    }
    
    override fun remove(requestId: UUID): Boolean {
        val sql = "DELETE FROM party_requests WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, requestId.toString())
            
            if (rowsAffected > 0) {
                requests.remove(requestId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to remove party request", e)
        }
    }
    
    override fun getById(requestId: UUID): PartyRequest? {
        return requests[requestId]
    }
    
    override fun getPendingRequestsForGuild(guildId: UUID): Set<PartyRequest> {
        return requests.values.filter { 
            it.toGuildId == guildId && it.status == PartyRequestStatus.PENDING
        }.toSet()
    }
    
    override fun getPendingRequestsFromGuild(guildId: UUID): Set<PartyRequest> {
        return requests.values.filter { 
            it.fromGuildId == guildId && it.status == PartyRequestStatus.PENDING
        }.toSet()
    }
    
    override fun getRequestsByRequester(playerId: UUID): Set<PartyRequest> {
        return requests.values.filter { it.requesterId == playerId }.toSet()
    }
    
    override fun getRequestsByStatus(status: PartyRequestStatus): Set<PartyRequest> {
        return requests.values.filter { it.status == status }.toSet()
    }
    
    override fun getExpiredRequests(): Set<PartyRequest> {
        val now = Instant.now()
        return requests.values.filter { request ->
            request.expiresAt.isBefore(now) && request.status == PartyRequestStatus.PENDING
        }.toSet()
    }
    
    override fun findActiveRequestBetweenGuilds(fromGuildId: UUID, toGuildId: UUID): PartyRequest? {
        return requests.values.find { request ->
            request.fromGuildId == fromGuildId && 
            request.toGuildId == toGuildId && 
            request.status == PartyRequestStatus.PENDING &&
            request.isValid()
        }
    }
    
    override fun getAll(): Set<PartyRequest> {
        return requests.values.toSet()
    }
}
