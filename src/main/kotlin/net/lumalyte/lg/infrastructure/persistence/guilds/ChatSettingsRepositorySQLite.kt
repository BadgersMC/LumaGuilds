package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.domain.values.ChatRateLimit
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID

class ChatSettingsRepositorySQLite(
    private val storage: Storage<Database>,
    private val defaultChannelVisibility: Boolean = true,
) : ChatSettingsRepository {
    private val visibilitySettings: MutableMap<UUID, ChatVisibilitySettings> = java.util.concurrent.ConcurrentHashMap()
    private val rateLimits: MutableMap<UUID, ChatRateLimit> = java.util.concurrent.ConcurrentHashMap()

    init {
        createChatSettingsTables()
        preload()
    }

    private fun createChatSettingsTables() {
        val visibilityTableSql =
            """
            CREATE TABLE IF NOT EXISTS chat_visibility_settings (
                player_id VARCHAR(36) PRIMARY KEY,
                guild_chat_visible INTEGER NOT NULL DEFAULT 1,
                ally_chat_visible INTEGER NOT NULL DEFAULT 1,
                party_chat_visible INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()

        val rateLimitTableSql =
            """
            CREATE TABLE IF NOT EXISTS chat_rate_limits (
                player_id VARCHAR(36) PRIMARY KEY,
                last_announce_time BIGINT NOT NULL DEFAULT 0,
                last_ping_time BIGINT NOT NULL DEFAULT 0,
                announce_count INTEGER NOT NULL DEFAULT 0,
                ping_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()

        try {
            storage.connection.executeUpdate(visibilityTableSql)
            storage.connection.executeUpdate(
                "CREATE TABLE IF NOT EXISTS chat_ui_preferences (player_id VARCHAR(36) PRIMARY KEY, global_chat_visible INTEGER NOT NULL DEFAULT 1, destination_indicator INTEGER NOT NULL DEFAULT 0)",
            )
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
        val sql =
            """
            SELECT v.player_id, v.guild_chat_visible, v.ally_chat_visible, v.party_chat_visible,
                COALESCE(u.global_chat_visible, 1) AS global_chat_visible, COALESCE(u.destination_indicator, 0) AS destination_indicator
            FROM chat_visibility_settings v LEFT JOIN chat_ui_preferences u ON u.player_id = v.player_id
            """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val settings =
                    ChatVisibilitySettings(
                        playerId = playerId,
                        guildChatVisible = result.getInt("guild_chat_visible") == 1,
                        allyChatVisible = result.getInt("ally_chat_visible") == 1,
                        partyChatVisible = result.getInt("party_chat_visible") == 1,
                        globalChatVisible = result.getInt("global_chat_visible") == 1,
                        destinationIndicator = result.getInt("destination_indicator") == 1,
                    )
                visibilitySettings[playerId] = settings
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload chat visibility settings", e)
        }
    }

    private fun preloadRateLimits() {
        val sql =
            """
            SELECT player_id, last_announce_time, last_ping_time, announce_count, ping_count
            FROM chat_rate_limits
            """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val rateLimit =
                    ChatRateLimit(
                        playerId = playerId,
                        // IDB returns small SQLite INTEGER values as Int, including the default 0.
                        // getLong casts to Long and fails when a player has not used one action yet.
                        lastAnnounceTime = (result.get("last_announce_time") as Number).toLong(),
                        lastPingTime = (result.get("last_ping_time") as Number).toLong(),
                        announceCount = result.getInt("announce_count"),
                        pingCount = result.getInt("ping_count"),
                    )
                rateLimits[playerId] = rateLimit
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload chat rate limits", e)
        }
    }

    override fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings =
        visibilitySettings[playerId] ?: ChatVisibilitySettings(
            playerId,
            guildChatVisible = defaultChannelVisibility,
            allyChatVisible = defaultChannelVisibility,
            partyChatVisible = defaultChannelVisibility,
        )

    override fun updateVisibilitySettings(settings: ChatVisibilitySettings): Boolean =
        try {
            storage.connection.connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection
                        .prepareStatement(
                            "REPLACE INTO chat_visibility_settings (player_id, guild_chat_visible, ally_chat_visible, party_chat_visible) VALUES (?, ?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, settings.playerId.toString())
                            bindFlags(statement, listOf(settings.guildChatVisible, settings.allyChatVisible, settings.partyChatVisible))
                            statement.executeUpdate()
                        }
                    connection
                        .prepareStatement(
                            "REPLACE INTO chat_ui_preferences (player_id, global_chat_visible, destination_indicator) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, settings.playerId.toString())
                            bindFlags(statement, listOf(settings.globalChatVisible, settings.destinationIndicator))
                            statement.executeUpdate()
                        }
                    connection.commit()
                } catch (failure: SQLException) {
                    try {
                        connection.rollback()
                    } catch (rollback: SQLException) {
                        failure.addSuppressed(rollback)
                    }
                    throw failure
                }
            }
            visibilitySettings[settings.playerId] = settings
            true
        } catch (failure: SQLException) {
            throw DatabaseOperationException("Failed to update chat visibility settings", failure)
        }

    private fun bindFlags(
        statement: java.sql.PreparedStatement,
        flags: List<Boolean>,
    ) {
        flags.forEachIndexed { index, enabled -> statement.setInt(index + 2, if (enabled) 1 else 0) }
    }

    override fun getRateLimit(playerId: UUID): ChatRateLimit = rateLimits[playerId] ?: ChatRateLimit(playerId)

    override fun updateRateLimit(rateLimit: ChatRateLimit): Boolean {
        val sql =
            """
            REPLACE INTO chat_rate_limits
            (player_id, last_announce_time, last_ping_time, announce_count, ping_count)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

        return try {
            val rowsAffected =
                storage.connection.executeUpdate(
                    sql,
                    rateLimit.playerId.toString(),
                    rateLimit.lastAnnounceTime,
                    rateLimit.lastPingTime,
                    rateLimit.announceCount,
                    rateLimit.pingCount,
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

    override fun getPlayersWithCustomSettings(): Set<UUID> = visibilitySettings.keys.toSet()

    override fun removePlayerSettings(playerId: UUID): Boolean {
        val visibilityDeleteSql = "DELETE FROM chat_visibility_settings WHERE player_id = ?"
        val rateLimitDeleteSql = "DELETE FROM chat_rate_limits WHERE player_id = ?"

        return try {
            storage.connection.executeUpdate("DELETE FROM chat_ui_preferences WHERE player_id = ?", playerId.toString())
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
