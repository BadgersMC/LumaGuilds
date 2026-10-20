package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/** Recovery reads and writes share database locks, including when separate adapters race. */
internal class MemberOwnerRecoverySQL(private val storage: Storage<Database>) {
    fun preview(guildId: UUID, playerId: UUID): OwnerRecoveryPreviewResult = try {
        val preview = storage.connection.connection.use { connection ->
            connection.committingTransaction { readState(connection, guildId, playerId, locking = false).preview }
        }
        OwnerRecoveryPreviewResult.Ready(preview)
    } catch (rejected: RecoveryRejected) {
        OwnerRecoveryPreviewResult.Rejected(rejected.reason)
    } catch (error: SQLException) {
        logStorageFailure("preview", guildId, playerId, error)
        OwnerRecoveryPreviewResult.Rejected(OwnerRecoveryRejection.STORAGE_FAILURE)
    }

    fun recover(preview: OwnerRecoveryPreview): OwnerRecoveryResult = try {
        storage.connection.connection.use { connection ->
            connection.committingTransaction {
                reserveSqliteWrite(connection)
                val current = readState(connection, preview.guildId, preview.member.playerId, locking = true)
                if (current.preview != preview) reject(OwnerRecoveryRejection.STALE_PREVIEW)
                replaceRank(connection, current)
            }
        }
        OwnerRecoveryResult.Recovered(preview)
    } catch (rejected: RecoveryRejected) {
        OwnerRecoveryResult.Rejected(rejected.reason)
    } catch (error: SQLException) {
        logStorageFailure("commit", preview.guildId, preview.member.playerId, error)
        OwnerRecoveryResult.Rejected(OwnerRecoveryRejection.STORAGE_FAILURE)
    }

    private fun readState(
        connection: Connection,
        guildId: UUID,
        playerId: UUID,
        locking: Boolean,
    ): RecoveryState {
        val guildName = readGuild(connection, guildId, locking)
        val ranks = readRanks(connection, guildId, locking)
        val ownerRank = ranks.values.singleOrNull { it.priority == 0 }
            ?: reject(OwnerRecoveryRejection.INVALID_OWNER_RANKS)
        val members = readMembers(connection, guildId, locking)
        if (members.map { it.member.playerId }.toSet().size != members.size) {
            reject(OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
        }
        if (members.any { it.member.rankId !in ranks }) reject(OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
        if (members.any { it.member.rankId == ownerRank.id }) reject(OwnerRecoveryRejection.ALREADY_OWNED)
        val target = members.singleOrNull { it.member.playerId == playerId }
            ?: reject(OwnerRecoveryRejection.MEMBER_NOT_FOUND)
        val previousRank = ranks.getValue(target.member.rankId)
        return RecoveryState(
            OwnerRecoveryPreview(guildId, guildName, target.member, previousRank.name, ownerRank.id, ownerRank.name),
            target.joinedAtValue,
        )
    }

    private fun reserveSqliteWrite(connection: Connection) {
        if (storage.dialect == SqlDialect.SQLITE) {
            // First statement in the deferred transaction obtains SQLite's writer reservation.
            // Zero rows match, so no values or timestamps change and no row triggers fire.
            connection.prepareStatement("UPDATE members SET rank_id = rank_id WHERE 1 = 0").use { statement ->
                statement.executeUpdate()
            }
        }
    }

    private fun readGuild(connection: Connection, guildId: UUID, locking: Boolean): String {
        val sql = "SELECT name FROM guilds WHERE id = ?${lockingSuffix(locking)}"
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { rows ->
                if (!rows.next()) reject(OwnerRecoveryRejection.GUILD_NOT_FOUND)
                rows.getString("name") ?: reject(OwnerRecoveryRejection.GUILD_NOT_FOUND)
            }
        }
    }

    private fun readRanks(connection: Connection, guildId: UUID, locking: Boolean): Map<UUID, StoredRank> {
        val sql = "SELECT id, name, priority FROM ranks WHERE guild_id = ? ORDER BY id${lockingSuffix(locking)}"
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        val id = parseUuid(rows.getString("id"), OwnerRecoveryRejection.INVALID_OWNER_RANKS)
                        val name = rows.getString("name")
                            ?: reject(OwnerRecoveryRejection.INVALID_OWNER_RANKS)
                        val priority = rows.getObject("priority")?.toString()?.toIntOrNull()
                            ?: reject(OwnerRecoveryRejection.INVALID_OWNER_RANKS)
                        if (priority < 0 || name.isBlank() || id in this) {
                            reject(OwnerRecoveryRejection.INVALID_OWNER_RANKS)
                        }
                        put(id, StoredRank(id, name, priority))
                    }
                }
            }
        }
    }

    private fun readMembers(connection: Connection, guildId: UUID, locking: Boolean): List<StoredMember> {
        val sql = "SELECT player_id, rank_id, joined_at FROM members WHERE guild_id = ? " +
            "ORDER BY player_id${lockingSuffix(locking)}"
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val playerId = parseUuid(
                            rows.getString("player_id"), OwnerRecoveryRejection.INVALID_MEMBER_RANKS,
                        )
                        val rankId = parseUuid(rows.getString("rank_id"), OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
                        val joinedAtValue = rows.getObject("joined_at")
                            ?: reject(OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
                        add(StoredMember(Member(playerId, guildId, rankId, parseJoinedAt(rows)), joinedAtValue))
                    }
                }
            }
        }
    }

    private fun replaceRank(connection: Connection, state: RecoveryState) {
        val preview = state.preview
        val sql = "UPDATE members SET rank_id = ? WHERE player_id = ? AND guild_id = ? " +
            "AND rank_id = ? AND joined_at = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, preview.ownerRankId.toString())
            statement.setString(2, preview.member.playerId.toString())
            statement.setString(3, preview.guildId.toString())
            statement.setString(4, preview.member.rankId.toString())
            // Use the database's exact representation, not a reserialized Instant (DATETIME or ISO text).
            statement.setObject(5, state.joinedAtValue)
            if (statement.executeUpdate() != 1) reject(OwnerRecoveryRejection.STALE_PREVIEW)
        }
    }

    private fun lockingSuffix(locking: Boolean): String =
        if (locking && storage.dialect == SqlDialect.MARIADB) " FOR UPDATE" else ""

    private fun parseUuid(value: String?, reason: OwnerRecoveryRejection): UUID = try {
        val id = UUID.fromString(value ?: reject(reason))
        if (!id.toString().equals(value, ignoreCase = true)) reject(reason)
        id
    } catch (_: IllegalArgumentException) {
        reject(reason)
    }

    private fun parseJoinedAt(rows: ResultSet): Instant {
        val value = rows.getObject("joined_at")
        return when (value) {
            is Timestamp -> value.toInstant()
            is LocalDateTime -> value.toInstant(ZoneOffset.UTC)
            is String -> try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    reject(OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
                }
            }
            else -> reject(OwnerRecoveryRejection.INVALID_MEMBER_RANKS)
        }
    }

    private fun reject(reason: OwnerRecoveryRejection): Nothing = throw RecoveryRejected(reason)

    private fun logStorageFailure(operation: String, guildId: UUID, playerId: UUID, error: SQLException) {
        logger.log(Level.SEVERE, "Owner recovery $operation failed: guild=$guildId member=$playerId", error)
    }

    private class RecoveryRejected(val reason: OwnerRecoveryRejection) : RuntimeException()

    private data class StoredRank(val id: UUID, val name: String, val priority: Int)

    private data class StoredMember(val member: Member, val joinedAtValue: Any)

    private data class RecoveryState(val preview: OwnerRecoveryPreview, val joinedAtValue: Any)

    private companion object {
        val logger: Logger = Logger.getLogger(MemberOwnerRecoverySQL::class.java.name)
    }
}
