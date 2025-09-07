package net.lumalyte.lg.application.results.claim.permission

/**
 * Result of granting guild members claim permissions.
 */
sealed class GrantGuildMembersClaimPermissionsResult {
    /**
     * Successfully granted permissions to guild members.
     * @param grantedCount Number of members who were granted new permissions.
     * @param alreadyHadAccessCount Number of members who already had access.
     */
    data class Success(val grantedCount: Int, val alreadyHadAccessCount: Int) : GrantGuildMembersClaimPermissionsResult()

    /**
     * The claim was not found.
     */
    object ClaimNotFound : GrantGuildMembersClaimPermissionsResult()

    /**
     * The player is not the owner of the claim.
     */
    object NotClaimOwner : GrantGuildMembersClaimPermissionsResult()

    /**
     * The claim is not owned by a guild.
     */
    object ClaimNotGuildOwned : GrantGuildMembersClaimPermissionsResult()

    /**
     * The guild has no members (other than the owner).
     */
    object NoGuildMembers : GrantGuildMembersClaimPermissionsResult()

    /**
     * A storage error occurred while granting permissions.
     */
    object StorageError : GrantGuildMembersClaimPermissionsResult()
}
