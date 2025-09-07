package net.lumalyte.lg.application.actions.claim.metadata

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.domain.entities.Claim
import java.util.UUID

class GetClaimDetails(private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID): Claim? {
        return claimRepository.getById(claimId)
    }
}
