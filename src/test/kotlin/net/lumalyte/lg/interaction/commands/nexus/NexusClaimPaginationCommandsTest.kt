package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimPaginationCommandsTest {
    private val playerId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) {
        every { uniqueId } returns playerId
    }
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val listClaims = mockk<ListPlayerClaims>()
    private val getBlockCount = mockk<GetClaimBlockCount>()
    private val getAtPosition = mockk<GetPartitionByPosition>()
    private val getPartitions = mockk<GetClaimPartitions>()
    private val getDetails = mockk<GetClaimDetails>()

    @Test
    fun `new handlers preserve ACF root and subcommand permissions and optional page arguments`() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusClaimListCommand::class.java.packageName,
            NexusClaimListCommand::class.java.classLoader,
        )
        data class Route(
            val type: kotlin.reflect.KClass<*>,
            val root: String,
            val subcommand: String,
            val permission: String,
        )
        for ((type, root, subcommand, permission) in listOf(
            Route(NexusClaimListCommand::class, "claimlist", "", "lumaguilds.command.claimlist"),
            Route(NexusClaimPartitionsCommand::class, "claim", "partitions", "lumaguilds.command.claim.partitions"),
        )) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type })
            assertEquals(root, definition.annotation.name)
            val path = if (subcommand.isEmpty()) emptyList() else listOf(subcommand)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == path })
            assertEquals(permission, handler.permission)
            assertTrue(handler.isPlayerOnly)
            val page = handler.parameters.single { it.isArg }
            assertEquals("page", page.name)
            assertTrue(page.isOptional)
        }
    }

    @Test
    fun `claimlist shows no-claims message instead of invalid page`() {
        every { listClaims.execute(playerId) } returns emptyList()

        NexusClaimListCommand(lang, listClaims, getBlockCount).list(player)

        verify(exactly = 1) { player.sendMessage(Component.text("command.claim_list.no_claims")) }
        verify(exactly = 0) { getBlockCount.execute(any()) }
    }

    @Test
    fun `claimlist validates page bounds and counts only requested claims`() {
        val claims = (1..12).map { index ->
            Claim(worldId, playerId, position = Position3D(index, 70, -index), name = "Claim-$index")
        }
        every { listClaims.execute(playerId) } returns claims
        every { getBlockCount.execute(any()) } returns 42
        val command = NexusClaimListCommand(lang, listClaims, getBlockCount)

        command.list(player, 0)
        command.list(player, Int.MAX_VALUE)
        verify(exactly = 2) { player.sendMessage(Component.text("command.common.invalid_page")) }
        verify(exactly = 0) { getBlockCount.execute(any()) }

        command.list(player, 2)
        verify(exactly = 1) { getBlockCount.execute(claims[10].id) }
        verify(exactly = 1) { getBlockCount.execute(claims[11].id) }
        verify(exactly = 2) { lang.msg("command.claim_list.row", *anyVararg()) }
        verify(exactly = 1) {
            lang.msg("command.info_box.paged", "current_page" to 2, "total_pages" to 2)
        }
    }

    @Test
    fun `partitions checks current world and handles unknown partitions before fetching lists`() {
        val world = mockk<World> { every { uid } returns worldId }
        every { player.location } returns Location(world, -1.5, 70.0, -1.5)
        every { player.world } returns world
        every { getAtPosition.execute(any(), worldId) } returns null

        NexusClaimPartitionsCommand(lang, getAtPosition, getPartitions, getDetails).partitions(player)

        verify(exactly = 1) {
            getAtPosition.execute(match { it.x == -2 && it.y == 70 && it.z == -2 }, worldId)
        }
        verify(exactly = 1) { player.sendMessage(Component.text("command.common.unknown_partition")) }
        verify(exactly = 0) { getPartitions.execute(any()) }
    }

    @Test
    fun `partitions uses shared pagination and formats only the chosen page`() {
        val claimId = UUID.randomUUID()
        val world = mockk<World> { every { uid } returns worldId }
        every { player.location } returns Location(world, 0.0, 70.0, 0.0)
        every { player.world } returns world
        every { getAtPosition.execute(any(), worldId) } returns Partition(
            claimId, Area(Position2D(0, 0), Position2D(15, 15)),
        )
        every { getPartitions.execute(claimId) } returns (1..11).map {
            Partition(claimId, Area(Position2D(it, it), Position2D(it + 15, it + 15)))
        }
        every { getDetails.execute(claimId) } returns mockk<Claim> {
            every { name } returns "Claim"
        }

        val command = NexusClaimPartitionsCommand(lang, getAtPosition, getPartitions, getDetails)
        command.partitions(player, -1)
        verify(exactly = 1) { player.sendMessage(Component.text("command.common.invalid_page")) }
        verify(exactly = 0) { getDetails.execute(any()) }

        command.partitions(player, 2)
        verify(exactly = 1) { lang.msg("command.partitions.row", *anyVararg()) }
        verify(exactly = 1) { lang.msg("command.partitions.header", "claim" to "Claim") }
        verify(exactly = 1) {
            lang.msg("command.info_box.paged", "current_page" to 2, "total_pages" to 2)
        }
    }
}
