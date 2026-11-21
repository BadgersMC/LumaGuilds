package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription
import net.lumalyte.lg.application.results.claim.flags.DisableClaimFlagResult
import net.lumalyte.lg.application.results.claim.flags.EnableClaimFlagResult
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimAttributeResult
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimWriteCommandsTest {
    private val playerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) {
        every { uniqueId } returns playerId
    }
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val access = mockk<NexusClaimAccess>()
    private val updateDescription = mockk<UpdateClaimDescription>()
    private val enable = mockk<EnableClaimFlag>()
    private val disable = mockk<DisableClaimFlag>()
    private val details = mockk<GetClaimDetails>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(worldId, playerId, position = Position3D(0, 70, 0), name = "Origin")

    private fun description() = NexusClaimDescriptionCommand(lang, access, updateDescription, details)
    private fun addFlag() = NexusClaimAddFlagCommand(lang, access, enable, details)
    private fun removeFlag() = NexusClaimRemoveFlagCommand(lang, access, disable, details)

    private fun allow(permission: RankPermission) {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, permission) } returns true
        every { details.execute(claimId) } returns claim
    }

    @Test
    fun scannerPreservesRoutesPermissionsAndTypedArguments() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusClaimDescriptionCommand::class.java.packageName,
            NexusClaimDescriptionCommand::class.java.classLoader,
        )
        data class Route(val type: kotlin.reflect.KClass<*>, val name: String, val argName: String, val permission: String)
        val routes = listOf(
            Route(NexusClaimDescriptionCommand::class, "description", "description", "lumaguilds.command.claim.description"),
            Route(NexusClaimAddFlagCommand::class, "addflag", "flag", "lumaguilds.command.claim.addflag"),
            Route(NexusClaimRemoveFlagCommand::class, "removeflag", "flag", "lumaguilds.command.claim.removeflag"),
        )
        for (route in routes) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == route.type })
            assertEquals("claim", definition.annotation.name)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == listOf(route.name) })
            assertEquals(route.permission, handler.permission)
            assertTrue(handler.isPlayerOnly)
            val arg = handler.parameters.single { it.isArg }
            assertEquals(route.argName, arg.name)
            assertEquals(String::class, arg.type)
            assertTrue(!arg.isOptional)
        }
        val method = NexusClaimDescriptionCommand::class.java.declaredMethods.single { it.name == "description" }
        assertTrue(method.parameters.any { it.isAnnotationPresent(Greedy::class.java) })
    }

    @Test
    fun missingPartitionStopsAllMutations() {
        every { access.partitionAtPlayer(player) } returns null
        description().description(player, "Hello")
        addFlag().addFlag(player, Flag.FIRE.name)
        removeFlag().removeFlag(player, Flag.FIRE.name)
        verify(exactly = 0) { access.hasPermission(any(), any(), any()) }
        verify(exactly = 0) { updateDescription.execute(any(), any()) }
        verify(exactly = 0) { enable.execute(any(), any()) }
        verify(exactly = 0) { disable.execute(any(), any()) }
    }

    @Test
    fun deniedManagementPermissionsStopMutationAndAreRechecked() {
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns false
        every { access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS) } returns false

        description().description(player, "Denied")
        addFlag().addFlag(player, Flag.FIRE.name)
        removeFlag().removeFlag(player, Flag.FIRE.name)
        verify(exactly = 0) { updateDescription.execute(any(), any()) }
        verify(exactly = 0) { enable.execute(any(), any()) }
        verify(exactly = 0) { disable.execute(any(), any()) }

        every { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns true
        every { updateDescription.execute(claimId, "Allowed") } returns UpdateClaimAttributeResult.Success(claim)
        every { details.execute(claimId) } returns claim
        description().description(player, "Allowed")
        verify(exactly = 2) { access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) }
        verify(exactly = 1) { updateDescription.execute(claimId, "Allowed") }
    }

    @Test
    fun descriptionPreservesSuccessAndValidationMessages() {
        allow(RankPermission.MANAGE_CLAIMS)
        every { updateDescription.execute(claimId, "A fine home") } returns UpdateClaimAttributeResult.Success(claim)
        every { updateDescription.execute(claimId, "Long") } returns UpdateClaimAttributeResult.InputTextInvalid(
            listOf(TextValidationErrorResult.ExceededCharacterLimit("50")),
        )
        every { updateDescription.execute(claimId, "Bad") } returns UpdateClaimAttributeResult.InputTextInvalid(
            listOf(TextValidationErrorResult.InvalidCharacters("!")),
        )
        every { updateDescription.execute(claimId, "Word") } returns UpdateClaimAttributeResult.InputTextInvalid(
            listOf(TextValidationErrorResult.ContainsBlacklistedWord("Word")),
        )
        every { updateDescription.execute(claimId, "") } returns UpdateClaimAttributeResult.InputTextInvalid(
            listOf(TextValidationErrorResult.NoCharactersProvided),
        )
        val command = description()
        command.description(player, "A fine home")
        command.description(player, "Long")
        command.description(player, "Bad")
        command.description(player, "Word")
        command.description(player, "")

        verify(exactly = 1) { lang.msg("command.claim.description.success", "claim" to "Origin") }
        verify(exactly = 1) { lang.msg("command.claim.description.exceed_limit", "length" to 4, "limit" to "50") }
        verify(exactly = 1) { lang.msg("command.claim.description.invalid_character", "characters" to "!") }
        verify(exactly = 1) { lang.msg("command.claim.description.blacklisted_word", "word" to "Word") }
        verify(exactly = 1) { lang.msg("command.claim.description.blank") }
    }

    @Test
    fun descriptionHandlesMissingClaimsAndStorageFailure() {
        allow(RankPermission.MANAGE_CLAIMS)
        every { updateDescription.execute(claimId, "Lost") } returns UpdateClaimAttributeResult.ClaimNotFound
        every { updateDescription.execute(claimId, "Storage") } returns UpdateClaimAttributeResult.StorageError
        description().description(player, "Lost")
        description().description(player, "Storage")
        verify(exactly = 1) { lang.msg("command.common.unknown_claim") }
        verify(exactly = 1) { lang.msg("general.error") }
        verify(exactly = 0) { details.execute(any()) }
    }

    @Test
    fun addFlagPreservesSuccessAlreadyExistsAndFailures() {
        allow(RankPermission.MANAGE_FLAGS)
        every { enable.execute(Flag.FIRE, claimId) } returns EnableClaimFlagResult.Success
        every { enable.execute(Flag.MOB, claimId) } returns EnableClaimFlagResult.AlreadyExists
        every { enable.execute(Flag.TREE, claimId) } returns EnableClaimFlagResult.ClaimNotFound
        every { enable.execute(Flag.PISTON, claimId) } returns EnableClaimFlagResult.StorageError
        val command = addFlag()
        command.addFlag(player, Flag.FIRE.name)
        command.addFlag(player, Flag.MOB.name)
        command.addFlag(player, Flag.TREE.name)
        command.addFlag(player, Flag.PISTON.name)

        verify(exactly = 1) {
            lang.msg("command.claim.add_flag.success", "flag" to "flag.fire.name", "claim" to "Origin")
        }
        verify(exactly = 1) {
            lang.msg("command.claim.add_flag.already_exists", "claim" to "Origin", "flag" to "flag.mob.name")
        }
        verify(exactly = 1) { lang.msg("command.common.unknown_claim") }
        verify(exactly = 1) { lang.msg("general.error") }
    }

    @Test
    fun invalidFlagNamesFailBeforeClaimLookup() {
        addFlag().addFlag(player, "NOT_A_FLAG")
        removeFlag().removeFlag(player, "NOT_A_FLAG")
        verify(exactly = 0) { access.partitionAtPlayer(any()) }
        verify(exactly = 0) { enable.execute(any(), any()) }
        verify(exactly = 0) { disable.execute(any(), any()) }
        verify(exactly = 2) { lang.msg("general.error") }
    }

    @Test
    fun removeFlagPreservesSuccessMissingFlagAndFailures() {
        allow(RankPermission.MANAGE_FLAGS)
        every { disable.execute(Flag.FIRE, claimId) } returns DisableClaimFlagResult.Success
        every { disable.execute(Flag.MOB, claimId) } returns DisableClaimFlagResult.DoesNotExist
        every { disable.execute(Flag.TREE, claimId) } returns DisableClaimFlagResult.ClaimNotFound
        every { disable.execute(Flag.PISTON, claimId) } returns DisableClaimFlagResult.StorageError
        val command = removeFlag()
        command.removeFlag(player, Flag.FIRE.name)
        command.removeFlag(player, Flag.MOB.name)
        command.removeFlag(player, Flag.TREE.name)
        command.removeFlag(player, Flag.PISTON.name)

        verify(exactly = 1) {
            lang.msg("command.claim.remove_flag.success", "flag" to "flag.fire.name", "claim" to "Origin")
        }
        verify(exactly = 1) {
            lang.msg("command.claim.remove_flag.does_not_exist", "claim" to "Origin", "flag" to "flag.mob.name")
        }
        verify(exactly = 1) { lang.msg("command.common.unknown_claim") }
        verify(exactly = 1) { lang.msg("general.error") }
    }
}
