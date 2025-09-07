package net.lumalyte.lg.application.actions.claim

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.domain.entities.Claim
import java.util.UUID

class ListPlayerClaims(private val claimRepository: ClaimRepository) {
    fun execute(playerId: UUID): List<Claim> {
        return claimRepository.getByPlayer(playerId).toList().sortedBy { it.name }
    }
}
