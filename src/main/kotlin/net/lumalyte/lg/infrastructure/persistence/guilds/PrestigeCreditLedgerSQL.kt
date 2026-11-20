package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PrestigeCreditLedger
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** Durable member/prestige delivery pairs for REQ-154. Additive table, no backfill. */
class PrestigeCreditLedgerSQL(private val storage: Storage<Database>) : PrestigeCreditLedger {
    private val mariaDb = storage.dialect == SqlDialect.MARIADB

    init {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        storage.connection.executeUpdate(
            """CREATE TABLE IF NOT EXISTS guild_prestige_member_credits (
                   player_id VARCHAR(36) NOT NULL,
                   transaction_id VARCHAR(36) NOT NULL,
                   delivered_at BIGINT NOT NULL,
                   PRIMARY KEY (player_id, transaction_id)
               )$engine"""
        )
    }

    override fun delivered(playerId: UUID): Set<UUID> =
        storage.connection.getResults(
            "SELECT transaction_id FROM guild_prestige_member_credits WHERE player_id = ?",
            playerId.toString(),
        ).map { UUID.fromString(it.getString("transaction_id")) }.toSet()

    override fun record(playerId: UUID, transactionId: UUID, at: Instant): Boolean =
        storage.connection.executeUpdate(
            (if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE") +
                " INTO guild_prestige_member_credits (player_id, transaction_id, delivered_at) VALUES (?, ?, ?)",
            playerId.toString(), transactionId.toString(), at.toEpochMilli(),
        ) == 1

    override fun forget(transactionIds: Set<UUID>): Int =
        transactionIds.sumOf { id ->
            storage.connection.executeUpdate(
                "DELETE FROM guild_prestige_member_credits WHERE transaction_id = ?", id.toString(),
            )
        }
}
