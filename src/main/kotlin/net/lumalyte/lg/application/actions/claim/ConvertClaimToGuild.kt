package net.lumalyte.lg.application.actions.claim

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.ConvertClaimToGuildResult
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import java.util.UUID

class ConvertClaimToGuild(
    private val claimRepository: ClaimRepository,
    private val guildService: GuildService,
    private val guildRolePermissionResolver: GuildRolePermissionResolver
) {

    /**
     * Converts a personal claim to a guild claim, making it accessible to guild members based on their roles.
     *
     * @param claimId The UUID of the claim to convert.
     * @param playerId The UUID of the player performing the action (must be claim owner).
     * @return A ConvertClaimToGuildResult indicating the outcome.
     */
    fun execute(claimId: UUID, playerId: UUID): ConvertClaimToGuildResult {
        try {
            // Check if claim exists and get it
            val claim = claimRepository.getById(claimId)
                ?: return ConvertClaimToGuildResult.ClaimNotFound

            // Verify player owns the claim
            if (claim.playerId != playerId) {
                return ConvertClaimToGuildResult.NotClaimOwner
            }

            // Check if claim is already guild-owned
            if (claim.teamId != null) {
                return ConvertClaimToGuildResult.AlreadyGuildOwned
            }

            // Check if player is in a guild
            val guildId = guildService.getPlayerGuilds(playerId).firstOrNull()?.id
                ?: return ConvertClaimToGuildResult.PlayerNotInGuild

            // Convert claim to guild ownership
            val updatedClaim = claim.copy(teamId = guildId)
            val success = claimRepository.update(updatedClaim)

            if (!success) {
                return ConvertClaimToGuildResult.StorageError
            }

            // Invalidate permission cache for this claim to ensure guild permissions are recalculated
            guildRolePermissionResolver.invalidateGuildCache(guildId)

            return ConvertClaimToGuildResult.Success

        } catch (error: DatabaseOperationException) {
            println("Error converting claim to guild: ${error.message}")
            return ConvertClaimToGuildResult.StorageError
        } catch (error: Exception) {
            println("Unexpected error converting claim to guild: ${error.message}")
            return ConvertClaimToGuildResult.StorageError
        }
    }
}
