package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.SpawnBannerState
import java.util.UUID

interface SpawnBannerRepository {
    fun save(state: SpawnBannerState): Boolean

    fun getAt(
        worldId: UUID,
        x: Int,
        y: Int,
        z: Int,
    ): SpawnBannerState?

    fun deleteAt(
        worldId: UUID,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean

    fun getAll(): List<SpawnBannerState>
}
