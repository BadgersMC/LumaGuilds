package net.lumalyte.lg.application.services

import java.util.UUID
import java.util.concurrent.CompletableFuture

data class DiscordRoleEnsureResult(
    val roleId: String,
    val created: Boolean,
)

enum class DiscordMemberRoleResult {
    APPLIED,
    REMOVED,
    ALREADY_PRESENT,
    ALREADY_ABSENT,
    PLAYER_UNLINKED,
    PLAYER_NOT_IN_DISCORD_GUILD,
    ROLE_MISSING,
}

interface DiscordGuildRoleGateway {
    fun isAvailable(): Boolean
    fun ensureRole(existingRoleId: String?, roleName: String): CompletableFuture<DiscordRoleEnsureResult>
    fun grantRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult>
    fun revokeRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult>

    /**
     * Revokes a role using an account reference captured before an unlink becomes authoritative.
     * The application layer must not interpret the reference.
     */
    fun revokeRoleForAccount(
        accountReference: DiscordAccountReference,
        roleId: String,
    ): CompletableFuture<DiscordMemberRoleResult> =
        revokeRoleByDiscordId(accountReference.value, roleId)

    /**
     * Compatibility seam for the legacy DiscordSRV adapter and older test doubles.
     * New providers should implement [revokeRoleForAccount] instead.
     */
    @Deprecated("Use revokeRoleForAccount with an opaque DiscordAccountReference")
    fun revokeRoleByDiscordId(
        discordId: String,
        roleId: String,
    ): CompletableFuture<DiscordMemberRoleResult> = failedUnsupportedAccountCleanup()

    fun revokeUnexpectedRoleMembers(roleId: String, allowedPlayerIds: Set<UUID>): CompletableFuture<Int>
    fun deleteRole(roleId: String): CompletableFuture<Boolean>

    private fun failedUnsupportedAccountCleanup(): CompletableFuture<DiscordMemberRoleResult> =
        CompletableFuture<DiscordMemberRoleResult>().also { future ->
            future.completeExceptionally(
                UnsupportedOperationException("Provider does not support unlink account cleanup")
            )
        }
}
