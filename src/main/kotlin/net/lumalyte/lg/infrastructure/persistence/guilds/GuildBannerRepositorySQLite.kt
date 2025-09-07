package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.services.BannerDesignData
import net.lumalyte.lg.application.services.BannerColor
import net.lumalyte.lg.application.services.BannerPattern
import net.lumalyte.lg.domain.entities.GuildBanner
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * SQLite implementation of GuildBannerRepository.
 */
class GuildBannerRepositorySQLite(
    private val storage: SQLiteStorage
) : GuildBannerRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        createTable()
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_banners (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                name TEXT,
                base_color TEXT NOT NULL,
                patterns TEXT NOT NULL,
                submitted_by TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (guild_id) REFERENCES guilds (id)
            )
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
        } catch (e: Exception) {
            logger.error("Failed to create guild_banners table", e)
        }
    }

    override fun save(banner: GuildBanner): Boolean {
        val sql = """
            INSERT OR REPLACE INTO guild_banners 
            (id, guild_id, name, base_color, patterns, submitted_by, created_at, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(
                sql,
                banner.id.toString(),
                banner.guildId.toString(),
                banner.name,
                banner.designData.baseColor.name,
                serializePatterns(banner.designData.patterns),
                banner.submittedBy.toString(),
                banner.createdAt.epochSecond,
                if (banner.isActive) 1 else 0
            )
            rowsAffected > 0
        } catch (e: Exception) {
            logger.error("Failed to save banner", e)
            false
        }
    }

    override fun getActiveBanner(guildId: UUID): GuildBanner? {
        val sql = "SELECT * FROM guild_banners WHERE guild_id = ? AND is_active = 1 LIMIT 1"
        
        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            if (results.isNotEmpty()) {
                results.first().toGuildBanner()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to get active banner", e)
            null
        }
    }

    override fun getBannersByGuild(guildId: UUID): List<GuildBanner> {
        val sql = "SELECT * FROM guild_banners WHERE guild_id = ? ORDER BY created_at DESC"
        
        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            results.mapNotNull { it.toGuildBanner() }
        } catch (e: Exception) {
            logger.error("Failed to get banners by guild", e)
            emptyList()
        }
    }

    override fun getById(bannerId: UUID): GuildBanner? {
        val sql = "SELECT * FROM guild_banners WHERE id = ?"
        
        return try {
            val results = storage.connection.getResults(sql, bannerId.toString())
            if (results.isNotEmpty()) {
                results.first().toGuildBanner()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to get banner by ID", e)
            null
        }
    }

    override fun removeActiveBanner(guildId: UUID): Boolean {
        val sql = "UPDATE guild_banners SET is_active = 0 WHERE guild_id = ? AND is_active = 1"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, guildId.toString())
            rowsAffected > 0
        } catch (e: Exception) {
            logger.error("Failed to remove active banner", e)
            false
        }
    }

    override fun delete(bannerId: UUID): Boolean {
        val sql = "DELETE FROM guild_banners WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, bannerId.toString())
            rowsAffected > 0
        } catch (e: Exception) {
            logger.error("Failed to delete banner", e)
            false
        }
    }

    override fun setActiveBanner(guildId: UUID, bannerId: UUID): Boolean {
        return try {
            // First, deactivate all banners for this guild
            storage.connection.executeUpdate("UPDATE guild_banners SET is_active = 0 WHERE guild_id = ?", guildId.toString())

            // Then activate the specified banner
            val rowsAffected = storage.connection.executeUpdate(
                "UPDATE guild_banners SET is_active = 1 WHERE id = ? AND guild_id = ?",
                bannerId.toString(),
                guildId.toString()
            )
            rowsAffected > 0
        } catch (e: Exception) {
            logger.error("Failed to set active banner", e)
            false
        }
    }

    private fun Map<String, Any>.toGuildBanner(): GuildBanner? {
        return try {
            val id = UUID.fromString(this["id"] as String)
            val guildId = UUID.fromString(this["guild_id"] as String)
            val name = this["name"] as String?
            val baseColor = BannerColor.valueOf(this["base_color"] as String)
            val patterns = deserializePatterns(this["patterns"] as String)
            val submittedBy = UUID.fromString(this["submitted_by"] as String)
            val createdAt = Instant.ofEpochSecond((this["created_at"] as Long))
            val isActive = (this["is_active"] as Long) == 1L

            val designData = BannerDesignData(baseColor, patterns)
            
            GuildBanner(
                id = id,
                guildId = guildId,
                name = name,
                designData = designData,
                submittedBy = submittedBy,
                createdAt = createdAt,
                isActive = isActive
            )
        } catch (e: Exception) {
            logger.error("Error converting result map to GuildBanner", e)
            null
        }
    }

    private fun serializePatterns(patterns: List<BannerPattern>): String {
        return patterns.joinToString("|") { "${it.type}:${it.color.name}" }
    }

    private fun deserializePatterns(patternsString: String): List<BannerPattern> {
        if (patternsString.isBlank()) return emptyList()
        
        return patternsString.split("|").mapNotNull { patternStr ->
            try {
                val parts = patternStr.split(":")
                if (parts.size == 2) {
                    val type = parts[0]
                    val color = BannerColor.valueOf(parts[1])
                    BannerPattern(type, color)
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.warn("Failed to deserialize pattern: $patternStr", e)
                null
            }
        }
    }
}
