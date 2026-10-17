package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.domain.entities.GuildInvitationLeaderboardEntry
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
        val pendingSql = """
            INSERT INTO guild_invitations (guild_id, guild_name, invited_player_id, inviter_player_id, inviter_name, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val historySql = """
            INSERT INTO guild_invitation_history (id, guild_id, inviter_player_id, invited_player_id, sent_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        val committed = storage.connection.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val pendingRows = connection.prepareStatement(pendingSql).use { statement ->
                    statement.setString(1, invitation.guildId.toString())
                    statement.setString(2, invitation.guildName)
                    statement.setString(3, invitation.invitedPlayerId.toString())
                    statement.setString(4, invitation.inviterPlayerId.toString())
                    statement.setString(5, invitation.inviterName)
                    statement.setString(6, invitation.timestamp.toString())
                    statement.executeUpdate()
                }
                check(pendingRows == 1) { "Pending invitation insert affected $pendingRows rows" }

                val historyRows = connection.prepareStatement(historySql).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, invitation.guildId.toString())
                    statement.setString(3, invitation.inviterPlayerId.toString())
                    statement.setString(4, invitation.invitedPlayerId.toString())
                    statement.setLong(5, invitation.timestamp.toEpochMilli())
                    statement.executeUpdate()
                }
                check(historyRows == 1) { "Invitation history insert affected $historyRows rows" }
                connection.commit()
                true
            } catch (error: Exception) {
                runCatching { connection.rollback() }
                false
            } finally {
                runCatching { connection.autoCommit = previousAutoCommit }
            }
        }

        if (committed) {
            invitations[Pair(invitation.invitedPlayerId, invitation.guildId)] = invitation
        }
        return committed
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
                val toRemove = invitations.filter { it.value.timestamp.isBefore(cutoffTime) }.keys
                toRemove.forEach { invitations.remove(it) }
            }
            rowsAffected
        } catch (e: SQLException) {
            0
        }
    }

    override fun getInvitationLeaderboard(guildId: UUID, limit: Int): List<GuildInvitationLeaderboardEntry> {
        if (limit <= 0) return emptyList()
        return storage.connection.getResults(
            """
            SELECT inviter_player_id, COUNT(*) AS invite_count
            FROM guild_invitation_history
            WHERE guild_id = ?
            GROUP BY inviter_player_id
            ORDER BY invite_count DESC, inviter_player_id ASC
            LIMIT ?
            """.trimIndent(),
            guildId.toString(),
            limit
        ).map { row ->
            GuildInvitationLeaderboardEntry(
                inviterPlayerId = UUID.fromString(row.getString("inviter_player_id")),
                inviteCount = row.getInt("invite_count")
            )
        }
    }

    override fun getInvitationLeaderboardPage(
        guildId: UUID,
        offset: Int,
        limit: Int
    ): List<GuildInvitationLeaderboardEntry> {
        if (limit <= 0) return emptyList()
        return storage.connection.getResults(
            """
            SELECT inviter_player_id, COUNT(*) AS invite_count
            FROM guild_invitation_history
            WHERE guild_id = ?
            GROUP BY inviter_player_id
            ORDER BY invite_count DESC, inviter_player_id ASC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            guildId.toString(),
            limit,
            offset.coerceAtLeast(0)
        ).map { row ->
            GuildInvitationLeaderboardEntry(
                inviterPlayerId = UUID.fromString(row.getString("inviter_player_id")),
                inviteCount = row.getInt("invite_count")
            )
        }
    }

    override fun getInvitationLeaderboardInviterCount(guildId: UUID): Int =
        storage.connection.getFirstRow(
            "SELECT COUNT(DISTINCT inviter_player_id) AS total FROM guild_invitation_history WHERE guild_id = ?",
            guildId.toString()
        )?.getInt("total") ?: 0

    override fun getSentInvitationCount(guildId: UUID): Int =
        storage.connection.getFirstRow(
            "SELECT COUNT(*) AS total FROM guild_invitation_history WHERE guild_id = ?",
            guildId.toString()
        )?.getInt("total") ?: 0
}
