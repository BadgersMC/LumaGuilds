package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.DiplomaticRelationRepository
import net.lumalyte.lg.domain.entities.DiplomaticRelation
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class DiplomaticRelationRepositorySQLite(private val storage: SQLiteStorage) : DiplomaticRelationRepository {

    private val logger = LoggerFactory.getLogger(DiplomaticRelationRepositorySQLite::class.java)

    private val relations: MutableMap<UUID, DiplomaticRelation> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            logger.info("Initializing diplomatic relations database...")
            try {
                createDiplomaticRelationsTable()
                preload()
                isInitialized = true
                logger.info("Diplomatic relations database initialized successfully")
            } catch (e: SQLException) {
                logger.error("Failed to initialize diplomatic relations database: ${e.message}", e)
                throw DatabaseOperationException("Failed to initialize diplomatic relations database: ${e.message}", e)
            }
        }
    }

    private fun createDiplomaticRelationsTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS diplomatic_relations (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                target_guild_id TEXT NOT NULL,
                type TEXT NOT NULL,
                established_at TEXT NOT NULL,
                expires_at TEXT,
                metadata TEXT NOT NULL DEFAULT '{}',
                UNIQUE(guild_id, target_guild_id),
                CHECK(guild_id < target_guild_id)
            )
        """.trimIndent()

        try {
            logger.info("Creating diplomatic_relations table...")
            storage.connection.executeUpdate(sql)
            logger.info("Successfully created diplomatic_relations table")
        } catch (e: SQLException) {
            logger.error("Failed to create diplomatic_relations table: ${e.message}", e)
            throw DatabaseOperationException("Failed to create diplomatic_relations table: ${e.message}", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT id, guild_id, target_guild_id, type, established_at, expires_at, metadata
            FROM diplomatic_relations
        """.trimIndent()

        try {
            logger.debug("Preloading diplomatic relations from database...")
            val results = storage.connection.getResults(sql)
            var count = 0
            for (result in results) {
                val relation = mapResultSetToDiplomaticRelation(result)
                relations[relation.id] = relation
                count++
            }
            logger.info("Successfully preloaded $count diplomatic relations from database")
        } catch (e: SQLException) {
            logger.error("Failed to preload diplomatic relations: ${e.message}", e)
            throw DatabaseOperationException("Failed to preload diplomatic relations: ${e.message}", e)
        }
    }

    private fun mapResultSetToDiplomaticRelation(rs: co.aikar.idb.DbRow): DiplomaticRelation {
        return DiplomaticRelation(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            targetGuildId = UUID.fromString(rs.getString("target_guild_id")),
            type = DiplomaticRelationType.valueOf(rs.getString("type")),
            establishedAt = Instant.parse(rs.getString("established_at")),
            expiresAt = rs.getString("expires_at")?.let { Instant.parse(it) },
            metadata = rs.getString("metadata")?.let {
                // Simple JSON parsing for metadata - in production this should use a proper JSON library
                emptyMap() // Placeholder for now
            } ?: emptyMap()
        )
    }

    override fun add(relation: DiplomaticRelation): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO diplomatic_relations (id, guild_id, target_guild_id, type, established_at, expires_at, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                relation.id.toString(),
                relation.guildId.toString(),
                relation.targetGuildId.toString(),
                relation.type.name,
                relation.establishedAt.toString(),
                relation.expiresAt?.toString(),
                "{}" // Placeholder for metadata serialization
            )

            relations[relation.id] = relation
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to add diplomatic relation: ${e.message}", e)
            throw DatabaseOperationException("Failed to add diplomatic relation", e)
        }
    }

    override fun update(relation: DiplomaticRelation): Boolean {
        val sql = """
            UPDATE diplomatic_relations
            SET guild_id = ?, target_guild_id = ?, type = ?, established_at = ?, expires_at = ?, metadata = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                relation.guildId.toString(),
                relation.targetGuildId.toString(),
                relation.type.name,
                relation.establishedAt.toString(),
                relation.expiresAt?.toString(),
                "{}",
                relation.id.toString()
            )

            if (rowsAffected > 0) {
                relations[relation.id] = relation
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to update diplomatic relation: ${e.message}", e)
            throw DatabaseOperationException("Failed to update diplomatic relation", e)
        }
    }

    override fun remove(relationId: UUID): Boolean {
        val sql = "DELETE FROM diplomatic_relations WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, relationId.toString())

            if (rowsAffected > 0) {
                relations.remove(relationId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to remove diplomatic relation: ${e.message}", e)
            throw DatabaseOperationException("Failed to remove diplomatic relation", e)
        }
    }

    override fun getById(relationId: UUID): DiplomaticRelation? {
        return relations[relationId]
    }

    override fun getByGuilds(guildA: UUID, guildB: UUID): DiplomaticRelation? {
        // Normalize guild order for consistent lookup
        val (firstGuild, secondGuild) = if (guildA.toString() < guildB.toString()) {
            guildA to guildB
        } else {
            guildB to guildA
        }

        return relations.values.find {
            it.guildId == firstGuild && it.targetGuildId == secondGuild
        }
    }

    override fun getByGuild(guildId: UUID): List<DiplomaticRelation> {
        ensureInitialized()
        return relations.values.filter { it.involves(guildId) }
    }

    override fun getByGuildAndType(guildId: UUID, type: DiplomaticRelationType): List<DiplomaticRelation> {
        return relations.values.filter {
            it.involves(guildId) && it.type == type
        }
    }

    override fun getActiveRelations(guildId: UUID): List<DiplomaticRelation> {
        return relations.values.filter { it.involves(guildId) && it.isActive() }
    }

    override fun getExpiredRelations(): List<DiplomaticRelation> {
        val now = Instant.now()
        return relations.values.filter { relation ->
            relation.expiresAt != null && relation.expiresAt.isBefore(now) && relation.isActive()
        }
    }

    override fun getActiveRelationsByType(guildId: UUID, type: DiplomaticRelationType): List<DiplomaticRelation> {
        return relations.values.filter {
            it.involves(guildId) && it.type == type && it.isActive()
        }
    }

    override fun hasRelationType(guildA: UUID, guildB: UUID, type: DiplomaticRelationType): Boolean {
        val relation = getByGuilds(guildA, guildB)
        return relation?.type == type && relation.isActive()
    }

    override fun getAll(): List<DiplomaticRelation> {
        return relations.values.toList()
    }

    private fun DiplomaticRelation.involves(guildId: UUID): Boolean {
        return this.guildId == guildId || this.targetGuildId == guildId
    }
}
