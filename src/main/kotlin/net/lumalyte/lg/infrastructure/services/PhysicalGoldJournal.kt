package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.PhysicalGoldReservation
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.util.UUID

/** Durable exact-item receipt; intermediate phases are deliberately not replayable. */
class PhysicalGoldJournal(private val storage: Storage<Database>) {
    data class Receipt(val reservation: PhysicalGoldReservation, val phase: String, val payload: String)

    init {
        val payloadType = if (storage.dialect == SqlDialect.MARIADB) "LONGTEXT" else "TEXT"
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS guild_gold_reservations (" +
            "transaction_id VARCHAR(36) PRIMARY KEY, player_id VARCHAR(36) NOT NULL, amount BIGINT NOT NULL, " +
            "phase VARCHAR(24) NOT NULL, payload $payloadType NOT NULL)")
    }

    fun get(id: UUID): Receipt? = storage.connection.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM guild_gold_reservations WHERE transaction_id = ?").use {
            it.setString(1, id.toString())
            it.executeQuery().use { rows -> if (!rows.next()) null else Receipt(
                PhysicalGoldReservation(id, UUID.fromString(rows.getString("player_id")), rows.getLong("amount")),
                rows.getString("phase"), rows.getString("payload")) }
        }
    }

    fun insert(reservation: PhysicalGoldReservation, payload: String) {
        storage.connection.connection.use { connection ->
            connection.prepareStatement("INSERT INTO guild_gold_reservations (transaction_id, player_id, amount, phase, payload) VALUES (?, ?, ?, 'REMOVING', ?)").use {
                it.setString(1, reservation.id.toString()); it.setString(2, reservation.playerId.toString())
                it.setLong(3, reservation.value); it.setString(4, payload)
                check(it.executeUpdate() == 1)
            }
        }
    }

    fun transition(id: UUID, expected: String, next: String): Boolean = storage.connection.connection.use { connection ->
        connection.prepareStatement("UPDATE guild_gold_reservations SET phase = ? WHERE transaction_id = ? AND phase = ?").use {
            it.setString(1, next); it.setString(2, id.toString()); it.setString(3, expected)
            it.executeUpdate() == 1
        }
    }
}
