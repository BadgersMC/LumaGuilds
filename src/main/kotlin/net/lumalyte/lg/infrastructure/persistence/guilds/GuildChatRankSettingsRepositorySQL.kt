package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildChatRankSettingsRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildChatRankSettingsSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuildChatRankSettingsRepositorySQL(private val storage: Storage<Database>) : GuildChatRankSettingsRepository {
    private val settings = ConcurrentHashMap<UUID, Boolean>()
    private val logger = LoggerFactory.getLogger(GuildChatRankSettingsRepositorySQL::class.java)
    private val upsert = if (storage.dialect == SqlDialect.MARIADB) {
        "INSERT INTO guild_chat_rank_settings (guild_id, ranks_visible) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE ranks_visible = VALUES(ranks_visible)"
    } else {
        "INSERT INTO guild_chat_rank_settings (guild_id, ranks_visible) VALUES (?, ?) " +
            "ON CONFLICT(guild_id) DO UPDATE SET ranks_visible = excluded.ranks_visible"
    }
    init {
        storage.connection.connection.use {
            GuildChatRankSettingsSchema.create(it, storage.dialect == SqlDialect.MARIADB)
        }
        storage.connection.getResults("SELECT guild_id, ranks_visible FROM guild_chat_rank_settings").forEach {
            settings[UUID.fromString(it.getString("guild_id"))] = (it.get("ranks_visible") as Number).toInt() == 1
        }
    }
    override fun ranksVisible(guildId: UUID): Boolean = settings[guildId] ?: true
    @Synchronized
    override fun setRanksVisible(guildId: UUID, visible: Boolean): Boolean = try {
        storage.connection.executeUpdate(upsert, guildId.toString(), if (visible) 1 else 0)
        settings[guildId] = visible
        true
    } catch (error: SQLException) {
        logger.error("Failed to persist guild-chat rank setting for $guildId", error)
        false
    }
}
