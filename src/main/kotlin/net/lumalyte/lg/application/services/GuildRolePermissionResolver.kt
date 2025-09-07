package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

/**
 * Service interface for resolving claim permissions based on guild roles.
 * This service maps guild ranks to claim permissions and caches results for performance.
 */
interface GuildRolePermissionResolver {
    
    /**
     * Checks if a player has a specific permission on a claim through their guild role.
     * 
     * @param playerId The UUID of the player
     * @param claimId The UUID of the claim
     * @param permission The permission to check
     * @return true if the player has the permission through their guild role, false otherwise
     */
    fun hasPermission(playerId: UUID, claimId: UUID, permission: ClaimPermission): Boolean
    
    /**
     * Gets all permissions a player has on a claim through their guild role.
     * 
     * @param playerId The UUID of the player
     * @param claimId The UUID of the claim
     * @return Set of permissions the player has through their guild role
     */
    fun getPermissions(playerId: UUID, claimId: UUID): Set<ClaimPermission>
    
    /**
     * Invalidates cached permissions for a specific player.
     * Should be called when a player's guild role changes.
     * 
     * @param playerId The UUID of the player whose cache should be invalidated
     */
    fun invalidatePlayerCache(playerId: UUID)
    
    /**
     * Invalidates cached permissions for all members of a guild.
     * Should be called when guild rank permissions change.
     * 
     * @param guildId The UUID of the guild whose members' cache should be invalidated
     */
    fun invalidateGuildCache(guildId: UUID)
    
    /**
     * Clears all cached permissions.
     * Should be called when configuration changes or on server restart.
     */
    fun clearCache()
}
