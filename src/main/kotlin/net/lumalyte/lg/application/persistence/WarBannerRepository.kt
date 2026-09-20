package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.WarBannerState
import java.util.UUID

interface WarBannerRepository {
    fun get(guildId: UUID): WarBannerState?
    fun getActiveAt(worldId: UUID, x: Int, y: Int, z: Int): WarBannerState?
    fun savePlacement(state: WarBannerState): Boolean
    fun delete(guildId: UUID, bannerId: UUID): Boolean
    fun deactivate(guildId: UUID, bannerId: UUID): Boolean
    fun expiredActive(now: Long): List<WarBannerState>
}
