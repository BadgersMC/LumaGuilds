package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class GuildInvitationRepositorySQLite(private val storage: Storage<Database>) : GuildInvitationRepository {

    private val invitations: MutableMap<Pair<UUID, UUID>, GuildInvitation> = mutableMapOf() // (playerId, guildId) -> Invitation

    init {
        // Table creation is handled by SQLiteMigrations (version 11)
        preload()
    }

    private fun preload() {
        val sql = "SELECT * FROM guild_invitations"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val invitation = mapResultSetToInvitation(result)
                invitations[Pair(invitation.invitedPlayerId, invitation.guildId)] = invitation
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guild invitations", e)
        }
    }

    private fun mapResultSetToInvitation(rs: co.aikar.idb.DbRow): GuildInvitation {
        val guildId = UUID.fromString(rs.getString("guild_id"))
        val guildName = rs.getString("guild_name")
        val invitedPlayerId = UUID.fromString(rs.getString("invited_player_id"))
        val inviterPlayerId = UUID.fromString(rs.getString("inviter_player_id"))
        val inviterName = rs.getString("inviter_name")
        val timestamp = rs.getInstantNotNull("timestamp")

        return GuildInvitation(
            guildId = guildId,
            guildName = guildName,
            invitedPlayerId = invitedPlayerId,
            inviterPlayerId = inviterPlayerId,
            inviterName = inviterName,
            timestamp = timestamp
        )
    }

    override fun getByPlayer(playerId: UUID): List<GuildInvitation> =
        invitations.values.filter { it.invitedPlayerId == playerId }.sortedBy { it.timestamp }

    override fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): GuildInvitation? =
        invitations[Pair(playerId, guildId)]

    override fun add(invitation: GuildInvitation): Boolean {
        val sql = """
            INSERT INTO guild_invitations (guild_id, guild_name, invited_player_id, inviter_player_id, inviter_name, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                invitation.guildId.toString(),
                invitation.guildName,
                invitation.invitedPlayerId.toString(),
                invitation.inviterPlayerId.toString(),
                invitation.inviterName,
                invitation.timestamp.toString()
            )
            if (rowsAffected > 0) {
                invitations[Pair(invitation.invitedPlayerId, invitation.guildId)] = invitation
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun remove(playerId: UUID, guildId: UUID): Boolean {
        val sql = "DELETE FROM guild_invitations WHERE invited_player_id = ? AND guild_id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, playerId.toString(), guildId.toString())
            if (rowsAffected > 0) {
                invitations.remove(Pair(playerId, guildId))
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun removeAllForPlayer(playerId: UUID): Boolean {
        val sql = "DELETE FROM guild_invitations WHERE invited_player_id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, playerId.toString())
            if (rowsAffected > 0) {
                invitations.keys.removeAll { it.first == playerId }
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun removeAllForGuild(guildId: UUID): Boolean {
        val sql = "DELETE FROM guild_invitations WHERE guild_id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, guildId.toString())
            if (rowsAffected > 0) {
                invitations.keys.removeAll { it.second == guildId }
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun hasInvitation(playerId: UUID, guildId: UUID): Boolean =
        invitations.containsKey(Pair(playerId, guildId))

    override fun getInvitationCount(playerId: UUID): Int =
        invitations.values.count { it.invitedPlayerId == playerId }

    override fun removeOlderThan(olderThan: Long): Int {
        val sql = "DELETE FROM guild_invitations WHERE timestamp < ?"

        return try {
            val cutoffTime = Instant.ofEpochSecond(olderThan)
            val rowsAffected = storage.connection.executeUpdate(sql, cutoffTime.toString())
            if (rowsAffected > 0) {
                // Remove from memory cache
                val toRemove = invitations.filter { it.value.timestamp.isBefore(cutoffTime) }.keys
                toRemove.forEach { invitations.remove(it) }
            }
            rowsAffected
        } catch (e: SQLException) {
            0
        }
    }
}
