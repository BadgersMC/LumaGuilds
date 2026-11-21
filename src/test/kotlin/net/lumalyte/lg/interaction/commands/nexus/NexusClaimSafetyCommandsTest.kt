package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.results.claim.partition.RemovePartitionResult
import net.lumalyte.lg.application.results.player.ToggleClaimOverrideResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimSafetyCommandsTest {
    private val playerId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val access = mockk<NexusClaimAccess>()
    private val removePartition = mockk<RemovePartition>()
    private val details = mockk<GetClaimDetails>()
    private val toggle = mockk<ToggleClaimOverride>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(UUID.randomUUID(), playerId, position = Position3D(5, 70, -3), name = "Home")

    private fun removeCommand() = NexusClaimRemoveCommand(lang, access, removePartition, details)
    private fun overrideCommand() = NexusClaimOverrideCommand(lang, toggle)

    private fun allowRemoval() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS) } returns true
    }

    @Test
    fun scannerPreservesPermissionsAndPlayerOnly() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusClaimRemoveCommand::class.java.packageName,
            NexusClaimRemoveCommand::class.java.classLoader,
        )
        val remove = assertNotNull(definitions.singleOrNull { it.commandClass == NexusClaimRemoveCommand::class })
        assertEquals("claim", remove.annotation.name)
        val removeHandler = assertNotNull(remove.subcommands.singleOrNull { it.path == listOf("remove") })
        assertEquals("lumaguilds.command.claim.remove", removeHandler.permission)
        assertTrue(removeHandler.isPlayerOnly)
        assertTrue(removeHandler.parameters.all { it.isContext })

        val override = assertNotNull(definitions.singleOrNull { it.commandClass == NexusClaimOverrideCommand::class })
        assertEquals("claimoverride", override.annotation.name)
        val overrideHandler = assertNotNull(override.subcommands.singleOrNull())
        assertEquals("lumaguilds.command.claimoverride", overrideHandler.permission)
        assertTrue(overrideHandler.isPlayerOnly)
        assertTrue(overrideHandler.parameters.all { it.isContext })
    }

    @Test
    fun removeStopsAtUnknownPartitionWithoutMutation() {
        every { access.partitionAtPlayer(player) } returns null

        removeCommand().remove(player)

        verify(exactly = 0) { access.hasPermission(any(), any(), any()) }
        verify(exactly = 0) { removePartition.execute(any()) }
    }

    @Test
    fun removeDeniedWithoutDeleteClaimsPermissionDoesNotMutate() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS) } returns false

        removeCommand().remove(player)

        verify(exactly = 1) { access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS) }
        verify(exactly = 0) { removePartition.execute(any()) }
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun removeChecksAuthorizationOnEveryInvocation() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS) } returnsMany listOf(true, false)
        every { removePartition.execute(partition.id) } returns RemovePartitionResult.StorageError
        val command = removeCommand()

        command.remove(player)
        command.remove(player)

        verify(exactly = 2) { access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS) }
        verify(exactly = 1) { removePartition.execute(partition.id) }
    }

    @Test
    fun removeSuccessAndMissingPartitionIncludeClaimName() {
        allowRemoval()
        every { details.execute(claimId) } returns claim
        every { removePartition.execute(partition.id) } returns RemovePartitionResult.Success
        removeCommand().remove(player)
        verify(exactly = 1) { lang.msg("command.claim.remove.success", "claim" to "Home") }

        every { removePartition.execute(partition.id) } returns RemovePartitionResult.DoesNotExist
        removeCommand().remove(player)
        verify(exactly = 1) { lang.msg("command.claim.remove.unknown_partition", "claim" to "Home") }
        verify(exactly = 2) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun removeFallsBackToNameErrorWhenClaimDetailsMissing() {
        allowRemoval()
        every { details.execute(claimId) } returns null
        every { removePartition.execute(partition.id) } returns RemovePartitionResult.Success

        removeCommand().remove(player)

        verify(exactly = 1) { lang.msg("general.name_error") }
        verify(exactly = 1) { lang.msg("command.claim.remove.success", "claim" to "general.name_error") }
    }

    @Test
    fun removeMapsFailureResultsWithoutReadingClaimName() {
        allowRemoval()
        every { removePartition.execute(partition.id) } returnsMany listOf(
            RemovePartitionResult.Disconnected,
            RemovePartitionResult.ExposedClaimAnchor,
            RemovePartitionResult.StorageError,
        )
        val command = removeCommand()

        repeat(3) { command.remove(player) }

        verify(exactly = 1) { lang.msg("command.claim.remove.disconnected") }
        verify(exactly = 1) { lang.msg("command.claim.remove.exposed_anchor") }
        verify(exactly = 1) { lang.msg("general.error") }
        verify(exactly = 0) { details.execute(any()) }
        verify(exactly = 3) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun overrideMapsToggleResultsWithoutManagementAuthorization() {
        every { toggle.execute(playerId) } returnsMany listOf(
            ToggleClaimOverrideResult.Success(true),
            ToggleClaimOverrideResult.Success(false),
            ToggleClaimOverrideResult.PlayerNotFound,
            ToggleClaimOverrideResult.StorageError,
        )
        val command = overrideCommand()

        repeat(4) { command.toggle(player) }

        verify(exactly = 1) { lang.msg("command.claim_override.enabled") }
        verify(exactly = 1) { lang.msg("command.claim_override.disabled") }
        verify(exactly = 2) { lang.msg("general.error") }
        verify(exactly = 4) { player.sendMessage(any<Component>()) }
        verify(exactly = 4) { toggle.execute(playerId) }
    }
}
