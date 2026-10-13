package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

/** Additive portable table; duplicate events never reset an already consumed prompt. */
class GuildOnboardingRepositorySQL(private val storage: Storage<Database>) : GuildOnboardingRepository {
    private val logger = LoggerFactory.getLogger(javaClass)
    init {
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS guild_onboarding_prompts (player_id VARCHAR(36) NOT NULL, guild_id VARCHAR(36) NOT NULL, consumed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(player_id, guild_id))")
    }

    override fun register(player: UUID, guild: UUID): Boolean {
        val suffix = if (storage.dialect == SqlDialect.SQLITE) "ON CONFLICT(player_id, guild_id) DO NOTHING" else "ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)"
        return execute("INSERT INTO guild_onboarding_prompts (player_id, guild_id, consumed) VALUES (?, ?, 0) $suffix", player, guild) >= 0
    }

    override fun consume(player: UUID, guild: UUID): Boolean =
        execute("UPDATE guild_onboarding_prompts SET consumed = 1 WHERE player_id = ? AND guild_id = ? AND consumed = 0", player, guild) == 1

    override fun dismiss(player: UUID, guild: UUID): Boolean =
        execute("UPDATE guild_onboarding_prompts SET consumed = 1 WHERE player_id = ? AND guild_id = ?", player, guild) >= 0

    private fun execute(sql: String, player: UUID, guild: UUID): Int = try {
        storage.connection.executeUpdate(sql, player.toString(), guild.toString())
    } catch (error: SQLException) {
        logger.error("Guild onboarding preference write failed", error)
        -1
    }
}
