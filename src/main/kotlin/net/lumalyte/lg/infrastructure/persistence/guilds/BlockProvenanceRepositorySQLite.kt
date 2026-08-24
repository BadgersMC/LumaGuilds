package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.infrastructure.persistence.storage.Storage

class BlockProvenanceRepositorySQLite(private val storage: Storage<Database>) : BlockProvenanceRepository {
    init {
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS quest_player_placed_blocks (
                world_id TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                PRIMARY KEY (world_id, x, y, z)
            )
        """.trimIndent())
    }

    override fun recordPlayerPlaced(position: BlockPosition): Boolean =
        storage.connection.executeUpdate(
            "INSERT OR IGNORE INTO quest_player_placed_blocks (world_id, x, y, z) VALUES (?, ?, ?, ?)",
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

    override fun move(source: BlockPosition, destination: BlockPosition) {
        if (!wasPlayerPlaced(source)) return
        remove(source)
        recordPlayerPlaced(destination)
    }
}
