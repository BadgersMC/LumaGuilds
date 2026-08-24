package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.BlockPosition

interface BlockProvenanceRepository {
    fun recordPlayerPlaced(position: BlockPosition): Boolean
    fun wasPlayerPlaced(position: BlockPosition): Boolean
    fun remove(position: BlockPosition): Boolean
    fun removeAll(positions: Collection<BlockPosition>)
    fun move(source: BlockPosition, destination: BlockPosition)
    fun moveAll(moves: Collection<Pair<BlockPosition, BlockPosition>>)
}
