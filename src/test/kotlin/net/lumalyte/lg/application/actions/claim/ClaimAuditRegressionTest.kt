package net.lumalyte.lg.application.actions.claim

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor
import net.lumalyte.lg.application.actions.claim.flag.DoesClaimHaveFlag
import net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.results.claim.anchor.MoveClaimAnchorResult
import net.lumalyte.lg.application.results.claim.flags.DoesClaimHaveFlagResult
import net.lumalyte.lg.application.results.claim.permission.GrantAllPlayerClaimPermissionsResult
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
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

        val result = MoveClaimAnchor(claims, world, lookup, override)
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

        val result = MoveClaimAnchor(claims, world, lookup, override)
            .execute(claimId, owner, worldId, newPosition)

        assertIs<MoveClaimAnchorResult.StorageError>(result)
        verify(exactly = 0) { world.breakWithoutItemDrop(any(), any()) }
    }

    private fun claim(
        claimId: UUID = UUID.randomUUID(),
        worldId: UUID = UUID.randomUUID(),
        owner: UUID = UUID.randomUUID(),
        position: Position3D = Position3D(0, 64, 0),
    ) = Claim(worldId, owner, position = position, name = "Audit").copy(id = claimId)
}
