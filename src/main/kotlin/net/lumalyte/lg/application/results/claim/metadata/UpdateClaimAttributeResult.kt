package net.lumalyte.lg.application.results.claim.metadata

import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.Claim

sealed class UpdateClaimAttributeResult {
    data class Success(val claim: Claim) : UpdateClaimAttributeResult()
    object ClaimNotFound : UpdateClaimAttributeResult()
    data class InputTextInvalid(val errors: List<TextValidationErrorResult>) : UpdateClaimAttributeResult()
    object StorageError : UpdateClaimAttributeResult()
}
