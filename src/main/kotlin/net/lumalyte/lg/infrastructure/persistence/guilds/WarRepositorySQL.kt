package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.DurableWarRecord
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.ResultSet
import java.util.UUID

/** Additive storage; single-statement revision checks prevent stale service instances overwriting state. */
class WarRepositorySQL(private val storage: Storage<Database>) : WarRepository {
    init {
        val payloadType = if (storage.javaClass.simpleName.contains("MariaDB", ignoreCase = true)) "LONGTEXT" else "TEXT"
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS guild_war_records (" +
            "war_id VARCHAR(36) PRIMARY KEY, revision BIGINT NOT NULL, payload $payloadType NOT NULL)")
    }

    override fun get(id: UUID): DurableWarRecord? = storage.connection.connection.use { connection ->
        connection.prepareStatement("SELECT war_id, revision, payload FROM guild_war_records WHERE war_id = ?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows -> if (rows.next()) decode(rows) else null }
        }
    }

    override fun getAll(): List<DurableWarRecord> = storage.connection.connection.use { connection ->
        connection.prepareStatement("SELECT war_id, revision, payload FROM guild_war_records ORDER BY war_id").use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(decode(rows)) } }
        }
    }

    override fun save(record: DurableWarRecord): Boolean {
        val next = record.copy(revision = Math.addExact(record.revision, 1))
        val payload = WarRecordCodec.encode(next)
        return storage.connection.connection.use { connection ->
            if (record.revision == 0L) {
                connection.prepareStatement("INSERT INTO guild_war_records (war_id, revision, payload) " +
                    "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM guild_war_records WHERE war_id = ?)").use { statement ->
                    statement.setString(1, record.id.toString())
                    statement.setLong(2, next.revision)
                    statement.setString(3, payload)
                    statement.setString(4, record.id.toString())
                    statement.executeUpdate() == 1
                }
            } else {
                connection.prepareStatement("UPDATE guild_war_records SET revision = ?, payload = ? WHERE war_id = ? AND revision = ?").use { statement ->
                    statement.setLong(1, next.revision)
                    statement.setString(2, payload)
                    statement.setString(3, record.id.toString())
                    statement.setLong(4, record.revision)
                    statement.executeUpdate() == 1
                }
            }
        }
    }

    private fun decode(rows: ResultSet): DurableWarRecord = WarRecordCodec.decode(rows.getString("payload")).also {
        check(it.id.toString() == rows.getString("war_id") && it.revision == rows.getLong("revision")) {
            "Persisted war identity/revision mismatch"
        }
    }
}
