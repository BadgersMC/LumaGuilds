package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.services.ClaimManagementAuthorizer
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NexusClaimAccessTest {
    private val playerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val lookup = mockk<GetPartitionByPosition>()
    private val overrides = mockk<DoesPlayerHaveClaimOverride>()
    private val details = mockk<GetClaimDetails>()
    private val authorizer = mockk<ClaimManagementAuthorizer>()
    private val access = NexusClaimAccess(lang, lookup, overrides, details, authorizer)
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))

    @Test
    fun locationLookupFloorsNegativeBlockCoordinatesAndIncludesWorld() {
        val world = mockk<World> { every { uid } returns worldId }
        every { player.location } returns Location(world, -1.5, 70.0, -1.5)
        every { player.world } returns world
        every { lookup.execute(any(), worldId) } returns null

        assertTrue(access.partitionAtPlayer(player) == null)
        verify(exactly = 1) {
            lookup.execute(match { it.x == -2 && it.y == 70 && it.z == -2 }, worldId)
        }
        verify(exactly = 1) { player.sendMessage(Component.text("command.common.unknown_partition")) }
    }

    @Test
    fun overrideGrantsAccessImmediatelyAndStorageErrorFailsClosedSilently() {
        every { overrides.execute(playerId) } returns DoesPlayerHaveClaimOverrideResult.Success(true)
        assertTrue(access.hasPermission(player, partition))
        verify(exactly = 0) { details.execute(any()) }
        verify(exactly = 0) { authorizer.hasPermission(any(), any(), any()) }

        every { overrides.execute(playerId) } returns DoesPlayerHaveClaimOverrideResult.StorageError
        assertFalse(access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS))
        verify(exactly = 0) { details.execute(any()) }
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun claimAccessRechecksRequestedGuildPermissionOnEachInvocation() {
        val claim = Claim(worldId, playerId, position = Position3D(0, 70, 0), name = "Owned")
        every { overrides.execute(playerId) } returns DoesPlayerHaveClaimOverrideResult.Success(false)
        every { details.execute(claimId) } returns claim
        every { authorizer.hasPermission(playerId, claim, RankPermission.MANAGE_FLAGS) } returns false
        assertFalse(access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS))
        verify(exactly = 1) { player.sendMessage(Component.text("command.common.no_claim_permission")) }

        every { authorizer.hasPermission(playerId, claim, RankPermission.MANAGE_FLAGS) } returns true
        assertTrue(access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS))
        verify(exactly = 2) { overrides.execute(playerId) }
        verify(exactly = 2) { details.execute(claimId) }
        verify(exactly = 2) { authorizer.hasPermission(playerId, claim, RankPermission.MANAGE_FLAGS) }
    }

    @Test
    fun missingClaimFailsClosedWithoutAuthorizationOrErrorMessage() {
        every { overrides.execute(playerId) } returns DoesPlayerHaveClaimOverrideResult.Success(false)
        every { details.execute(claimId) } returns null
        assertFalse(access.hasPermission(player, partition))
        verify(exactly = 0) { authorizer.hasPermission(any(), any(), any()) }
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }
}
