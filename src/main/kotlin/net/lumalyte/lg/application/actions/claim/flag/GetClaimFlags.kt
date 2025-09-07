package net.lumalyte.lg.application.actions.claim.flag

import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.domain.values.Flag
import java.util.*

class GetClaimFlags(private val flagRepository: ClaimFlagRepository) {
    fun execute(claimId: UUID): List<Flag> {
        return flagRepository.getByClaim(claimId).toList()
    }
}
