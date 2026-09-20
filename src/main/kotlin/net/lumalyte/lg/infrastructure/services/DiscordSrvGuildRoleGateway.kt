package net.lumalyte.lg.infrastructure.services

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role
import net.lumalyte.lg.application.services.DiscordGuildRoleGateway
import net.lumalyte.lg.application.services.DiscordMemberRoleResult
import net.lumalyte.lg.application.services.DiscordRoleEnsureResult
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DiscordSrvGuildRoleGateway : DiscordGuildRoleGateway {
    private val logger = LoggerFactory.getLogger(DiscordSrvGuildRoleGateway::class.java)

    override fun isAvailable(): Boolean =
        runCatching { DiscordSRV.isReady && DiscordSRV.getPlugin().mainGuild != null }.getOrDefault(false)

    override fun ensureRole(
        existingRoleId: String?,
        roleName: String,
    ): CompletableFuture<DiscordRoleEnsureResult> {
        val guild = mainGuild() ?: return failedFuture(IllegalStateException("DiscordSRV main guild is unavailable"))
        existingRoleId?.let { id ->
            guild.getRoleById(id)?.let { role ->
                if (role.name == roleName) {
                    return CompletableFuture.completedFuture(DiscordRoleEnsureResult(role.id, false))
                }
                return role.manager.setName(roleName).submit()
                    .thenApply { DiscordRoleEnsureResult(role.id, false) }
            }
        }
        return guild.createRole()
            .setName(roleName)
            .setMentionable(false)
            .submit()
            .thenApply { role -> DiscordRoleEnsureResult(role.id, true) }
    }

    override fun grantRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> =
        mutateRole(playerId, roleId, grant = true)

    override fun revokeRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> =
        mutateRole(playerId, roleId, grant = false)

    override fun revokeRoleByDiscordId(
        discordId: String,
        roleId: String,
    ): CompletableFuture<DiscordMemberRoleResult> =
        mutateRoleByDiscordId(discordId, roleId, grant = false)

    override fun deleteRole(roleId: String): CompletableFuture<Boolean> {
        val guild = mainGuild() ?: return CompletableFuture.completedFuture(false)
        val role = guild.getRoleById(roleId) ?: return CompletableFuture.completedFuture(true)
        return role.delete().submit().thenApply { true }
    }

    private fun mutateRole(
        playerId: UUID,
        roleId: String,
        grant: Boolean,
    ): CompletableFuture<DiscordMemberRoleResult> {
        val plugin = runCatching { DiscordSRV.getPlugin() }.getOrNull()
            ?: return failedFuture(IllegalStateException("DiscordSRV plugin is unavailable"))
        val discordId = plugin.accountLinkManager.getDiscordId(playerId)
            ?: return CompletableFuture.completedFuture(DiscordMemberRoleResult.PLAYER_UNLINKED)

        return mutateRoleByDiscordId(discordId, roleId, grant)
    }

    private fun mutateRoleByDiscordId(
        discordId: String,
        roleId: String,
        grant: Boolean,
    ): CompletableFuture<DiscordMemberRoleResult> {
        val guild = mainGuild()
            ?: return failedFuture(IllegalStateException("DiscordSRV main guild is unavailable"))
        val role = guild.getRoleById(roleId)
            ?: return CompletableFuture.completedFuture(DiscordMemberRoleResult.ROLE_MISSING)

        return findMember(guild, discordId).thenCompose { member ->
            if (member == null) {
                CompletableFuture.completedFuture(DiscordMemberRoleResult.PLAYER_NOT_IN_DISCORD_GUILD)
            } else if (grant && role in member.roles) {
                CompletableFuture.completedFuture(DiscordMemberRoleResult.ALREADY_PRESENT)
            } else if (!grant && role !in member.roles) {
                CompletableFuture.completedFuture(DiscordMemberRoleResult.ALREADY_ABSENT)
            } else {
                val action = if (grant) guild.addRoleToMember(member, role) else guild.removeRoleFromMember(member, role)
                action.submit().thenApply {
                    if (grant) DiscordMemberRoleResult.APPLIED else DiscordMemberRoleResult.REMOVED
                }
            }
        }
    }

    private fun findMember(guild: Guild, discordId: String): CompletableFuture<Member?> {
        guild.getMemberById(discordId)?.let { return CompletableFuture.completedFuture(it) }
        return guild.retrieveMemberById(discordId).submit()
            .handle { member, error ->
                if (error != null) {
                    logger.debug("Discord account $discordId is not available in the configured guild: ${error.message}")
                    null
                } else member
            }
    }

    private fun mainGuild(): Guild? =
        runCatching { if (DiscordSRV.isReady) DiscordSRV.getPlugin().mainGuild else null }.getOrNull()

    private fun <T> failedFuture(error: Throwable): CompletableFuture<T> =
        CompletableFuture<T>().also { it.completeExceptionally(error) }
}
