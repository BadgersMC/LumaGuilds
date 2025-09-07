package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class GetClaimPlayerPermissions(private val playerAccessRepository: PlayerAccessRepository) {
    fun execute(claimId: UUID, playerId: UUID): List<ClaimPermission> {
        return playerAccessRepository.getForPlayerInClaim(claimId, playerId).toList()
    }
}
