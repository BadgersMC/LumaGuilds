package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.results.claim.permission.GrantClaimWidePermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokeClaimWidePermissionResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NexusClaimWidePermissionCommandsTest {
    private val playerId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val sent = mutableListOf<Component>()
    private val keys = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers {
            val key = firstArg<String>()
            val args = args.drop(1).flatMap { (it as Array<*>).toList() }
                .filterIsInstance<Pair<*, *>>().associate { it.first as String to it.second }
            keys += key to args
            Component.text(key)
        }
    }
    private val access = mockk<NexusClaimAccess>()
    private val details = mockk<GetClaimDetails>()
    private val grant = mockk<GrantClaimWidePermission>()
    private val revoke = mockk<RevokeClaimWidePermission>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(UUID.randomUUID(), playerId, position = Position3D(1, 64, 1), name = "Home")

    init {
        every { player.sendMessage(any<Component>()) } answers { sent += firstArg<Component>() }
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { details.execute(claimId) } returns claim
    }

    private fun trust() = NexusTrustAllCommand(lang, access, grant, details)
    private fun untrust() = NexusUntrustAllCommand(lang, access, revoke, details)

    @Test
    fun scannerPreservesPermissionsAndRequiredPermissionArgument() {
        val definitions = scanGuildCommandDefinitions()
        for ((type, sub, perm) in listOf(
            Triple(NexusTrustAllCommand::class, "trustall", "lumaguilds.command.claim.trustall"),
            Triple(NexusUntrustAllCommand::class, "untrustall", "lumaguilds.command.claim.untrustall"),
        )) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type })
            assertEquals("claim", definition.annotation.name)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == listOf(sub) })
            assertEquals(perm, handler.permission)
            assertTrue(handler.isPlayerOnly)
            val arg = handler.parameters.single { it.isArg }
            assertEquals("permission", arg.name)
            assertTrue(!arg.isOptional)
        }
    }

    @Test
    fun parserPreservesLegacySimplifiedEnumMatchingAndCoversEveryPermission() {
        ClaimPermission.entries.forEach {
            assertEquals(it, parseClaimPermission(it.name))
            assertEquals(it, parseClaimPermission(it.name.lowercase()))
        }
        assertNull(parseClaimPermission(""))
        assertEquals(ClaimPermission.BUILD, parseClaimPermission(" build"))
        assertEquals(ClaimPermission.BUILD, parseClaimPermission("build "))
        assertEquals(ClaimPermission.BUILD, parseClaimPermission("b-u_i.l-d"))
        assertNull(parseClaimPermission("bogus"))
        assertNull(parseClaimPermission("bui"), "legacy enum matching does not accept partial prefixes")
        assertNull(parseClaimPermission("BUILD,HARVEST"))
    }

    @Test
    fun invalidValueNeverTouchesClaimStateOrAuthorization() {
        trust().trustAll(player, "nope")
        untrust().untrustAll(player, "nope")

        verify(exactly = 0) { access.partitionAtPlayer(any()) }
        verify(exactly = 0) { access.hasPermission(any(), any(), any()) }
        verify(exactly = 0) { grant.execute(any(), any()) }
        verify(exactly = 0) { revoke.execute(any(), any()) }
        assertEquals(listOf("general.error", "general.error"), keys.map { it.first })
        assertEquals(2, sent.size)
    }

    @Test
    fun unknownPartitionOrDeniedPermissionStopsBeforeMutation() {
        every { access.partitionAtPlayer(player) } returns null
        trust().trustAll(player, "build")
        untrust().untrustAll(player, "build")

        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns false
        trust().trustAll(player, "build")
        untrust().untrustAll(player, "build")

        verify(exactly = 0) { grant.execute(any(), any()) }
        verify(exactly = 0) { revoke.execute(any(), any()) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun authorizationIsCheckedImmediatelyBeforeMutation() {
        every { grant.execute(claimId, ClaimPermission.BUILD) } returns GrantClaimWidePermissionResult.Success
        every { revoke.execute(claimId, ClaimPermission.BUILD) } returns RevokeClaimWidePermissionResult.Success
        trust().trustAll(player, "Build")
        untrust().untrustAll(player, "BUILD")

        verifyOrder {
            access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)
            grant.execute(claimId, ClaimPermission.BUILD)
            access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)
            revoke.execute(claimId, ClaimPermission.BUILD)
        }
    }

    @Test
    fun grantResultsMapToLegacyKeysAndPlaceholders() {
        val expected = listOf(
            GrantClaimWidePermissionResult.Success to "command.claim.trust_all.success",
            GrantClaimWidePermissionResult.AlreadyExists to "command.claim.trust_all.already_exists",
            GrantClaimWidePermissionResult.ClaimNotFound to "command.common.unknown_claim",
            GrantClaimWidePermissionResult.StorageError to "general.error",
        )
        for ((result, key) in expected) {
            keys.clear()
            every { grant.execute(claimId, ClaimPermission.DOOR) } returns result
            trust().trustAll(player, "door")
            assertEquals(key, keys.last().first)
            if (key.startsWith("command.claim.trust_all")) {
                assertEquals("Home", keys.last().second["claim"])
                assertEquals(ClaimPermission.DOOR.nameKey, keys.last().second["permission"])
            }
        }
    }

    @Test
    fun revokeResultsMapToLegacyKeysAndPlaceholders() {
        val expected = listOf(
            RevokeClaimWidePermissionResult.Success to "command.claim.untrust_all.success",
            RevokeClaimWidePermissionResult.DoesNotExist to "command.claim.untrust_all.does_not_exist",
            RevokeClaimWidePermissionResult.ClaimNotFound to "command.common.unknown_claim",
            RevokeClaimWidePermissionResult.StorageError to "general.error",
        )
        for ((result, key) in expected) {
            keys.clear()
            every { revoke.execute(claimId, ClaimPermission.DOOR) } returns result
            untrust().untrustAll(player, "door")
            assertEquals(key, keys.last().first)
            if (key.startsWith("command.claim.untrust_all")) {
                assertEquals("Home", keys.last().second["claim"])
                assertEquals(ClaimPermission.DOOR.nameKey, keys.last().second["permission"])
            }
        }
    }
}
