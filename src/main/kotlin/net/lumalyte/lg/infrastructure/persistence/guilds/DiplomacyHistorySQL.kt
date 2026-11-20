package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.DiplomacyHistory
import net.lumalyte.lg.domain.entities.DiplomacyEntry
import net.lumalyte.lg.domain.entities.DiplomacyKind
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** REQ-203: `guild_diplomacy_log`, created here on SQLite and MariaDB. One row per guild per change. */
internal class DiplomacyHistorySQL(private val storage: Storage<Database>) : DiplomacyHistory {
    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { c ->
            c.createStatement().use { st ->
                st.execute("""CREATE TABLE IF NOT EXISTS guild_diplomacy_log (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    other_guild_id VARCHAR(36) NOT NULL,
                    kind VARCHAR(32) NOT NULL,
                    actor_id VARCHAR(36),
                    happened_at BIGINT NOT NULL,
                    days INTEGER
                )$engine""")
                st.execute("CREATE INDEX IF NOT EXISTS idx_guild_diplomacy_log_guild ON guild_diplomacy_log (guild_id, happened_at)")
            }
        }
    }

    override fun record(entries: List<DiplomacyEntry>) {
        if (entries.isEmpty()) return
        storage.connection.connection.use { c ->
            val auto = c.autoCommit
            c.autoCommit = false
            try {
                c.prepareStatement(
                    "INSERT INTO guild_diplomacy_log (id, guild_id, other_guild_id, kind, actor_id, happened_at, days) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).use { st ->
                    entries.forEach { e ->
                        st.setString(1, UUID.randomUUID().toString())
                        st.setString(2, e.guildId.toString())
                        st.setString(3, e.otherGuildId.toString())
                        st.setString(4, e.kind.name)
                        st.setString(5, e.actorId?.toString())
                        st.setLong(6, e.at.toEpochMilli())
                        if (e.days == null) st.setNull(7, java.sql.Types.INTEGER) else st.setInt(7, e.days)
                        st.addBatch()
                    }
                    st.executeBatch()
                }
                c.commit()
            } catch (error: Exception) {
                c.rollback()
                throw error
            } finally {
                c.autoCommit = auto
            }
        }
    }

    override fun page(guildId: UUID, offset: Int, limit: Int): List<DiplomacyEntry> =
        storage.connection.connection.use { c ->
            c.prepareStatement(
                "SELECT other_guild_id, kind, actor_id, happened_at, days FROM guild_diplomacy_log WHERE guild_id = ? " +
                    "ORDER BY happened_at DESC, id LIMIT ${limit.coerceIn(1, 500)} OFFSET ${offset.coerceAtLeast(0)}",
            ).use { st ->
                st.setString(1, guildId.toString())
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val kind = runCatching { DiplomacyKind.valueOf(rs.getString(2)) }.getOrNull() ?: continue
                            val days = rs.getInt(5).takeUnless { rs.wasNull() }
                            add(
                                DiplomacyEntry(
                                    guildId, UUID.fromString(rs.getString(1)), kind,
                                    rs.getString(3)?.let(UUID::fromString), Instant.ofEpochMilli(rs.getLong(4)), days,
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun count(guildId: UUID): Int = storage.connection.connection.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM guild_diplomacy_log WHERE guild_id = ?").use { st ->
            st.setString(1, guildId.toString())
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }
}
