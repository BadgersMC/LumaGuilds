package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.permission.GrantPlayerClaimPermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
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
import kotlin.test.assertTrue

class NexusClaimPlayerPermissionCommandsTest {
    private val playerId = UUID.randomUUID()
    private val targetId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val target = mockk<Player>(relaxed = true) {
        every { uniqueId } returns targetId
        every { name } returns "Alex"
        every { isOnline } returns true
    }
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
    private val grant = mockk<GrantPlayerClaimPermission>()
    private val revoke = mockk<RevokePlayerClaimPermission>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(UUID.randomUUID(), playerId, position = Position3D(1, 64, 1), name = "Home")

    init {
        every { player.sendMessage(any<Component>()) } answers { sent += firstArg<Component>() }
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { details.execute(claimId) } returns claim
    }

    private fun trust() = NexusTrustCommand(lang, access, grant, details)
    private fun untrust() = NexusUntrustCommand(lang, access, revoke, details)

    @Test
    fun scannerPreservesPermissionsPlayerOnlyAndArguments() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusTrustCommand::class.java.packageName,
            NexusTrustCommand::class.java.classLoader,
        )
        for ((type, sub, perm) in listOf(
            Triple(NexusTrustCommand::class, "trust", "lumaguilds.command.claim.trust"),
            Triple(NexusUntrustCommand::class, "untrust", "lumaguilds.command.claim.untrust"),
        )) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type })
            assertEquals("claim", definition.annotation.name)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == listOf(sub) })
            assertEquals(perm, handler.permission)
            assertTrue(handler.isPlayerOnly)
            val args = handler.parameters.filter { it.isArg }
            assertEquals(listOf("player", "permission"), args.map { it.name })
            assertTrue(args.all { !it.isOptional })
        }
    }

    @Test
    fun invalidPermissionOrOfflineTargetNeverTouchesClaimStateOrAuthorization() {
        trust().trust(player, target, "nope")
        untrust().untrust(player, target, "nope")
        every { target.isOnline } returns false
        trust().trust(player, target, "build")
        untrust().untrust(player, target, "build")

        verify(exactly = 0) { access.partitionAtPlayer(any()) }
        verify(exactly = 0) { access.hasPermission(any(), any(), any()) }
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
        verify(exactly = 0) { revoke.execute(any(), any(), any()) }
        assertEquals(List(4) { "general.error" }, keys.map { it.first })
        assertEquals(4, sent.size)
    }

    @Test
    fun unknownPartitionOrDeniedPermissionStopsBeforeMutation() {
        every { access.partitionAtPlayer(player) } returns null
        trust().trust(player, target, "build")
        untrust().untrust(player, target, "build")

        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns false
        trust().trust(player, target, "build")
        untrust().untrust(player, target, "build")

        verify(exactly = 0) { grant.execute(any(), any(), any()) }
        verify(exactly = 0) { revoke.execute(any(), any(), any()) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun authorizationIsCheckedImmediatelyBeforeMutation() {
        every { grant.execute(claimId, targetId, ClaimPermission.BUILD) } returns GrantPlayerClaimPermissionResult.Success
        every { revoke.execute(claimId, targetId, ClaimPermission.BUILD) } returns RevokePlayerClaimPermissionResult.Success
        trust().trust(player, target, "Build")
        untrust().untrust(player, target, "BUILD")

        verifyOrder {
            access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)
            grant.execute(claimId, targetId, ClaimPermission.BUILD)
            access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)
            revoke.execute(claimId, targetId, ClaimPermission.BUILD)
        }
    }

    @Test
    fun grantResultsMapToLegacyKeysAndPlaceholders() {
        val expected = listOf(
            GrantPlayerClaimPermissionResult.Success to "command.claim.trust.success",
            GrantPlayerClaimPermissionResult.AlreadyExists to "command.claim.trust.already_exists",
            GrantPlayerClaimPermissionResult.ClaimNotFound to "command.common.unknown_claim",
            GrantPlayerClaimPermissionResult.StorageError to "general.error",
        )
        for ((result, key) in expected) {
            keys.clear()
            every { grant.execute(claimId, targetId, ClaimPermission.DOOR) } returns result
            trust().trust(player, target, "door")
            assertEquals(key, keys.last().first)
            if (key.startsWith("command.claim.trust.")) {
                assertEquals("Home", keys.last().second["claim"])
                assertEquals("Alex", keys.last().second["player"])
                assertEquals(ClaimPermission.DOOR.nameKey, keys.last().second["permission"])
                assertEquals(setOf("claim", "player", "permission"), keys.last().second.keys)
            }
        }
    }

    @Test
    fun revokeResultsMapToLegacyKeysAndPlaceholders() {
        val expected = listOf(
            RevokePlayerClaimPermissionResult.Success to "command.claim.untrust.success",
            RevokePlayerClaimPermissionResult.DoesNotExist to "command.claim.untrust.does_not_exist",
            RevokePlayerClaimPermissionResult.ClaimNotFound to "command.common.unknown_claim",
            RevokePlayerClaimPermissionResult.StorageError to "general.error",
        )
        for ((result, key) in expected) {
            keys.clear()
            every { revoke.execute(claimId, targetId, ClaimPermission.DOOR) } returns result
            untrust().untrust(player, target, "door")
            assertEquals(key, keys.last().first)
            if (key.startsWith("command.claim.untrust.")) {
                assertEquals("Home", keys.last().second["claim"])
                assertEquals("Alex", keys.last().second["player"])
                assertEquals(ClaimPermission.DOOR.nameKey, keys.last().second["permission"])
                assertEquals(setOf("claim", "player", "permission"), keys.last().second.keys)
            }
        }
    }
}
