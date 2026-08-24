package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.infrastructure.persistence.storage.Storage

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
            moves.forEach { (source, destination) ->
                statement.executeUpdateQuery(
                    "UPDATE quest_player_placed_blocks SET world_id = ?, x = ?, y = ?, z = ? WHERE world_id = ? AND x = ? AND y = ? AND z = ?",
                    destination.worldId.toString(), destination.x, destination.y, destination.z,
                    source.worldId.toString(), source.x, source.y, source.z
                )
            }
            true
        }) { "Block provenance move transaction did not commit" }
    }
}
