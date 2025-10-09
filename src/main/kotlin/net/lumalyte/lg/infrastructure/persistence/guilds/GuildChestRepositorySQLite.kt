package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildChestRepository
import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestContents
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class GuildChestRepositorySQLite(private val storage: SQLiteStorage) : GuildChestRepository {

    private val logger = LoggerFactory.getLogger(GuildChestRepositorySQLite::class.java)

    private val chests: MutableMap<UUID, GuildChest> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            logger.info("Initializing guild chest database...")
            try {
                createGuildChestTable()
                preload()
                isInitialized = true
                logger.info("Guild chest database initialized successfully")
            } catch (e: SQLException) {
                logger.error("Failed to initialize guild chest database: ${e.message}", e)
                throw DatabaseOperationException("Failed to initialize guild chest database: ${e.message}", e)
            }
        }
    }

    private fun createGuildChestTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_chests (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                world_id TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                chest_size INTEGER NOT NULL DEFAULT 54,
                max_size INTEGER NOT NULL DEFAULT 270,
                is_locked BOOLEAN NOT NULL DEFAULT 0,
                last_accessed TEXT NOT NULL,
                created_at TEXT NOT NULL,
                metadata TEXT NOT NULL DEFAULT '{}',
                UNIQUE(guild_id, world_id, x, y, z),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent()

        try {
            logger.info("Creating guild_chests table...")
            storage.connection.executeUpdate(sql)
            logger.info("Successfully created guild_chests table")
        } catch (e: SQLException) {
            logger.error("Failed to create guild_chests table: ${e.message}", e)
            throw DatabaseOperationException("Failed to create guild_chests table: ${e.message}", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT id, guild_id, world_id, x, y, z, chest_size, max_size, is_locked, last_accessed, created_at, metadata
            FROM guild_chests
        """.trimIndent()

        try {
            logger.debug("Preloading guild chests from database...")
            val results = storage.connection.getResults(sql)
            var count = 0
            for (result in results) {
                val chest = mapResultSetToGuildChest(result)
                chests[chest.id] = chest
                count++
            }
            logger.info("Successfully preloaded $count guild chests from database")
        } catch (e: SQLException) {
            logger.error("Failed to preload guild chests: ${e.message}", e)
            throw DatabaseOperationException("Failed to preload guild chests: ${e.message}", e)
        }
    }

    private fun mapResultSetToGuildChest(rs: co.aikar.idb.DbRow): GuildChest {
        return GuildChest(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            worldId = UUID.fromString(rs.getString("world_id")),
            location = Position3D(rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
            chestSize = rs.getInt("chest_size"),
            maxSize = rs.getInt("max_size"),
            isLocked = rs.getInt("is_locked") == 1,
            lastAccessed = Instant.parse(rs.getString("last_accessed")),
            createdAt = Instant.parse(rs.getString("created_at")),
            metadata = rs.getString("metadata")?.let {
                // Simple JSON parsing for metadata - in production this should use a proper JSON library
                emptyMap() // Placeholder for now
            } ?: emptyMap()
        )
    }

    override fun add(chest: GuildChest): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO guild_chests (id, guild_id, world_id, x, y, z, chest_size, max_size, is_locked, last_accessed, created_at, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                chest.id.toString(),
                chest.guildId.toString(),
                chest.worldId.toString(),
                chest.location.x,
                chest.location.y,
                chest.location.z,
                chest.chestSize,
                chest.maxSize,
                if (chest.isLocked) 1 else 0,
                chest.lastAccessed.toString(),
                chest.createdAt.toString(),
                "{}" // Placeholder for metadata serialization
            )

            chests[chest.id] = chest
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to add guild chest: ${e.message}", e)
            throw DatabaseOperationException("Failed to add guild chest", e)
        }
    }

    override fun update(chest: GuildChest): Boolean {
        val sql = """
            UPDATE guild_chests
            SET guild_id = ?, world_id = ?, x = ?, y = ?, z = ?, chest_size = ?, max_size = ?, is_locked = ?, last_accessed = ?, metadata = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                chest.guildId.toString(),
                chest.worldId.toString(),
                chest.location.x,
                chest.location.y,
                chest.location.z,
                chest.chestSize,
                chest.maxSize,
                if (chest.isLocked) 1 else 0,
                chest.lastAccessed.toString(),
                "{}",
                chest.id.toString()
            )

            if (rowsAffected > 0) {
                chests[chest.id] = chest
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to update guild chest: ${e.message}", e)
            throw DatabaseOperationException("Failed to update guild chest", e)
        }
    }

    override fun remove(chestId: UUID): Boolean {
        val sql = "DELETE FROM guild_chests WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, chestId.toString())

            if (rowsAffected > 0) {
                chests.remove(chestId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to remove guild chest: ${e.message}", e)
            throw DatabaseOperationException("Failed to remove guild chest", e)
        }
    }

    override fun getById(chestId: UUID): GuildChest? {
        return chests[chestId]
    }

    override fun getByGuild(guildId: UUID): List<GuildChest> {
        ensureInitialized()
        return chests.values.filter { it.guildId == guildId }
    }

    override fun getByLocation(worldId: UUID, x: Int, y: Int, z: Int): GuildChest? {
        return chests.values.find { it.worldId == worldId && it.location.x == x && it.location.y == y && it.location.z == z }
    }

    override fun getByWorld(worldId: UUID): List<GuildChest> {
        return chests.values.filter { it.worldId == worldId }
    }

    override fun getAll(): List<GuildChest> {
        return chests.values.toList()
    }

    override fun hasChestAtLocation(guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int): Boolean {
        return chests.values.any {
            it.guildId == guildId &&
            it.worldId == worldId &&
            it.location.x == x &&
            it.location.y == y &&
            it.location.z == z
        }
    }

    override fun getChestContents(chestId: UUID): GuildChestContents? {
        val sql = """
            SELECT items, last_updated
            FROM guild_chest_contents
            WHERE chest_id = ?
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, chestId.toString())
            if (result != null) {
                val itemsString = result.getString("items")
                val lastUpdated = Instant.parse(result.getString("last_updated"))

                // Deserialize items from JSON string
                val items = deserializeItems(itemsString)
                GuildChestContents(chestId, items, lastUpdated)
            } else {
                null
            }
        } catch (e: SQLException) {
            logger.error("Error getting chest contents for chest $chestId", e)
            null
        }
    }

    override fun updateChestContents(contents: GuildChestContents): Boolean {
        val sql = """
            INSERT OR REPLACE INTO guild_chest_contents (chest_id, items, last_updated)
            VALUES (?, ?, ?)
        """.trimIndent()

        return try {
            val itemsString = serializeItems(contents.items)
            val rowsAffected = storage.connection.executeUpdate(sql,
                contents.chestId.toString(),
                itemsString,
                contents.lastUpdated.toString()
            )
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Error updating chest contents for chest ${contents.chestId}", e)
            false
        }
    }

    override fun removeChestContents(chestId: UUID): Boolean {
        val sql = "DELETE FROM guild_chest_contents WHERE chest_id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, chestId.toString())
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Error removing chest contents for chest $chestId", e)
            false
        }
    }

    override fun getInactiveChests(olderThan: java.time.Instant): List<GuildChest> {
        return chests.values.filter { it.lastAccessed.isBefore(olderThan) }
    }

    override fun updateLastAccessed(chestId: UUID, accessTime: java.time.Instant): Boolean {
        val chest = chests[chestId]
        if (chest != null) {
            val updatedChest = chest.copy(lastAccessed = accessTime)
            return update(updatedChest)
        }
        return false
    }

    // Helper methods for item serialization
    private fun serializeItems(items: Map<Int, String>): String {
        // Convert the map to a JSON-like string
        return items.entries.joinToString(";") { (slot, itemString) ->
            "$slot:$itemString"
        }
    }

    private fun deserializeItems(itemsString: String): Map<Int, String> {
        if (itemsString.isEmpty()) return emptyMap()

        return itemsString.split(";")
            .filter { it.contains(":") }
            .associate {
                val parts = it.split(":", limit = 2)
                parts[0].toInt() to parts[1]
            }
    }
}
