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
    fun revokeRoleByDiscordId(discordId: String, roleId: String): CompletableFuture<DiscordMemberRoleResult>
    fun deleteRole(roleId: String): CompletableFuture<Boolean>
}
