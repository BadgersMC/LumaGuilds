package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.HallPedestalRepository
import net.lumalyte.lg.domain.rewards.HallPedestal
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException

/** SQLite and MariaDB storage for Hall of Prestige pedestals (REQ-275). */
@Suppress("LibraryEntitiesShouldNotBePublic")
class HallPedestalRepositorySQL(private val storage: Storage<Database>) : HallPedestalRepository {
    init {
        try {
            jdbc { c ->
                val maria = storage.dialect == SqlDialect.MARIADB
                val world = if (maria) "VARCHAR(64)" else "TEXT"
                val engine = if (maria) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
                c.createStatement().use {
                    it.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS guild_hall_pedestals (slot INTEGER NOT NULL PRIMARY KEY, " +
                            "world $world NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                            "yaw DOUBLE NOT NULL)$engine",
                    )
                }
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create Hall pedestal table", e)
        }
    }

    override fun all(): List<HallPedestal> = jdbc { c ->
        c.prepareStatement("SELECT slot, world, x, y, z, yaw FROM guild_hall_pedestals ORDER BY slot").use { ps ->
            ps.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(HallPedestal(rows.getInt(1), rows.getString(2), rows.getDouble(3),
                        rows.getDouble(4), rows.getDouble(5), rows.getDouble(6).toFloat()))
                }
            }
        }
    }

    override fun set(pedestal: HallPedestal) {
        jdbc { c ->
            val auto = c.autoCommit
            c.autoCommit = false
            try {
                c.prepareStatement("DELETE FROM guild_hall_pedestals WHERE slot = ?").use { it.setInt(1, pedestal.slot); it.executeUpdate() }
                c.prepareStatement("INSERT INTO guild_hall_pedestals (slot, world, x, y, z, yaw) VALUES (?, ?, ?, ?, ?, ?)").use { ps ->
                    ps.setInt(1, pedestal.slot)
                    ps.setString(2, pedestal.world)
                    ps.setDouble(3, pedestal.x)
                    ps.setDouble(4, pedestal.y)
                    ps.setDouble(5, pedestal.z)
                    ps.setDouble(6, pedestal.yaw.toDouble())
                    ps.executeUpdate()
                }
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = auto
            }
        }
    }

    override fun remove(slot: Int): Boolean = jdbc { c ->
        c.prepareStatement("DELETE FROM guild_hall_pedestals WHERE slot = ?").use { it.setInt(1, slot); it.executeUpdate() > 0 }
    }

    private fun <T> jdbc(block: (Connection) -> T): T = storage.connection.connection.use(block)
}
