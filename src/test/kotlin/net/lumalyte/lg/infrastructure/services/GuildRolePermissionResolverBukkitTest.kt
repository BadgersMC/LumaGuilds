package net.lumalyte.lg.infrastructure.services

import dev.mizarc.bellclaims.application.persistence.ClaimRepository
import dev.mizarc.bellclaims.application.services.ConfigService
import dev.mizarc.bellclaims.application.services.MemberService
import dev.mizarc.bellclaims.application.services.RankService
import dev.mizarc.bellclaims.config.MainConfig
import dev.mizarc.bellclaims.config.TeamRolePermissions
import dev.mizarc.bellclaims.domain.entities.Claim
import dev.mizarc.bellclaims.domain.entities.Member
import dev.mizarc.bellclaims.domain.entities.Rank
import dev.mizarc.bellclaims.domain.values.ClaimPermission
import dev.mizarc.bellclaims.domain.values.Position3D
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

class GuildRolePermissionResolverBukkitTest {
    
    private lateinit var guildRolePermissionResolver: GuildRolePermissionResolverBukkit
    private lateinit var mockMemberService: MemberService
    private lateinit var mockRankService: RankService
    private lateinit var mockClaimRepository: ClaimRepository
    private lateinit var mockConfigService: ConfigService
    
    private lateinit var playerId: UUID
    private lateinit var guildId: UUID
    private lateinit var claimId: UUID
    private lateinit var rankId: UUID
    
    @BeforeEach
    fun setUp() {
        mockMemberService = mockk<MemberService>()
        mockRankService = mockk<RankService>()
        mockClaimRepository = mockk<ClaimRepository>()
        mockConfigService = mockk<ConfigService>()
        
        playerId = UUID.randomUUID()
        guildId = UUID.randomUUID()
        claimId = UUID.randomUUID()
        rankId = UUID.randomUUID()
        
        // Setup default config with role mappings
        val rolePermissions = TeamRolePermissions(
            roleMappings = mapOf(
                "Owner" to setOf("BUILD", "HARVEST", "CONTAINER", "VIEW"),
                "Member" to setOf("VIEW", "HARVEST")
            ),
            defaultPermissions = setOf("VIEW")
        )
        val config = MainConfig(teamRolePermissions = rolePermissions)
        every { mockConfigService.loadConfig() } returns config
        
        guildRolePermissionResolver = GuildRolePermissionResolverBukkit(
            mockMemberService,
            mockRankService,
            mockClaimRepository,
            mockConfigService
        )
    }
    
    @Test
    fun `should return permissions for guild member with valid rank`() {
        // Given: Player is in guild and has Owner rank
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // When
        val permissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then
        assertEquals(setOf(ClaimPermission.BUILD, ClaimPermission.HARVEST, ClaimPermission.CONTAINER, ClaimPermission.VIEW), permissions)
    }
    
    @Test
    fun `should return true for hasPermission when player has the permission`() {
        // Given: Player is in guild with permissions that include BUILD
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // When
        val hasPermission = guildRolePermissionResolver.hasPermission(playerId, claimId, ClaimPermission.BUILD)
        
        // Then
        assertTrue(hasPermission)
    }
    
    @Test
    fun `should return false for hasPermission when player does not have the permission`() {
        // Given: Player is in guild with Member rank (no BUILD permission)
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Member", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // When
        val hasPermission = guildRolePermissionResolver.hasPermission(playerId, claimId, ClaimPermission.BUILD)
        
        // Then
        assertFalse(hasPermission)
    }
    
    @Test
    fun `should return empty permissions when player is not in guild that owns claim`() {
        // Given: Player is not in the guild that owns the claim
        val claim = createTestClaim(guildId)
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(UUID.randomUUID()) // Different guild
        
        // When
        val permissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then
        assertTrue(permissions.isEmpty())
    }
    
    @Test
    fun `should return empty permissions when claim is not owned by a guild`() {
        // Given: Claim is owned by a player, not a guild
        val claim = createTestClaim(null) // No team ownership
        
        every { mockClaimRepository.getById(claimId) } returns claim
        
        // When
        val permissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then
        assertTrue(permissions.isEmpty())
    }
    
    @Test
    fun `should return default permissions when player has no rank in guild`() {
        // Given: Player is in guild but has no rank
        val claim = createTestClaim(guildId)
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns null
        
        // When
        val permissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then
        assertEquals(setOf(ClaimPermission.VIEW), permissions)
    }
    
    @Test
    fun `should return default permissions when rank is not in config`() {
        // Given: Player has a rank not configured in role mappings
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "UnknownRank", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // When
        val permissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then
        assertEquals(setOf(ClaimPermission.VIEW), permissions)
    }
    
    @Test
    fun `should cache permissions and not call services again`() {
        // Given: First call to get permissions
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // When: Call twice
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        val secondCallPermissions = guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // Then: Services should only be called once due to caching
        verify(exactly = 1) { mockClaimRepository.getById(claimId) }
        verify(exactly = 1) { mockMemberService.getPlayerGuilds(playerId) }
        verify(exactly = 1) { mockRankService.getPlayerRank(playerId, guildId) }
        
        assertEquals(setOf(ClaimPermission.BUILD, ClaimPermission.HARVEST, ClaimPermission.CONTAINER, ClaimPermission.VIEW), secondCallPermissions)
    }
    
    @Test
    fun `should invalidate player cache correctly`() {
        // Given: Cached permissions for player
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // First call to cache permissions
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // When: Invalidate cache
        guildRolePermissionResolver.invalidatePlayerCache(playerId)
        
        // Then: Second call should hit services again
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        verify(exactly = 2) { mockClaimRepository.getById(claimId) }
    }
    
    @Test
    fun `should invalidate guild cache correctly`() {
        // Given: Player in guild with cached permissions
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        val member = Member(playerId, guildId, rankId, Instant.now())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        every { mockMemberService.getGuildMembers(guildId) } returns setOf(member)
        
        // Cache permissions
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // When: Invalidate guild cache
        guildRolePermissionResolver.invalidateGuildCache(guildId)
        
        // Then: Should fetch guild members and invalidate their caches
        verify(exactly = 1) { mockMemberService.getGuildMembers(guildId) }
    }
    
    @Test
    fun `should clear all cache correctly`() {
        // Given: Some cached permissions
        val claim = createTestClaim(guildId)
        val rank = Rank(rankId, guildId, "Owner", 0, emptySet())
        
        every { mockClaimRepository.getById(claimId) } returns claim
        every { mockMemberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { mockRankService.getPlayerRank(playerId, guildId) } returns rank
        
        // Cache permissions
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        
        // When: Clear all cache
        guildRolePermissionResolver.clearCache()
        
        // Then: Next call should hit services again
        guildRolePermissionResolver.getPermissions(playerId, claimId)
        verify(exactly = 2) { mockClaimRepository.getById(claimId) }
    }
    
    private fun createTestClaim(teamId: UUID?): Claim {
        return Claim(
            claimId,
            UUID.randomUUID(), // worldId
            playerId,
            teamId,
            Instant.now(),
            "TestClaim",
            "Test Description",
            Position3D(0, 64, 0),
            "GRASS_BLOCK"
        )
    }
}
