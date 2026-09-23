package net.lumalyte.lg.application.actions.claim

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor
import net.lumalyte.lg.application.actions.claim.flag.DoesClaimHaveFlag
import net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantGuildMembersClaimPermissions
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.results.claim.anchor.MoveClaimAnchorResult
import net.lumalyte.lg.application.results.claim.flags.DoesClaimHaveFlagResult
import net.lumalyte.lg.application.results.claim.permission.GrantAllPlayerClaimPermissionsResult
import net.lumalyte.lg.application.results.claim.permission.GrantGuildMembersClaimPermissionsResult
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.services.ClaimManagementAuthorizer
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener
import net.lumalyte.lg.interaction.listeners.forEachNonExemptTarget
import net.lumalyte.lg.interaction.listeners.hasDeniedRelevantTarget
import org.bukkit.event.block.BlockExplodeEvent
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertIs

class ClaimAuditRegressionTest {
    @Test
    fun `grant all permissions adds every permission and never removes`() {
        val claimId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val claims = mockk<ClaimRepository>()
        val access = mockk<PlayerAccessRepository>()
        every { claims.getById(claimId) } returns claim(claimId = claimId)
        ClaimPermission.entries.forEach {
            every { access.add(claimId, playerId, it) } returns true
        }

        val result = GrantAllPlayerClaimPermissions(claims, access).execute(claimId, playerId)

        assertIs<GrantAllPlayerClaimPermissionsResult.Success>(result)
        ClaimPermission.entries.forEach {
            verify(exactly = 1) { access.add(claimId, playerId, it) }
            verify(exactly = 0) { access.remove(claimId, playerId, it) }
        }
    }

    @Test
    fun `missing claim returns ClaimNotFound without querying flags`() {
        val claimId = UUID.randomUUID()
        val claims = mockk<ClaimRepository>()
        val flags = mockk<ClaimFlagRepository>()
        every { claims.getById(claimId) } returns null

        val result = DoesClaimHaveFlag(claims, flags).execute(claimId, Flag.FIRE)

        assertIs<DoesClaimHaveFlagResult.ClaimNotFound>(result)
        verify(exactly = 0) { flags.doesClaimHaveFlag(any(), any()) }
    }

    @Test
    fun `anchor may move to empty destination and updates world id`() {
        val owner = UUID.randomUUID()
        val oldWorld = UUID.randomUUID()
        val newWorld = UUID.randomUUID()
        val claimId = UUID.randomUUID()
        val oldPosition = Position3D(1, 64, 1)
        val newPosition = Position3D(50, 70, 50)
        val original = claim(claimId, oldWorld, owner, oldPosition)
        val claims = mockk<ClaimRepository>()
        val world = mockk<WorldManipulationService>()
        val lookup = mockk<GetClaimAtPosition>()
        val override = mockk<DoesPlayerHaveClaimOverride>()
        every { claims.getById(claimId) } returns original
        every { lookup.execute(newWorld, newPosition) } returns GetClaimAtPositionResult.NoClaimFound
        every { override.execute(owner) } returns DoesPlayerHaveClaimOverrideResult.Success(false)
        every { claims.update(any()) } returns true
        every { world.breakWithoutItemDrop(oldWorld, oldPosition) } returns true

        val result = MoveClaimAnchor(claims, world, lookup, override, ClaimManagementAuthorizer(mockk(relaxed = true)))
            .execute(claimId, owner, newWorld, newPosition)

        assertIs<MoveClaimAnchorResult.Success>(result)
        verify {
            claims.update(match {
                it.id == claimId && it.worldId == newWorld && it.position == newPosition
            })
        }
        verify { world.breakWithoutItemDrop(oldWorld, oldPosition) }
    }

    @Test
    fun `anchor persistence failure leaves old block intact`() {
        val owner = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        val claimId = UUID.randomUUID()
        val oldPosition = Position3D(1, 64, 1)
        val newPosition = Position3D(2, 64, 2)
        val original = claim(claimId, worldId, owner, oldPosition)
        val claims = mockk<ClaimRepository>()
        val world = mockk<WorldManipulationService>()
        val lookup = mockk<GetClaimAtPosition>()
        val override = mockk<DoesPlayerHaveClaimOverride>()
        every { claims.getById(claimId) } returns original
        every { lookup.execute(worldId, newPosition) } returns GetClaimAtPositionResult.NoClaimFound

        every { override.execute(owner) } returns DoesPlayerHaveClaimOverrideResult.Success(false)
        every { claims.update(any()) } returns false

        val result = MoveClaimAnchor(claims, world, lookup, override, ClaimManagementAuthorizer(mockk(relaxed = true)))
            .execute(claimId, owner, worldId, newPosition)

        assertIs<MoveClaimAnchorResult.StorageError>(result)
        verify(exactly = 0) { world.breakWithoutItemDrop(any(), any()) }
    }

    @Test
    fun `guild permission manager can administer converted claim permissions`() {
        val originalOwner = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val converted = claim(owner = originalOwner, guildId = guildId)
        val claims = mockk<ClaimRepository>()
        val members = mockk<MemberService>()
        val access = mockk<PlayerAccessRepository>()
        every { claims.getById(converted.id) } returns converted
        every { members.hasPermission(manager, guildId, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { members.getGuildMembers(guildId) } returns emptySet()

        val result = GrantGuildMembersClaimPermissions(claims, members, access)
            .execute(converted.id, manager)

        assertIs<GrantGuildMembersClaimPermissionsResult.NoGuildMembers>(result)
        verify { members.hasPermission(manager, guildId, RankPermission.MANAGE_PERMISSIONS) }
    }

    @Test
    fun `guild permission manager is included when sharing with all guild members`() {
        val originalOwner = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val otherMember = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val rankId = UUID.randomUUID()
        val converted = claim(owner = originalOwner, guildId = guildId)
        val claims = mockk<ClaimRepository>()
        val members = mockk<MemberService>()
        val access = mockk<PlayerAccessRepository>()

        every { claims.getById(converted.id) } returns converted
        every { members.hasPermission(manager, guildId, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { members.getGuildMembers(guildId) } returns setOf(
            Member(originalOwner, guildId, rankId, Instant.EPOCH),
            Member(manager, guildId, rankId, Instant.EPOCH),
            Member(otherMember, guildId, rankId, Instant.EPOCH),
        )
        ClaimPermission.entries.forEach { permission ->
            every { access.add(converted.id, manager, permission) } returns true
            every { access.add(converted.id, otherMember, permission) } returns true
        }

        val result = GrantGuildMembersClaimPermissions(claims, members, access)
            .execute(converted.id, manager)

        assertIs<GrantGuildMembersClaimPermissionsResult.Success>(result)
        ClaimPermission.entries.forEach { permission ->
            verify(exactly = 0) { access.add(converted.id, originalOwner, permission) }
            verify(exactly = 1) { access.add(converted.id, manager, permission) }
            verify(exactly = 1) { access.add(converted.id, otherMember, permission) }
        }
    }

    @Test
    fun `later denied protection target is checked after an allowed target`() {
        val checked = mutableListOf<String>()
        val denied = hasDeniedRelevantTarget(
            listOf("allowed", "denied"),
            isRelevant = { true },
            isDenied = {
                checked += it
                it == "denied"
            },
        )

        kotlin.test.assertTrue(denied)
        kotlin.test.assertEquals(listOf("allowed", "denied"), checked)
    }

    @Test
    fun `exempt potion target does not skip later protected entities`() {
        val visited = mutableListOf<String>()
        forEachNonExemptTarget(
            listOf("monster", "animal-one", "player", "animal-two"),
            isExempt = { it == "monster" || it == "player" },
        ) { visited += it }

        kotlin.test.assertEquals(listOf("animal-one", "animal-two"), visited)
    }

    @Test
    fun `block explosion protection listens to the block explosion event`() {
        val method = WorldClaimProtectionListener::class.java.getDeclaredMethod(
            "onBlockExplodeEvent",
            BlockExplodeEvent::class.java,
        )
        kotlin.test.assertEquals(BlockExplodeEvent::class.java, method.parameterTypes.single())
    }

    @Test
    fun `guild manager can move converted claim after original owner is gone`() {
        val originalOwner = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        val claimId = UUID.randomUUID()
        val oldPosition = Position3D(1, 64, 1)
        val newPosition = Position3D(2, 64, 2)
        val original = claim(claimId, worldId, originalOwner, oldPosition, guildId)
        val claims = mockk<ClaimRepository>()
        val world = mockk<WorldManipulationService>()
        val lookup = mockk<GetClaimAtPosition>()
        val override = mockk<DoesPlayerHaveClaimOverride>()
        val members = mockk<MemberService>()
        every { claims.getById(claimId) } returns original
        every { lookup.execute(worldId, newPosition) } returns GetClaimAtPositionResult.NoClaimFound
        every { override.execute(manager) } returns DoesPlayerHaveClaimOverrideResult.Success(false)
        every { members.hasPermission(manager, guildId, RankPermission.MANAGE_CLAIMS) } returns true
        every { claims.update(any()) } returns true
        every { world.breakWithoutItemDrop(worldId, oldPosition) } returns true

        val result = MoveClaimAnchor(
            claims,
            world,
            lookup,
            override,
            ClaimManagementAuthorizer(members),
        ).execute(claimId, manager, worldId, newPosition)

        assertIs<MoveClaimAnchorResult.Success>(result)
        verify { members.hasPermission(manager, guildId, RankPermission.MANAGE_CLAIMS) }
    }

    @Test
    fun `guild rank permission is required for converted claim management`() {
        val owner = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val claim = claim(owner = owner, guildId = guildId)
        val members = mockk<MemberService>()
        every { members.hasPermission(manager, guildId, RankPermission.MANAGE_FLAGS) } returns true
        every { members.hasPermission(manager, guildId, RankPermission.DELETE_CLAIMS) } returns false
        val authorizer = ClaimManagementAuthorizer(members)

        kotlin.test.assertTrue(authorizer.hasPermission(manager, claim, RankPermission.MANAGE_FLAGS))
        kotlin.test.assertFalse(authorizer.hasPermission(manager, claim, RankPermission.DELETE_CLAIMS))
        kotlin.test.assertTrue(authorizer.hasPermission(owner, claim, RankPermission.DELETE_CLAIMS))
    }

    private fun claim(
        claimId: UUID = UUID.randomUUID(),
        worldId: UUID = UUID.randomUUID(),
        owner: UUID = UUID.randomUUID(),
        position: Position3D = Position3D(0, 64, 0),
        guildId: UUID? = null,
    ) = Claim(worldId, owner, position = position, name = "Audit").copy(id = claimId, teamId = guildId)
}
