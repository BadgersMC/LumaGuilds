package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.WarBannerRepository
import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.ResultSet
import java.util.UUID

class WarBannerRepositorySQL(
    private val storage: Storage<Database>,
) : WarBannerRepository {

    override fun get(guildId: UUID): WarBannerState? =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM war_banners WHERE guild_id = ?"
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.executeQuery().use { rows ->
                    if (rows.next()) read(rows) else null
                }
            }
        }

    override fun getActiveAt(
        worldId: UUID,
        x: Int,
        y: Int,
        z: Int,
    ): WarBannerState? = storage.connection.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM war_banners
            WHERE world_id = ? AND x = ? AND y = ? AND z = ? AND active = 1
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, worldId.toString())
            statement.setInt(2, x)
            statement.setInt(3, y)
            statement.setInt(4, z)
            statement.executeQuery().use { rows ->
                if (rows.next()) read(rows) else null
            }
        }
    }

    @Synchronized
    override fun savePlacement(state: WarBannerState): Boolean {
        val existing = get(state.guildId)
        return storage.connection.connection.use { connection ->
            if (existing == null) {
                connection.prepareStatement(
                    """
                    INSERT INTO war_banners
                    (guild_id,banner_id,world_id,x,y,z,placed_by,transaction_id,
                     placed_at,expires_at,cooldown_until,active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent()
                ).use { statement ->
                    bindPlacement(statement, state)
                    statement.executeUpdate() == 1
                }
            } else {
                connection.prepareStatement(
                    """
                    UPDATE war_banners SET banner_id=?, world_id=?, x=?, y=?, z=?,
                    placed_by=?, transaction_id=?, placed_at=?, expires_at=?,
                    cooldown_until=?, active=? WHERE guild_id=?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, state.bannerId.toString())
                    statement.setString(2, state.worldId.toString())
                    statement.setInt(3, state.x)
                    statement.setInt(4, state.y)
                    statement.setInt(5, state.z)
                    statement.setString(6, state.placedBy.toString())
                    statement.setString(7, state.transactionId.toString())
                    statement.setLong(8, state.placedAt)
                    statement.setLong(9, state.expiresAt)
                    statement.setLong(10, state.cooldownUntil)
                    statement.setBoolean(11, state.active)
                    statement.setString(12, state.guildId.toString())
                    statement.executeUpdate() == 1
                }
            }
        }
    }

    override fun delete(guildId: UUID, bannerId: UUID): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM war_banners WHERE guild_id=? AND banner_id=?"
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.setString(2, bannerId.toString())
                statement.executeUpdate() == 1
            }
        }

    override fun deactivate(guildId: UUID, bannerId: UUID): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE war_banners SET active=0 WHERE guild_id=? AND banner_id=? AND active=1"
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.setString(2, bannerId.toString())
                statement.executeUpdate() == 1
            }
        }

    override fun expiredActive(now: Long): List<WarBannerState> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM war_banners WHERE active=1 AND expires_at <= ?"
            ).use { statement ->
                statement.setLong(1, now)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(read(rows))
                    }
                }
            }
        }

    private fun bindPlacement(
        statement: java.sql.PreparedStatement,
        state: WarBannerState,
    ) {
        statement.setString(1, state.guildId.toString())
        statement.setString(2, state.bannerId.toString())
        statement.setString(3, state.worldId.toString())
        statement.setInt(4, state.x)
        statement.setInt(5, state.y)
        statement.setInt(6, state.z)
        statement.setString(7, state.placedBy.toString())
        statement.setString(8, state.transactionId.toString())
        statement.setLong(9, state.placedAt)
        statement.setLong(10, state.expiresAt)
        statement.setLong(11, state.cooldownUntil)
        statement.setBoolean(12, state.active)
    }

    private fun read(rows: ResultSet) = WarBannerState(
        guildId = UUID.fromString(rows.getString("guild_id")),
        bannerId = UUID.fromString(rows.getString("banner_id")),
        worldId = UUID.fromString(rows.getString("world_id")),
        x = rows.getInt("x"),
        y = rows.getInt("y"),
        z = rows.getInt("z"),
        placedBy = UUID.fromString(rows.getString("placed_by")),
        transactionId = UUID.fromString(rows.getString("transaction_id")),
        placedAt = rows.getLong("placed_at"),
        expiresAt = rows.getLong("expires_at"),
        cooldownUntil = rows.getLong("cooldown_until"),
        active = rows.getBoolean("active"),
    )
}
