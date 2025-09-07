package net.lumalyte.lg.application.results.claim.metadata

import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.Claim

sealed class UpdateClaimNameResult {
    data class Success(val claim: Claim) : UpdateClaimNameResult()
    object ClaimNotFound : UpdateClaimNameResult()
    object NameAlreadyExists: UpdateClaimNameResult()
    data class InputTextInvalid(val errors: List<TextValidationErrorResult>) : UpdateClaimNameResult()
    object StorageError : UpdateClaimNameResult()
}
