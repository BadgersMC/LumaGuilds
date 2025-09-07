package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.domain.values.ChatRateLimit
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.util.UUID

class ChatSettingsRepositorySQLite(private val storage: SQLiteStorage) : ChatSettingsRepository {
    
    private val visibilitySettings: MutableMap<UUID, ChatVisibilitySettings> = mutableMapOf()
    private val rateLimits: MutableMap<UUID, ChatRateLimit> = mutableMapOf()
    
    init {
        createChatSettingsTables()
        preload()
    }
    
    private fun createChatSettingsTables() {
        val visibilityTableSql = """
            CREATE TABLE IF NOT EXISTS chat_visibility_settings (
                player_id TEXT PRIMARY KEY,
                guild_chat_visible INTEGER NOT NULL DEFAULT 1,
                ally_chat_visible INTEGER NOT NULL DEFAULT 1,
                party_chat_visible INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent()
        
        val rateLimitTableSql = """
            CREATE TABLE IF NOT EXISTS chat_rate_limits (
                player_id TEXT PRIMARY KEY,
                last_announce_time INTEGER NOT NULL DEFAULT 0,
                last_ping_time INTEGER NOT NULL DEFAULT 0,
                announce_count INTEGER NOT NULL DEFAULT 0,
                ping_count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(visibilityTableSql)
            storage.connection.executeUpdate(rateLimitTableSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create chat settings tables", e)
        }
    }
    
    private fun preload() {
        preloadVisibilitySettings()
        preloadRateLimits()
    }
    
    private fun preloadVisibilitySettings() {
        val sql = """
            SELECT player_id, guild_chat_visible, ally_chat_visible, party_chat_visible
            FROM chat_visibility_settings
        """.trimIndent()
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val settings = ChatVisibilitySettings(
                    playerId = playerId,
                    guildChatVisible = result.getInt("guild_chat_visible") == 1,
                    allyChatVisible = result.getInt("ally_chat_visible") == 1,
                    partyChatVisible = result.getInt("party_chat_visible") == 1
                )
                visibilitySettings[playerId] = settings
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload chat visibility settings", e)
        }
    }
    
    private fun preloadRateLimits() {
        val sql = """
            SELECT player_id, last_announce_time, last_ping_time, announce_count, ping_count
            FROM chat_rate_limits
        """.trimIndent()
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val rateLimit = ChatRateLimit(
                    playerId = playerId,
                    lastAnnounceTime = result.getLong("last_announce_time"),
                    lastPingTime = result.getLong("last_ping_time"),
                    announceCount = result.getInt("announce_count"),
                    pingCount = result.getInt("ping_count")
                )
                rateLimits[playerId] = rateLimit
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload chat rate limits", e)
        }
    }
    
    override fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings {
        return visibilitySettings[playerId] ?: ChatVisibilitySettings(playerId)
    }
    
    override fun updateVisibilitySettings(settings: ChatVisibilitySettings): Boolean {
        val sql = """
            INSERT OR REPLACE INTO chat_visibility_settings 
            (player_id, guild_chat_visible, ally_chat_visible, party_chat_visible)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                settings.playerId.toString(),
                if (settings.guildChatVisible) 1 else 0,
                if (settings.allyChatVisible) 1 else 0,
                if (settings.partyChatVisible) 1 else 0
            )
            
            if (rowsAffected > 0) {
                visibilitySettings[settings.playerId] = settings
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update chat visibility settings", e)
        }
    }
    
    override fun getRateLimit(playerId: UUID): ChatRateLimit {
        return rateLimits[playerId] ?: ChatRateLimit(playerId)
    }
    
    override fun updateRateLimit(rateLimit: ChatRateLimit): Boolean {
        val sql = """
            INSERT OR REPLACE INTO chat_rate_limits 
            (player_id, last_announce_time, last_ping_time, announce_count, ping_count)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                rateLimit.playerId.toString(),
                rateLimit.lastAnnounceTime,
                rateLimit.lastPingTime,
                rateLimit.announceCount,
                rateLimit.pingCount
            )
            
            if (rowsAffected > 0) {
                rateLimits[rateLimit.playerId] = rateLimit
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update chat rate limit", e)
        }
    }
    
    override fun resetRateLimit(playerId: UUID): Boolean {
        val resetRateLimit = ChatRateLimit(playerId)
        return updateRateLimit(resetRateLimit)
    }
    
    override fun getPlayersWithCustomSettings(): Set<UUID> {
        return visibilitySettings.keys.toSet()
    }
    
    override fun removePlayerSettings(playerId: UUID): Boolean {
        val visibilityDeleteSql = "DELETE FROM chat_visibility_settings WHERE player_id = ?"
        val rateLimitDeleteSql = "DELETE FROM chat_rate_limits WHERE player_id = ?"
        
        return try {
            val visibilityDeleted = storage.connection.executeUpdate(visibilityDeleteSql, playerId.toString()) >= 0
            val rateLimitDeleted = storage.connection.executeUpdate(rateLimitDeleteSql, playerId.toString()) >= 0
            
            visibilitySettings.remove(playerId)
            rateLimits.remove(playerId)
            
            visibilityDeleted && rateLimitDeleted
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to remove player chat settings", e)
        }
    }
}
