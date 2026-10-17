package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildDiscordRoleSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuildDiscordRoleRepositorySQL(
    private val storage: Storage<Database>,
) : GuildDiscordRoleRepository {
    private val logger = LoggerFactory.getLogger(GuildDiscordRoleRepositorySQL::class.java)
    private val links = ConcurrentHashMap<UUID, GuildDiscordRoleLink>()

    private val upsertSql = if (storage.javaClass.simpleName.contains("MariaDB")) {
        """
        INSERT INTO ${GuildDiscordRoleSchema.TABLE} (guild_id, discord_role_id, unlocked_at)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE
            discord_role_id = VALUES(discord_role_id),
            unlocked_at = VALUES(unlocked_at)
        """.trimIndent()
    } else {
        """
        INSERT INTO ${GuildDiscordRoleSchema.TABLE} (guild_id, discord_role_id, unlocked_at)
        VALUES (?, ?, ?)
        ON CONFLICT(guild_id) DO UPDATE SET
            discord_role_id = excluded.discord_role_id,
            unlocked_at = excluded.unlocked_at
        """.trimIndent()
    }

    init {
        preload()
    }

    override fun get(guildId: UUID): GuildDiscordRoleLink? = links[guildId]

    override fun getAll(): List<GuildDiscordRoleLink> = links.values.toList()

    override fun upsert(link: GuildDiscordRoleLink): Boolean = try {
        storage.connection.executeUpdate(
            upsertSql,
            link.guildId.toString(),
            link.discordRoleId,
            link.unlockedAt.toEpochMilli(),
        )
        links[link.guildId] = link
        true
    } catch (exception: SQLException) {
        logger.error("Failed to persist Discord role link for guild ${link.guildId}", exception)
        false
    }

    override fun delete(guildId: UUID): Boolean = try {
        storage.connection.executeUpdate(
            "DELETE FROM ${GuildDiscordRoleSchema.TABLE} WHERE guild_id = ?",
            guildId.toString(),
        )
        links.remove(guildId)
        true
    } catch (exception: SQLException) {
        logger.error("Failed to delete Discord role link for guild $guildId", exception)
        false
    }

    private fun preload() {
        try {
            storage.connection.getResults(
                "SELECT guild_id, discord_role_id, unlocked_at FROM ${GuildDiscordRoleSchema.TABLE}"
            ).forEach { row ->
                val link = GuildDiscordRoleLink(
                    guildId = UUID.fromString(row.getString("guild_id")),
                    discordRoleId = row.getString("discord_role_id"),
                    unlockedAt = Instant.ofEpochMilli(row.getLong("unlocked_at")),
                )
                links[link.guildId] = link
            }
        } catch (exception: SQLException) {
            logger.error("Failed to preload Discord role links", exception)
            throw exception
        } catch (exception: IllegalArgumentException) {
            logger.error("Invalid Discord role link row", exception)
            throw exception
        }
    }
}
