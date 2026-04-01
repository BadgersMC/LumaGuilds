package net.lumalyte.lg.infrastructure.persistence.invitations

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitation
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * SQLite implementation of GuildInvitationRepository.
 */
class GuildInvitationRepositorySQLite(private val database: Database) : GuildInvitationRepository {

    private val log = LoggerFactory.getLogger(GuildInvitationRepositorySQLite::class.java)

    override fun getByPlayer(playerId: UUID): List<GuildInvitation> {
        return try {
            val results = database.getResults(
                """
                SELECT guild_id, guild_name, invited_player_id, inviter_player_id, inviter_name, invited_at
                FROM guild_invitations
                WHERE invited_player_id = ?
                ORDER BY invited_at DESC
                """.trimIndent(),
                playerId.toString()
            )

            results.mapNotNull { row ->
                try {
                    GuildInvitation(
                        guildId = UUID.fromString(row.getString("guild_id")),
                        guildName = row.getString("guild_name"),
                        invitedPlayerId = UUID.fromString(row.getString("invited_player_id")),
                        inviterPlayerId = UUID.fromString(row.getString("inviter_player_id")),
                        inviterName = row.getString("inviter_name"),
                        timestamp = Instant.parse(row.getString("invited_at"))
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse invitation row", e)
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get invitations for player $playerId", e)
            emptyList()
        }
    }

    override fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): GuildInvitation? {
        return try {
            val row = database.getFirstRow(
                """
                SELECT guild_id, guild_name, invited_player_id, inviter_player_id, inviter_name, invited_at
                FROM guild_invitations
                WHERE invited_player_id = ? AND guild_id = ?
                """.trimIndent(),
                playerId.toString(),
                guildId.toString()
            ) ?: return null

            GuildInvitation(
                guildId = UUID.fromString(row.getString("guild_id")),
                guildName = row.getString("guild_name"),
                invitedPlayerId = UUID.fromString(row.getString("invited_player_id")),
                inviterPlayerId = UUID.fromString(row.getString("inviter_player_id")),
                inviterName = row.getString("inviter_name"),
                timestamp = Instant.parse(row.getString("invited_at"))
            )
        } catch (e: Exception) {
            log.error("Failed to get invitation for player $playerId and guild $guildId", e)
            null
        }
    }

    override fun add(invitation: GuildInvitation): Boolean {
        return try {
            database.executeUpdate(
                """
                INSERT INTO guild_invitations (
                    guild_id, guild_name, invited_player_id, inviter_player_id, inviter_name, invited_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                invitation.guildId.toString(),
                invitation.guildName,
                invitation.invitedPlayerId.toString(),
                invitation.inviterPlayerId.toString(),
                invitation.inviterName,
                invitation.timestamp.toString()
            )
            true
        } catch (e: Exception) {
            log.error("Failed to create invitation for player ${invitation.invitedPlayerId} to guild ${invitation.guildId}", e)
            false
        }
    }

    override fun remove(playerId: UUID, guildId: UUID): Boolean {
        return try {
            val rowsAffected = database.executeUpdate(
                """
                DELETE FROM guild_invitations
                WHERE invited_player_id = ? AND guild_id = ?
                """.trimIndent(),
                playerId.toString(),
                guildId.toString()
            )
            rowsAffected > 0
        } catch (e: Exception) {
            log.error("Failed to delete invitation for player $playerId and guild $guildId", e)
            false
        }
    }

    override fun removeAllForPlayer(playerId: UUID): Boolean {
        return try {
            database.executeUpdate(
                """
                DELETE FROM guild_invitations
                WHERE invited_player_id = ?
                """.trimIndent(),
                playerId.toString()
            )
            true
        } catch (e: Exception) {
            log.error("Failed to delete all invitations for player $playerId", e)
            false
        }
    }

    override fun removeAllForGuild(guildId: UUID): Boolean {
        return try {
            database.executeUpdate(
                """
                DELETE FROM guild_invitations
                WHERE guild_id = ?
                """.trimIndent(),
                guildId.toString()
            )
            true
        } catch (e: Exception) {
            log.error("Failed to delete all invitations for guild $guildId", e)
            false
        }
    }

    override fun hasInvitation(playerId: UUID, guildId: UUID): Boolean {
        return try {
            val row = database.getFirstRow(
                """
                SELECT 1 FROM guild_invitations
                WHERE invited_player_id = ? AND guild_id = ?
                """.trimIndent(),
                playerId.toString(),
                guildId.toString()
            )
            row != null
        } catch (e: Exception) {
            log.error("Failed to check invitation existence for player $playerId and guild $guildId", e)
            false
        }
    }

    override fun getInvitationCount(playerId: UUID): Int {
        return try {
            val row = database.getFirstRow(
                """
                SELECT COUNT(*) as count FROM guild_invitations
                WHERE invited_player_id = ?
                """.trimIndent(),
                playerId.toString()
            )
            row?.getInt("count") ?: 0
        } catch (e: Exception) {
            log.error("Failed to count invitations for player $playerId", e)
            0
        }
    }

    override fun removeOlderThan(olderThan: Long): Int {
        return try {
            val cutoffTime = Instant.ofEpochSecond(olderThan)

            database.executeUpdate(
                """
                DELETE FROM guild_invitations
                WHERE invited_at < ?
                """.trimIndent(),
                cutoffTime.toString()
            )
        } catch (e: Exception) {
            log.error("Failed to delete old invitations", e)
            0
        }
    }
}
