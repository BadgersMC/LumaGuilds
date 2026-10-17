package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.util.UUID

class BlockProvenanceRepositorySQLite(private val storage: Storage<Database>) : BlockProvenanceRepository {
    private val insertSql = if (storage.javaClass.simpleName.contains("MariaDB")) {
        "INSERT IGNORE INTO quest_player_placed_blocks (world_id, x, y, z) VALUES (?, ?, ?, ?)"
    } else {
        "INSERT OR IGNORE INTO quest_player_placed_blocks (world_id, x, y, z) VALUES (?, ?, ?, ?)"
    }

    override fun recordPlayerPlaced(position: BlockPosition): Boolean =
        storage.connection.executeUpdate(
            insertSql,
            position.worldId.toString(), position.x, position.y, position.z
        ) == 1

    override fun wasPlayerPlaced(position: BlockPosition): Boolean =
        storage.connection.getFirstRow(
            "SELECT 1 AS found FROM quest_player_placed_blocks WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
            position.worldId.toString(), position.x, position.y, position.z
        ) != null

    override fun remove(position: BlockPosition): Boolean =
        storage.connection.executeUpdate(
            "DELETE FROM quest_player_placed_blocks WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
            position.worldId.toString(), position.x, position.y, position.z
        ) == 1

    override fun removeAll(positions: Collection<BlockPosition>) {
        if (positions.isEmpty()) return
        check(storage.connection.createTransaction { statement ->
            positions.forEach { position ->
                statement.executeUpdateQuery(
                    "DELETE FROM quest_player_placed_blocks WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
                    position.worldId.toString(), position.x, position.y, position.z
                )
            }
            true
        }) { "Block provenance removal transaction did not commit" }
    }

    override fun move(source: BlockPosition, destination: BlockPosition) {
        storage.connection.executeUpdate(
            "UPDATE quest_player_placed_blocks SET world_id = ?, x = ?, y = ?, z = ? WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
            destination.worldId.toString(), destination.x, destination.y, destination.z,
            source.worldId.toString(), source.x, source.y, source.z
        )
    }

    override fun moveAll(moves: Collection<Pair<BlockPosition, BlockPosition>>) {
        if (moves.isEmpty()) return
        check(storage.connection.createTransaction { statement ->
            if (storage.dialect == SqlDialect.SQLITE) {
                // Reserve the writer before reading; deferred read-to-write
                // upgrades can fail immediately despite SQLite's busy timeout.
                statement.executeUpdateQuery(
                    "UPDATE quest_player_placed_blocks SET x = x WHERE 1 = 0"
                )
            }
            // Snapshot all sources before changing any coordinate. Overlapping
            // chains must not move one marker twice or violate the primary key.
            val sources = moves.map { it.first }.distinct()
            val parameters = sources.flatMap { listOf<Any>(it.worldId.toString(), it.x, it.y, it.z) }.toTypedArray()
            val predicate = sources.joinToString(" OR ") { "(world_id = ? AND x = ? AND y = ? AND z = ?)" }
            val marked = statement.executeQueryGetResults(
                "SELECT world_id, x, y, z FROM quest_player_placed_blocks WHERE $predicate", *parameters
            ).mapTo(HashSet()) { row ->
                BlockPosition(UUID.fromString(row.getString("world_id")), row.getInt("x"), row.getInt("y"), row.getInt("z"))
            }
            val markedMoves = moves.filter { it.first in marked }
            markedMoves.forEach { (source, _) ->
                statement.executeUpdateQuery(
                    "DELETE FROM quest_player_placed_blocks WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
                    source.worldId.toString(), source.x, source.y, source.z
                )
            }
            markedMoves.forEach { (_, destination) ->
                statement.executeUpdateQuery(insertSql,
                    destination.worldId.toString(), destination.x, destination.y, destination.z)
            }
            true
        }) { "Block provenance move transaction did not commit" }
    }
}
