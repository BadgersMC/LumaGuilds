package net.lumalyte.lg.application.results.claim

import net.lumalyte.lg.domain.entities.Claim

sealed class CreateClaimResult {
    data class Success(val claim: Claim): net.lumalyte.lg.application.results.claim.CreateClaimResult()
    object NameCannotBeBlank: net.lumalyte.lg.application.results.claim.CreateClaimResult()
    object LimitExceeded: net.lumalyte.lg.application.results.claim.CreateClaimResult()
    object NameAlreadyExists: net.lumalyte.lg.application.results.claim.CreateClaimResult()
    object TooCloseToWorldBorder: net.lumalyte.lg.application.results.claim.CreateClaimResult()
}
