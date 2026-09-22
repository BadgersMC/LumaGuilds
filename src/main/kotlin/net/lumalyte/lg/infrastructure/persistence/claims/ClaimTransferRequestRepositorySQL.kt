package net.lumalyte.lg.infrastructure.persistence.claims

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.ClaimTransferRequestSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

class ClaimTransferRequestRepositorySQL(
    private val storage: Storage<Database>,
) : ClaimTransferRequestRepository {
    private val logger = LoggerFactory.getLogger(ClaimTransferRequestRepositorySQL::class.java)

    private val upsertSql = when (storage.dialect) {
        SqlDialect.MARIADB -> """
            INSERT INTO ${ClaimTransferRequestSchema.TABLE} (claim_id, player_id, expires_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)
        """.trimIndent()
        SqlDialect.SQLITE -> """
            INSERT INTO ${ClaimTransferRequestSchema.TABLE} (claim_id, player_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT(claim_id, player_id) DO UPDATE SET expires_at = excluded.expires_at
        """.trimIndent()
    }

    override fun hasActive(claimId: UUID, playerId: UUID, nowEpochSeconds: Long): Boolean {
        return try {
            val rows = storage.connection.getResults(
                "SELECT expires_at FROM ${ClaimTransferRequestSchema.TABLE} " +
                    "WHERE claim_id = ? AND player_id = ?",
                claimId.toString(),
                playerId.toString(),
            )
            val row = rows.firstOrNull() ?: return false
            val expiresAt = row.get<Number>("expires_at")?.toLong() ?: return false
            if (expiresAt <= nowEpochSeconds) {
                withdraw(claimId, playerId)
                false
            } else {
                true
            }
        } catch (exception: SQLException) {
            throw DatabaseOperationException(
                "Failed to read claim transfer request $claimId -> $playerId",
                exception,
            )
        }
    }

    override fun offer(claimId: UUID, playerId: UUID, expiresAtEpochSeconds: Long): Boolean = try {
        storage.connection.executeUpdate(
            upsertSql,
            claimId.toString(),
            playerId.toString(),
            expiresAtEpochSeconds,
        ) > 0
    } catch (exception: SQLException) {
        throw DatabaseOperationException(
            "Failed to persist claim transfer request $claimId -> $playerId",
            exception,
        )
    }

    override fun withdraw(claimId: UUID, playerId: UUID): Boolean = try {
        storage.connection.executeUpdate(
            "DELETE FROM ${ClaimTransferRequestSchema.TABLE} WHERE claim_id = ? AND player_id = ?",
            claimId.toString(),
            playerId.toString(),
        ) > 0
    } catch (exception: SQLException) {
        throw DatabaseOperationException(
            "Failed to withdraw claim transfer request $claimId -> $playerId",
            exception,
        )
    }

    override fun clearClaim(claimId: UUID): Boolean = try {
        storage.connection.executeUpdate(
            "DELETE FROM ${ClaimTransferRequestSchema.TABLE} WHERE claim_id = ?",
            claimId.toString(),
        )
        true
    } catch (exception: SQLException) {
        throw DatabaseOperationException(
            "Failed to clear claim transfer requests for $claimId",
            exception,
        )
    }
}
