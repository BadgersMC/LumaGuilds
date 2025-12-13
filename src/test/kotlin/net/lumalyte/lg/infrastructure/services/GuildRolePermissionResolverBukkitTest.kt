package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.config.MainConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class GuildRolePermissionResolverBukkitTest {

    private lateinit var resolver: GuildRolePermissionResolverBukkit
    private lateinit var memberService: MemberService
    private lateinit var rankService: RankService
    private lateinit var claimRepository: ClaimRepository
    private lateinit var configService: ConfigService
    private lateinit var adminOverrideService: AdminOverrideService

    private lateinit var testPlayerId: UUID
    private lateinit var testGuildId: UUID
    private lateinit var testClaimId: UUID
    private lateinit var testClaim: Claim

    @BeforeEach
    fun setUp() {
        // Create mock services
        memberService = mockk(relaxed = true)
        rankService = mockk(relaxed = true)
        claimRepository = mockk(relaxed = true)
        configService = mockk(relaxed = true)
        adminOverrideService = mockk(relaxed = true)

        // Set up test UUIDs
        testPlayerId = UUID.randomUUID()
        testGuildId = UUID.randomUUID()
        testClaimId = UUID.randomUUID()

        // Create test claim owned by guild
        testClaim = mockk(relaxed = true)
        every { testClaim.teamId } returns testGuildId
        every { claimRepository.getById(testClaimId) } returns testClaim

        // Set up default config
        val mockConfig = MainConfig(
            teamRolePermissions = net.lumalyte.lg.config.TeamRolePermissions(
                defaultPermissions = setOf("VIEW"),
                roleMappings = mapOf(
                    "member" to setOf("VIEW", "DOOR"),
                    "officer" to setOf("VIEW", "DOOR", "CONTAINER", "BUILD")
                )
            )
        )
        every { configService.loadConfig() } returns mockConfig

        // Create resolver instance
        resolver = GuildRolePermissionResolverBukkit(
            memberService = memberService,
            rankService = rankService,
            claimRepository = claimRepository,
            configService = configService,
            adminOverrideService = adminOverrideService
        )
    }

    @Test
    fun `admin override should grant all permissions`() {
        // Given: Player has admin override enabled
        every { adminOverrideService.hasOverride(testPlayerId) } returns true

        // When: Check all permissions
        val allPermissions = ClaimPermission.entries.toSet()
        val grantedPermissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should have all permissions
        assertEquals(allPermissions, grantedPermissions)

        // Verify each individual permission
        ClaimPermission.entries.forEach { permission ->
            assertTrue(
                resolver.hasPermission(testPlayerId, testClaimId, permission),
                "Should have permission: $permission"
            )
        }
    }

    @Test
    fun `admin override should bypass guild membership check`() {
        // Given: Player has admin override enabled but is NOT in the guild
        every { adminOverrideService.hasOverride(testPlayerId) } returns true
        every { memberService.getPlayerGuilds(testPlayerId) } returns emptySet()

        // When: Get permissions
        val permissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should still have all permissions despite not being a member
        assertEquals(ClaimPermission.entries.toSet(), permissions)

        // Verify membership service was NOT called (override bypassed check)
        verify(exactly = 0) { memberService.getPlayerGuilds(testPlayerId) }
    }

    @Test
    fun `admin override should be checked before normal permission logic`() {
        // Given: Player has admin override enabled
        every { adminOverrideService.hasOverride(testPlayerId) } returns true

        // When: Get permissions
        resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should check override first and skip other service calls
        verify(exactly = 1) { adminOverrideService.hasOverride(testPlayerId) }
        verify(exactly = 0) { memberService.getPlayerGuilds(any()) }
        verify(exactly = 0) { rankService.getPlayerRank(any(), any()) }
    }

    @Test
    fun `hasPermission should return true for all permissions when override enabled`() {
        // Given: Player has admin override enabled
        every { adminOverrideService.hasOverride(testPlayerId) } returns true

        // Then: All permissions should return true
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.BUILD))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.CONTAINER))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.REDSTONE))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.DETONATE))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.HARVEST))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.DISPLAY))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.VEHICLE))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.SIGN))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.DOOR))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.TRADE))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.HUSBANDRY))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.EVENT))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.SLEEP))
        assertTrue(resolver.hasPermission(testPlayerId, testClaimId, ClaimPermission.VIEW))
    }

    @Test
    fun `normal permission resolution when override disabled`() {
        // Given: Player does NOT have override but is a guild member
        every { adminOverrideService.hasOverride(testPlayerId) } returns false
        every { memberService.getPlayerGuilds(testPlayerId) } returns setOf(testGuildId)

        val mockRank = mockk<Rank>(relaxed = true)
        every { mockRank.name } returns "member"
        every { rankService.getPlayerRank(testPlayerId, testGuildId) } returns mockRank

        // When: Get permissions
        val permissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should get normal permissions based on rank (VIEW, DOOR from config)
        assertEquals(setOf(ClaimPermission.VIEW, ClaimPermission.DOOR), permissions)

        // Verify normal service calls were made
        verify(exactly = 1) { memberService.getPlayerGuilds(testPlayerId) }
        verify(exactly = 1) { rankService.getPlayerRank(testPlayerId, testGuildId) }
    }

    @Test
    fun `non-member without override should have no permissions`() {
        // Given: Player does NOT have override and is NOT in guild
        every { adminOverrideService.hasOverride(testPlayerId) } returns false
        every { memberService.getPlayerGuilds(testPlayerId) } returns emptySet()

        // When: Get permissions
        val permissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should have no permissions
        assertTrue(permissions.isEmpty())

        // Verify rank service was NOT called (not a member)
        verify(exactly = 0) { rankService.getPlayerRank(any(), any()) }
    }

    @Test
    fun `cache should properly reflect override state`() {
        // Given: Player initially does NOT have override
        every { adminOverrideService.hasOverride(testPlayerId) } returns false
        every { memberService.getPlayerGuilds(testPlayerId) } returns setOf(testGuildId)

        val mockRank = mockk<Rank>(relaxed = true)
        every { mockRank.name } returns "member"
        every { rankService.getPlayerRank(testPlayerId, testGuildId) } returns mockRank

        // When: Get permissions (should cache result)
        val permissions1 = resolver.getPermissions(testPlayerId, testClaimId)
        assertEquals(setOf(ClaimPermission.VIEW, ClaimPermission.DOOR), permissions1)

        // When: Enable override and invalidate cache
        every { adminOverrideService.hasOverride(testPlayerId) } returns true
        resolver.invalidatePlayerCache(testPlayerId)

        // When: Get permissions again (should get new result)
        val permissions2 = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should now have all permissions
        assertEquals(ClaimPermission.entries.toSet(), permissions2)
    }

    @Test
    fun `admin override should work for claims not owned by guilds`() {
        // Given: Claim is NOT owned by a guild (teamId is null)
        val nonGuildClaim = mockk<Claim>(relaxed = true)
        every { nonGuildClaim.teamId } returns null
        every { claimRepository.getById(testClaimId) } returns nonGuildClaim

        // Given: Player has admin override enabled
        every { adminOverrideService.hasOverride(testPlayerId) } returns true

        // When: Get permissions
        val permissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should still have all permissions due to override
        assertEquals(ClaimPermission.entries.toSet(), permissions)
    }

    @Test
    fun `multiple calls with override should return consistent results`() {
        // Given: Player has admin override enabled
        every { adminOverrideService.hasOverride(testPlayerId) } returns true

        // When: Call getPermissions multiple times
        val result1 = resolver.getPermissions(testPlayerId, testClaimId)
        val result2 = resolver.getPermissions(testPlayerId, testClaimId)
        val result3 = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: All results should be identical
        assertEquals(result1, result2)
        assertEquals(result2, result3)
        assertEquals(ClaimPermission.entries.toSet(), result1)
    }

    @Test
    fun `override check should handle different players independently`() {
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()

        // Given: Only player1 has override enabled
        every { adminOverrideService.hasOverride(player1) } returns true
        every { adminOverrideService.hasOverride(player2) } returns false
        every { memberService.getPlayerGuilds(player2) } returns emptySet()

        // When: Get permissions for both players
        val permissions1 = resolver.getPermissions(player1, testClaimId)
        val permissions2 = resolver.getPermissions(player2, testClaimId)

        // Then: Player1 should have all permissions, player2 should have none
        assertEquals(ClaimPermission.entries.toSet(), permissions1)
        assertTrue(permissions2.isEmpty())
    }

    @Test
    fun `officer rank with override should still get all permissions`() {
        // Given: Player has override AND is an officer (should get all permissions, not just officer perms)
        every { adminOverrideService.hasOverride(testPlayerId) } returns true
        every { memberService.getPlayerGuilds(testPlayerId) } returns setOf(testGuildId)

        val mockRank = mockk<Rank>(relaxed = true)
        every { mockRank.name } returns "officer"
        every { rankService.getPlayerRank(testPlayerId, testGuildId) } returns mockRank

        // When: Get permissions
        val permissions = resolver.getPermissions(testPlayerId, testClaimId)

        // Then: Should have ALL permissions (not just officer permissions)
        assertEquals(ClaimPermission.entries.toSet(), permissions)

        // Verify override was checked and rank service was NOT called (override bypassed it)
        verify(exactly = 1) { adminOverrideService.hasOverride(testPlayerId) }
        verify(exactly = 0) { rankService.getPlayerRank(any(), any()) }
    }
}
