package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.values.ClaimPermission
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bukkit implementation of GuildRolePermissionResolver that maps guild ranks to claim permissions.
 * Uses caching for performance and integrates with the config system for role-to-permission mappings.
 */
class GuildRolePermissionResolverBukkit(
    private val memberService: MemberService,
    private val rankService: RankService,
    private val claimRepository: ClaimRepository,
    private val configService: ConfigService
) : GuildRolePermissionResolver {
    
    private val logger = LoggerFactory.getLogger(GuildRolePermissionResolverBukkit::class.java)
    
    // Cache for storing player permissions: playerId+claimId -> Set<ClaimPermission>
    private val permissionCache = ConcurrentHashMap<String, Set<ClaimPermission>>()
    
    // Cache for storing guild membership: playerId+guildId -> Boolean
    private val membershipCache = ConcurrentHashMap<String, Boolean>()
    
    // Scheduled executor for cache cleanup
    private val executor = Executors.newSingleThreadScheduledExecutor()
    
    init {
        // Schedule periodic cache cleanup every hour
        executor.scheduleAtFixedRate({
            try {
                // Clear old cache entries to prevent memory buildup
                val maxSize = 10000
                if (permissionCache.size > maxSize) {
                    permissionCache.clear()
                    logger.debug("Cleared permission cache due to size limit")
                }
                if (membershipCache.size > maxSize) {
                    membershipCache.clear()
                    logger.debug("Cleared membership cache due to size limit")
                }
            } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
                logger.warn("Error during cache cleanup", e)
            }
        }, 1, 1, TimeUnit.HOURS)
    }
    
    override fun hasPermission(playerId: UUID, claimId: UUID, permission: ClaimPermission): Boolean {
        return getPermissions(playerId, claimId).contains(permission)
    }
    
    override fun getPermissions(playerId: UUID, claimId: UUID): Set<ClaimPermission> {
        val cacheKey = "${playerId}_${claimId}"
        
        // Check cache first
        permissionCache[cacheKey]?.let { return it }
        
        val permissions = computePermissions(playerId, claimId)
        
        // Cache the result
        permissionCache[cacheKey] = permissions
        
        return permissions
    }
    
    private fun computePermissions(playerId: UUID, claimId: UUID): Set<ClaimPermission> {
        try {
            // Get the claim to check if it has guild ownership
            val claim = claimRepository.getById(claimId) ?: return emptySet()
            
            // If claim is not owned by a guild, no guild permissions apply
            val guildId = claim.teamId ?: return emptySet()
            
            // Check if player is a member of the guild that owns the claim
            if (!isPlayerInGuild(playerId, guildId)) {
                return emptySet()
            }
            
            // Get player's rank in the guild
            val rank = rankService.getPlayerRank(playerId, guildId) ?: return getDefaultPermissions()
            
            // Map rank name to claim permissions using config
            return mapRankToClaimPermissions(rank.name)
            
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warn("Error computing permissions for player $playerId on claim $claimId", e)
            return emptySet()
        }
    }
    
    private fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean {
        val cacheKey = "${playerId}_${guildId}"
        
        // Check cache first
        membershipCache[cacheKey]?.let { return it }
        
        val isMember = memberService.getPlayerGuilds(playerId).contains(guildId)
        
        // Cache the result
        membershipCache[cacheKey] = isMember
        
        return isMember
    }
    
    private fun mapRankToClaimPermissions(rankName: String): Set<ClaimPermission> {
        val config = configService.loadConfig()
        
        // Get permissions for the specific rank from role mappings
        val rankPermissions = config.teamRolePermissions.roleMappings[rankName]
        
        if (rankPermissions.isNullOrEmpty()) {
            logger.debug("No permissions found for rank '$rankName', using defaults")
            return getDefaultPermissions()
        }
        
        // Convert string permissions to ClaimPermission enum values
        return rankPermissions.mapNotNull { permissionString ->
            try {
                ClaimPermission.valueOf(permissionString.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid permission '$permissionString' for rank '$rankName'")
                null
            }
        }.toSet()
    }
    
    private fun getDefaultPermissions(): Set<ClaimPermission> {
        val config = configService.loadConfig()
        val defaultPermissions = config.teamRolePermissions.defaultPermissions
        
        if (defaultPermissions.isEmpty()) {
            return setOf(ClaimPermission.VIEW)
        }
        
        return defaultPermissions.mapNotNull { permissionString ->
            try {
                ClaimPermission.valueOf(permissionString.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid default permission '$permissionString'")
                null
            }
        }.toSet()
    }
    
    override fun invalidatePlayerCache(playerId: UUID) {
        // Remove all cache entries for this player
        val keysToRemove = permissionCache.keys.filter { it.startsWith("${playerId}_") }
        keysToRemove.forEach { permissionCache.remove(it) }
        
        val membershipKeysToRemove = membershipCache.keys.filter { it.startsWith("${playerId}_") }
        membershipKeysToRemove.forEach { membershipCache.remove(it) }
        
        logger.debug("Invalidated cache for player $playerId")
    }
    
    override fun invalidateGuildCache(guildId: UUID) {
        // Remove cache entries for all members of this guild
        try {
            val members = memberService.getGuildMembers(guildId)
            members.forEach { member ->
                invalidatePlayerCache(member.playerId)
            }
            
            // Also remove membership cache entries for this guild
            val membershipKeysToRemove = membershipCache.keys.filter { it.endsWith("_${guildId}") }
            membershipKeysToRemove.forEach { membershipCache.remove(it) }
            
            logger.debug("Invalidated cache for guild $guildId")
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warn("Error invalidating guild cache for guild $guildId", e)
        }
    }
    
    override fun clearCache() {
        permissionCache.clear()
        membershipCache.clear()
        logger.info("Cleared all guild role permission caches")
    }
    
    /**
     * Cleanup resources when the service is destroyed.
     */
    fun destroy() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
        clearCache()
    }
}
