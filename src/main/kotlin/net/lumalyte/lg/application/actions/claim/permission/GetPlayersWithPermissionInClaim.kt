package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import java.util.UUID

class GetPlayersWithPermissionInClaim(private val playerAccessRepository: PlayerAccessRepository) {
    fun execute(claimId: UUID): List<UUID> {
        return playerAccessRepository.getPlayersWithPermissionInClaim(claimId).toList()
    }
}
