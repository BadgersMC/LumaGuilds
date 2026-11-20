package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimMetadataCommandsTest {
    private val playerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val access = mockk<NexusClaimAccess>()
    private val details = mockk<GetClaimDetails>()
    private val flags = mockk<GetClaimFlags>()
    private val permissions = mockk<GetClaimPermissions>()
    private val partitions = mockk<GetClaimPartitions>()
    private val blockCount = mockk<GetClaimBlockCount>()
    private val trusted = mockk<GetPlayersWithPermissionInClaim>()
    private val updateName = mockk<UpdateClaimName>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(worldId, playerId, position = Position3D(5, 70, -3), name = "Original")
    private fun infoCommand() = NexusClaimInfoCommand(
        lang, access, details, flags, permissions, partitions, blockCount, trusted,
    )
    private fun renameCommand() = NexusClaimRenameCommand(lang, access, updateName, details)

    @Test
    fun scannerPreservesClaimInfoAndRenamePermissionsAndRequiredNameArgument() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusClaimInfoCommand::class.java.packageName,
            NexusClaimInfoCommand::class.java.classLoader,
        )
        for ((type, subcommand, permission) in listOf(
            Triple(NexusClaimInfoCommand::class, "info", "lumaguilds.command.claim.info"),
            Triple(NexusClaimRenameCommand::class, "rename", "lumaguilds.command.claim.rename"),
        )) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type })
            assertEquals("claim", definition.annotation.name)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == listOf(subcommand) })
            assertEquals(permission, handler.permission)
            assertTrue(handler.isPlayerOnly)
            if (subcommand == "rename") {
                val arg = handler.parameters.single { it.isArg }
                assertEquals("name", arg.name)
                assertTrue(!arg.isOptional)
            } else {
                assertTrue(handler.parameters.all { it.isContext })
            }
        }
    }

    @Test
    fun infoStopsAtUnknownPartitionOrDeletedClaimWithoutReadingMetadata() {
        every { access.partitionAtPlayer(player) } returns null
        infoCommand().info(player)
        verify(exactly = 0) { details.execute(any()) }

        every { access.partitionAtPlayer(player) } returns partition
        every { details.execute(claimId) } returns null
        infoCommand().info(player)

        verify(exactly = 0) { flags.execute(any()) }
        verify(exactly = 0) { permissions.execute(any()) }
        verify(exactly = 0) { partitions.execute(any()) }
        verify(exactly = 0) { blockCount.execute(any()) }
        verify(exactly = 0) { trusted.execute(any()) }
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun infoRendersLegacyClaimMetadataWithoutManagementPermissionCheck() {
        every { access.partitionAtPlayer(player) } returns partition
        every { details.execute(claimId) } returns claim
        every { flags.execute(claimId) } returns listOf(Flag.FIRE)
        every { permissions.execute(claimId) } returns emptyList()
        every { partitions.execute(claimId) } returns listOf(partition)
        every { blockCount.execute(claimId) } returns 256
        every { trusted.execute(claimId) } returns listOf(UUID.randomUUID(), UUID.randomUUID())
        val offline = mockk<OfflinePlayer> { every { name } returns "Owner" }

        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getOfflinePlayer(playerId) } returns offline
            infoCommand().info(player)
        } finally {
            unmockkStatic(Bukkit::class)
        }

        verify(exactly = 0) { access.hasPermission(any(), any(), any()) }
        verify(exactly = 1) { lang.msg("command.claim.info.row.owner", "owner" to "Owner") }
        verify(exactly = 1) { lang.msg("command.claim.info.row.partition_count", "partition_count" to "1") }
        verify(exactly = 1) { lang.msg("command.claim.info.row.block_count", "block_count" to "256") }
        verify(exactly = 1) { lang.msg("command.claim.info.row.trusted_users", "trusted_users" to "2") }
        verify(exactly = 1) { lang.msg(Flag.FIRE.nameKey) }
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun renameChecksCurrentManagementPermissionBeforeMutation() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns false

        renameCommand().rename(player, "Nope")

        verify(exactly = 1) { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) }
        verify(exactly = 0) { updateName.execute(any(), any()) }
    }

    @Test
    fun renamePreservesSuccessAndDuplicateNameResponses() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns true
        every { updateName.execute(claimId, "NewName") } returns UpdateClaimNameResult.Success(claim.copy(name = "NewName"))
        every { details.execute(claimId) } returns claim.copy(name = "NewName")

        renameCommand().rename(player, "NewName")
        verify(exactly = 1) {
            lang.msg("command.claim.rename.success", "claim" to "NewName", "name" to "NewName")
        }

        every { updateName.execute(claimId, "Taken") } returns UpdateClaimNameResult.NameAlreadyExists
        renameCommand().rename(player, "Taken")
        verify(exactly = 1) { lang.msg("command.claim.rename.already_exists", "name" to "Taken") }
        verify(exactly = 1) { details.execute(claimId) }
    }

    @Test
    fun renamePreservesValidationFailureAndStorageErrorMessages() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns true
        every { updateName.execute(claimId, "Overlength") } returns UpdateClaimNameResult.InputTextInvalid(
            listOf(TextValidationErrorResult.ExceededCharacterLimit("50")),
        )
        every { updateName.execute(claimId, "") } returns UpdateClaimNameResult.InputTextInvalid(
            listOf(TextValidationErrorResult.NoCharactersProvided),
        )
        every { updateName.execute(claimId, "Database") } returns UpdateClaimNameResult.StorageError

        renameCommand().rename(player, "Overlength")
        renameCommand().rename(player, "")
        renameCommand().rename(player, "Database")

        verify(exactly = 1) {
            lang.msg("command.claim.rename.exceed_limit", "length" to 10, "limit" to "50")
        }
        verify(exactly = 1) { lang.msg("command.claim.rename.blank") }
        verify(exactly = 1) { lang.msg("general.error") }
    }
}
