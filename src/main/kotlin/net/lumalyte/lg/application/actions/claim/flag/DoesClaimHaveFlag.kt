package net.lumalyte.lg.application.actions.claim.flag

import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.flags.DoesClaimHaveFlagResult
import net.lumalyte.lg.domain.values.Flag
import java.util.UUID

class DoesClaimHaveFlag(private val claimRepository: ClaimRepository,
                        private val claimFlagRepository: ClaimFlagRepository) {
    fun execute(claimId: UUID, flag: Flag): DoesClaimHaveFlagResult {
        claimRepository.getById(claimId) ?: DoesClaimHaveFlagResult.ClaimNotFound
        return DoesClaimHaveFlagResult.Success(claimFlagRepository.doesClaimHaveFlag(claimId, flag))
    }
}
