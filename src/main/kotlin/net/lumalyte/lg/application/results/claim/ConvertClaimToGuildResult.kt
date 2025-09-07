package net.lumalyte.lg.application.results.claim

/**
 * Sealed class representing the possible results of converting a personal claim to a guild claim.
 */
sealed class ConvertClaimToGuildResult {
    /**
     * The claim was successfully converted to a guild claim.
     */
    data object Success : ConvertClaimToGuildResult()

    /**
     * The claim was not found.
     */
    data object ClaimNotFound : ConvertClaimToGuildResult()

    /**
     * The player is not the owner of the claim.
     */
    data object NotClaimOwner : ConvertClaimToGuildResult()

    /**
     * The claim is already a guild claim.
     */
    data object AlreadyGuildOwned : ConvertClaimToGuildResult()

    /**
     * The player is not in a guild.
     */
    data object PlayerNotInGuild : ConvertClaimToGuildResult()

    /**
     * A storage error occurred while updating the claim.
     */
    data object StorageError : ConvertClaimToGuildResult()
}
