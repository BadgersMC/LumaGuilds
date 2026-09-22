package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.SpawnBannerRepository
import net.lumalyte.lg.domain.entities.SpawnBannerCategory
import net.lumalyte.lg.domain.entities.SpawnBannerState
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.ResultSet
import java.util.UUID

class SpawnBannerRepositorySQL(
    private val storage: Storage<Database>,
) : SpawnBannerRepository {

    override fun getAt(worldId: UUID, x: Int, y: Int, z: Int): SpawnBannerState? =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM spawn_banners WHERE world_id=? AND x=? AND y=? AND z=?"
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
    override fun save(state: SpawnBannerState): Boolean =
        storage.connection.connection.use { connection ->
            val existing = getAt(state.worldId, state.x, state.y, state.z)
            if (existing == null) {
                connection.prepareStatement(
                    """
                    INSERT INTO spawn_banners
                    (banner_id,world_id,x,y,z,rank,category,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """.trimIndent()
                ).use { statement ->
                    bind(statement, state)
                    statement.executeUpdate() == 1
                }
            } else {
                connection.prepareStatement(
                    """
                    UPDATE spawn_banners
                    SET banner_id=?, rank=?, category=?, created_at=?
                    WHERE world_id=? AND x=? AND y=? AND z=?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, state.bannerId.toString())
                    statement.setInt(2, state.rank)
                    statement.setString(3, state.category.name)
                    statement.setLong(4, state.createdAt)
                    statement.setString(5, state.worldId.toString())
                    statement.setInt(6, state.x)
                    statement.setInt(7, state.y)
                    statement.setInt(8, state.z)
                    statement.executeUpdate() == 1
                }
            }
        }

    override fun deleteAt(worldId: UUID, x: Int, y: Int, z: Int): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM spawn_banners WHERE world_id=? AND x=? AND y=? AND z=?"
            ).use { statement ->
                statement.setString(1, worldId.toString())
                statement.setInt(2, x)
                statement.setInt(3, y)
                statement.setInt(4, z)
                statement.executeUpdate() > 0
            }
        }

    override fun getAll(): List<SpawnBannerState> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM spawn_banners ORDER BY category, rank, created_at"
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(read(rows))
                    }
                }
            }
        }

    private fun bind(statement: java.sql.PreparedStatement, state: SpawnBannerState) {
        statement.setString(1, state.bannerId.toString())
        statement.setString(2, state.worldId.toString())
        statement.setInt(3, state.x)
        statement.setInt(4, state.y)
        statement.setInt(5, state.z)
        statement.setInt(6, state.rank)
        statement.setString(7, state.category.name)
        statement.setLong(8, state.createdAt)
    }

    private fun read(rows: ResultSet) = SpawnBannerState(
        bannerId = UUID.fromString(rows.getString("banner_id")),
        worldId = UUID.fromString(rows.getString("world_id")),
        x = rows.getInt("x"),
        y = rows.getInt("y"),
        z = rows.getInt("z"),
        rank = rows.getInt("rank"),
        category = SpawnBannerCategory.valueOf(rows.getString("category")),
        createdAt = rows.getLong("created_at"),
    )
}
