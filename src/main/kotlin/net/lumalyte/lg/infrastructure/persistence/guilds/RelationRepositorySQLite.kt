package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class RelationRepositorySQLite(private val storage: SQLiteStorage) : RelationRepository {
    
    private val relations: MutableMap<UUID, Relation> = mutableMapOf()
    
    init {
        createRelationTable()
        preload()
    }
    
    private fun createRelationTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS relations (
                id TEXT PRIMARY KEY,
                guild_a TEXT NOT NULL,
                guild_b TEXT NOT NULL,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                expires_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                UNIQUE(guild_a, guild_b),
                CHECK(guild_a < guild_b)
            )
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create relations table", e)
        }
    }
    
    private fun preload() {
        val sql = """
            SELECT id, guild_a, guild_b, type, status, expires_at, created_at, updated_at
            FROM relations
        """.trimIndent()
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val relation = mapResultSetToRelation(result)
                relations[relation.id] = relation
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload relations", e)
        }
    }
    
    private fun mapResultSetToRelation(rs: co.aikar.idb.DbRow): Relation {
        return Relation(
            id = UUID.fromString(rs.getString("id")),
            guildA = UUID.fromString(rs.getString("guild_a")),
            guildB = UUID.fromString(rs.getString("guild_b")),
            type = RelationType.valueOf(rs.getString("type")),
            status = RelationStatus.valueOf(rs.getString("status")),
            expiresAt = rs.getString("expires_at")?.let { Instant.parse(it) },
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
    }
    
    override fun add(relation: Relation): Boolean {
        val sql = """
            INSERT INTO relations (id, guild_a, guild_b, type, status, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                relation.id.toString(),
                relation.guildA.toString(),
                relation.guildB.toString(),
                relation.type.name,
                relation.status.name,
                relation.expiresAt?.toString(),
                relation.createdAt.toString(),
                relation.updatedAt.toString()
            )
            
            relations[relation.id] = relation
            rowsAffected > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to add relation", e)
        }
    }
    
    override fun update(relation: Relation): Boolean {
        val sql = """
            UPDATE relations 
            SET guild_a = ?, guild_b = ?, type = ?, status = ?, expires_at = ?, updated_at = ?
            WHERE id = ?
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                relation.guildA.toString(),
                relation.guildB.toString(),
                relation.type.name,
                relation.status.name,
                relation.expiresAt?.toString(),
                relation.updatedAt.toString(),
                relation.id.toString()
            )
            
            if (rowsAffected > 0) {
                relations[relation.id] = relation
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update relation", e)
        }
    }
    
    override fun remove(relationId: UUID): Boolean {
        val sql = "DELETE FROM relations WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, relationId.toString())
            
            if (rowsAffected > 0) {
                relations.remove(relationId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to remove relation", e)
        }
    }
    
    override fun getById(relationId: UUID): Relation? {
        return relations[relationId]
    }
    
    override fun getByGuilds(guildA: UUID, guildB: UUID): Relation? {
        // Normalize guild order for consistent lookup
        val (firstGuild, secondGuild) = if (guildA.toString() < guildB.toString()) {
            guildA to guildB
        } else {
            guildB to guildA
        }
        
        return relations.values.find { 
            it.guildA == firstGuild && it.guildB == secondGuild
        }
    }
    
    override fun getByGuild(guildId: UUID): Set<Relation> {
        return relations.values.filter { it.involves(guildId) }.toSet()
    }
    
    override fun getByGuildAndType(guildId: UUID, type: RelationType): Set<Relation> {
        return relations.values.filter { 
            it.involves(guildId) && it.type == type && it.isActive()
        }.toSet()
    }
    
    override fun getByGuildAndStatus(guildId: UUID, status: RelationStatus): Set<Relation> {
        return relations.values.filter { 
            it.involves(guildId) && it.status == status
        }.toSet()
    }
    
    override fun getExpiredRelations(): Set<Relation> {
        val now = Instant.now()
        return relations.values.filter { relation ->
            relation.expiresAt != null && relation.expiresAt.isBefore(now) && 
            relation.status == RelationStatus.ACTIVE
        }.toSet()
    }
    
    override fun getByType(type: RelationType): Set<Relation> {
        return relations.values.filter { 
            it.type == type && it.isActive()
        }.toSet()
    }
    
    override fun getByStatus(status: RelationStatus): Set<Relation> {
        return relations.values.filter { it.status == status }.toSet()
    }
    
    override fun hasRelationType(guildA: UUID, guildB: UUID, type: RelationType): Boolean {
        val relation = getByGuilds(guildA, guildB)
        return relation?.type == type && relation.isActive()
    }
    
    override fun getAll(): Set<Relation> {
        return relations.values.toSet()
    }
}
