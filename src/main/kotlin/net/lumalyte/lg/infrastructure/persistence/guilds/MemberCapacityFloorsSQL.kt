package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.MemberCapacityFloors
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** REQ-190: floors live in memory after one read; the snapshot runs in one transaction. */
internal class MemberCapacityFloorsSQL(private val storage: Storage<Database>) : MemberCapacityFloors {
    private val logger = LoggerFactory.getLogger(MemberCapacityFloorsSQL::class.java)
    private val floors = ConcurrentHashMap<UUID, Int>()

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { c ->
            c.createStatement().use {
                it.execute("""CREATE TABLE IF NOT EXISTS guild_member_capacity_floors (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    floor_members INTEGER NOT NULL
                )$engine""")
                it.execute("""CREATE TABLE IF NOT EXISTS guild_member_capacity_snapshot (
                    id INTEGER PRIMARY KEY,
                    taken_at BIGINT NOT NULL
                )$engine""")
            }
            load(c)
        }
    }

    private fun load(c: Connection) {
        c.prepareStatement("SELECT guild_id, floor_members FROM guild_member_capacity_floors").use { st ->
            st.executeQuery().use { rs ->
                while (rs.next()) floors[UUID.fromString(rs.getString(1))] = rs.getInt(2)
            }
        }
    }

    override fun floor(guildId: UUID): Int? = floors[guildId]

    @Synchronized
    override fun snapshotOnce(): Int = try {
        storage.connection.connection.use { c ->
            val auto = c.autoCommit
            c.autoCommit = false
            try {
                // The marker row's primary key makes the snapshot happen once, even with two servers.
                val claimed = c.prepareStatement(
                    (if (storage.dialect == SqlDialect.MARIADB) "INSERT IGNORE" else "INSERT OR IGNORE") +
                        " INTO guild_member_capacity_snapshot (id, taken_at) VALUES (1, ?)",
                ).use {
                    it.setLong(1, System.currentTimeMillis())
                    it.executeUpdate() == 1
                }
                val written = if (!claimed) 0 else c.prepareStatement(
                    """INSERT INTO guild_member_capacity_floors (guild_id, floor_members)
                       SELECT g.id, COUNT(m.player_id) FROM guilds g
                       LEFT JOIN members m ON m.guild_id = g.id
                       GROUP BY g.id"""
                ).use { it.executeUpdate() }
                c.commit()
                if (written > 0) {
                    floors.clear()
                    load(c)
                    logger.info("Recorded member capacity floors for $written existing guilds (REQ-190)")
                }
                written
            } catch (error: Exception) {
                c.rollback()
                throw error
            } finally {
                c.autoCommit = auto
            }
        }
    } catch (error: Exception) {
        logger.error("Unable to record member capacity floors; grandfathered guilds keep the level cap only", error)
        0
    }
}
