package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildKillLog
import net.lumalyte.lg.application.persistence.GuildRival
import net.lumalyte.lg.domain.entities.Kill
import net.lumalyte.lg.domain.values.CombatSummary
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** REQ-199: the kill log in `guild_kill_log`, created here on SQLite and MariaDB. Times are epoch millis. */
internal class GuildKillLogSQL(private val storage: Storage<Database>) : GuildKillLog {
    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        connect { c ->
            c.createStatement().use { st ->
                st.execute("""CREATE TABLE IF NOT EXISTS guild_kill_log (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    killer_id VARCHAR(36) NOT NULL,
                    victim_id VARCHAR(36) NOT NULL,
                    killer_guild_id VARCHAR(36),
                    victim_guild_id VARCHAR(36),
                    weapon VARCHAR(64),
                    killed_at BIGINT NOT NULL
                )$engine""")
                listOf("killer_guild_id", "victim_guild_id", "killer_id, victim_id").forEachIndexed { i, cols ->
                    st.execute("CREATE INDEX IF NOT EXISTS idx_guild_kill_log_$i ON guild_kill_log ($cols)")
                }
            }
        }
    }

    override fun record(kill: Kill) {
        connect { c ->
            c.prepareStatement(
                "INSERT INTO guild_kill_log (id, killer_id, victim_id, killer_guild_id, victim_guild_id, weapon, killed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { st ->
                st.setString(1, kill.id.toString())
                st.setString(2, kill.killerId.toString())
                st.setString(3, kill.victimId.toString())
                st.setString(4, kill.killerGuildId?.toString())
                st.setString(5, kill.victimGuildId?.toString())
                st.setString(6, kill.weapon?.take(64))
                st.setLong(7, kill.timestamp.toEpochMilli())
                st.executeUpdate()
            }
        }
    }

    override fun lastKill(killerId: UUID, victimId: UUID): Instant? = connect { c ->
        c.query("SELECT MAX(killed_at) FROM guild_kill_log WHERE killer_id = ? AND victim_id = ?", killerId, victimId) { rs ->
            rs.next()
            rs.getLong(1).takeUnless { rs.wasNull() }?.let(Instant::ofEpochMilli)
        }
    }

    override fun guildSummary(guildId: UUID, from: Instant?, to: Instant?): CombatSummary = connect { c ->
        val range = buildString {
            if (from != null) append(" AND killed_at >= ${from.toEpochMilli()}")
            if (to != null) append(" AND killed_at < ${to.toEpochMilli()}")
        }
        val kills = c.query("SELECT COUNT(*) FROM guild_kill_log WHERE killer_guild_id = ?$range", guildId) { it.next(); it.getInt(1) }
        val deaths = c.query("SELECT COUNT(*) FROM guild_kill_log WHERE victim_guild_id = ?$range", guildId) { it.next(); it.getInt(1) }
        CombatSummary(kills, deaths)
    }

    override fun playerSummary(playerId: UUID): CombatSummary = connect { c ->
        val kills = c.query("SELECT COUNT(*) FROM guild_kill_log WHERE killer_id = ?", playerId) { it.next(); it.getInt(1) }
        val deaths = c.query("SELECT COUNT(*) FROM guild_kill_log WHERE victim_id = ?", playerId) { it.next(); it.getInt(1) }
        CombatSummary(kills, deaths)
    }

    override fun recent(guildId: UUID, limit: Int): List<Kill> = connect { c ->
        c.query(
            "$SELECT WHERE killer_guild_id = ? OR victim_guild_id = ? ORDER BY killed_at DESC LIMIT ${limit.coerceIn(1, 500)}",
            guildId, guildId,
        ) { it.kills() }
    }

    override fun between(guildA: UUID, guildB: UUID, limit: Int): List<Kill> = connect { c ->
        c.query(
            "$SELECT WHERE (killer_guild_id = ? AND victim_guild_id = ?) OR (killer_guild_id = ? AND victim_guild_id = ?) " +
                "ORDER BY killed_at DESC LIMIT ${limit.coerceIn(1, 500)}",
            guildA, guildB, guildB, guildA,
        ) { it.kills() }
    }

    override fun killCounts(players: Collection<UUID>): Map<UUID, Int> {
        if (players.isEmpty()) return emptyMap()
        return players.distinct().chunked(CHUNK).fold(mutableMapOf()) { counts, chunk ->
            connect { c ->
                val marks = chunk.joinToString(", ") { "?" }
                c.query(
                    "SELECT killer_id, COUNT(*) FROM guild_kill_log WHERE killer_id IN ($marks) GROUP BY killer_id",
                    *chunk.toTypedArray(),
                ) { rs -> while (rs.next()) counts[UUID.fromString(rs.getString(1))] = rs.getInt(2) }
            }
            counts
        }
    }

    override fun rivals(guildId: UUID, limit: Int): List<GuildRival> = connect { c ->
        val kills = c.query(
            "SELECT victim_guild_id, COUNT(*) FROM guild_kill_log WHERE killer_guild_id = ? AND victim_guild_id IS NOT NULL GROUP BY victim_guild_id",
            guildId,
        ) { rs -> buildMap { while (rs.next()) put(UUID.fromString(rs.getString(1)), rs.getInt(2)) } }
        val deaths = c.query(
            "SELECT killer_guild_id, COUNT(*) FROM guild_kill_log WHERE victim_guild_id = ? AND killer_guild_id IS NOT NULL GROUP BY killer_guild_id",
            guildId,
        ) { rs -> buildMap { while (rs.next()) put(UUID.fromString(rs.getString(1)), rs.getInt(2)) } }
        (kills.keys + deaths.keys).filter { it != guildId }
            .map { GuildRival(it, kills[it] ?: 0, deaths[it] ?: 0) }
            .sortedWith(compareByDescending<GuildRival> { it.kills + it.deaths }.thenByDescending { it.kills }.thenBy { it.guildId })
            .take(limit)
    }

    private fun <T> connect(block: (Connection) -> T): T = storage.connection.connection.use(block)

    private fun <T> Connection.query(sql: String, vararg args: UUID, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { st: PreparedStatement ->
            args.forEachIndexed { i, id -> st.setString(i + 1, id.toString()) }
            st.executeQuery().use(read)
        }

    private fun ResultSet.kills(): List<Kill> = buildList {
        while (next()) {
            add(
                Kill(
                    id = UUID.fromString(getString("id")),
                    killerId = UUID.fromString(getString("killer_id")),
                    victimId = UUID.fromString(getString("victim_id")),
                    killerGuildId = getString("killer_guild_id")?.let(UUID::fromString),
                    victimGuildId = getString("victim_guild_id")?.let(UUID::fromString),
                    timestamp = Instant.ofEpochMilli(getLong("killed_at")),
                    weapon = getString("weapon"),
                ),
            )
        }
    }

    private companion object {
        const val CHUNK = 200
        const val SELECT = "SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, weapon, killed_at FROM guild_kill_log"
    }
}
