package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.DiscordGuildRoleGateway
import net.lumalyte.lg.application.services.DiscordMemberRoleResult
import net.lumalyte.lg.application.services.DiscordRoleEnsureResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

class UnavailableDiscordGuildRoleGateway : DiscordGuildRoleGateway {
    override fun isAvailable(): Boolean = false

    override fun ensureRole(existingRoleId: String?, roleName: String): CompletableFuture<DiscordRoleEnsureResult> = unavailable()

    override fun grantRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> = unavailable()

    override fun revokeRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> = unavailable()

    override fun revokeRoleByDiscordId(discordId: String, roleId: String): CompletableFuture<DiscordMemberRoleResult> = unavailable()

    override fun revokeUnexpectedRoleMembers(roleId: String, allowedPlayerIds: Set<UUID>): CompletableFuture<Int> = unavailable()

    override fun deleteRole(roleId: String): CompletableFuture<Boolean> = unavailable()

    private fun <T> unavailable(): CompletableFuture<T> = CompletableFuture<T>().also {
        it.completeExceptionally(IllegalStateException("DiscordSRV is not available"))
    }
}
