package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimTrustListCommandTest {
    private val playerId = UUID.randomUUID()
    private val claimId = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns playerId }
    private val sent = mutableListOf<Component>()
    private val lang = mockk<LangService> {
        every { msg(any<String>(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val access = mockk<NexusClaimAccess>()
    private val trusted = mockk<GetPlayersWithPermissionInClaim>()
    private val details = mockk<GetClaimDetails>()
    private val perms = mockk<GetClaimPlayerPermissions>()
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val claim = Claim(UUID.randomUUID(), playerId, position = Position3D(1, 64, 1), name = "Home")

    init {
        mockkStatic(Bukkit::class)
        every { player.sendMessage(any<Component>()) } answers { sent += firstArg<Component>() }
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { details.execute(claimId) } returns claim
        every { perms.execute(claimId, any()) } returns listOf(ClaimPermission.BUILD)
    }

    @AfterEach
    fun cleanup() = unmockkStatic(Bukkit::class)

    private fun command() = NexusClaimTrustListCommand(lang, access, trusted, details, perms)

    /** Returns ids named p01..pNN (index i -> p(i+1)); the repository yields them reversed. */
    private fun trustedPlayers(n: Int): List<UUID> {
        val ids = (1..n).map { UUID.randomUUID() }
        ids.forEachIndexed { i, id ->
            every { Bukkit.getOfflinePlayer(id) } returns mockk<OfflinePlayer> {
                every { name } returns "p%02d".format(i + 1)
            }
        }
        every { trusted.execute(claimId) } returns ids.reversed()
        return ids
    }

    private fun invalidPages() = sent.count { it == Component.text("command.common.invalid_page") }

    @Test
    fun scannerPreservesPermissionPlayerOnlyAndOptionalPage() {
        val definitions = PaperCommandScanner().scanCommands(
            NexusClaimTrustListCommand::class.java.packageName,
            NexusClaimTrustListCommand::class.java.classLoader,
        )
        val definition = assertNotNull(definitions.singleOrNull { it.commandClass == NexusClaimTrustListCommand::class })
        assertEquals("claim", definition.annotation.name)
        val handler = assertNotNull(definition.subcommands.singleOrNull { it.path == listOf("trustlist") })
        assertEquals("lumaguilds.command.claim.trustlist", handler.permission)
        assertTrue(handler.isPlayerOnly)
        val page = handler.parameters.single { it.isArg }
        assertEquals("page", page.name)
        assertTrue(page.isOptional)
    }

    @Test
    fun `unknown partition or missing permission stops before any lookup`() {
        every { access.partitionAtPlayer(player) } returns null
        command().trustList(player)
        every { access.partitionAtPlayer(player) } returns partition
        every { access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS) } returns false
        command().trustList(player)

        verify(exactly = 0) { trusted.execute(any()) }
        verify(exactly = 0) { details.execute(any()) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `empty trusted list reports no players even for invalid page`() {
        every { trusted.execute(claimId) } returns emptyList()

        command().trustList(player, 99)
        command().trustList(player, -3)

        assertEquals(listOf<Component>(Component.text("command.claim.trust_list.no_players"), Component.text("command.claim.trust_list.no_players")), sent.toList())
        verify(exactly = 0) { details.execute(any()) }
        verify(exactly = 0) { perms.execute(any(), any()) }
    }

    @Test
    fun `invalid pages reply invalid_page with no name details or permission lookups`() {
        trustedPlayers(11)
        // legacy 10-per-page validity: page 3 needs >= 21 players
        val bad = listOf(0, -1, 3, Int.MIN_VALUE, Int.MAX_VALUE, 214748365)
        bad.forEach { command().trustList(player, it) }

        assertEquals(bad.size, invalidPages())
        verify(exactly = 0) { Bukkit.getOfflinePlayer(any<UUID>()) }
        verify(exactly = 0) { details.execute(any()) }
        verify(exactly = 0) { perms.execute(any(), any()) }
    }

    @Test
    fun `null page defaults to page one and shows only four of five legacy rows`() {
        val ids = trustedPlayers(7)

        command().trustList(player)

        verify(exactly = 4) { lang.msg("command.claim.trust_list.row", *anyVararg()) }
        for (i in 0..3) verify(exactly = 1) { perms.execute(claimId, ids[i]) }
        for (i in 4..6) verify(exactly = 0) { perms.execute(claimId, ids[i]) }
        verify(exactly = 1) { lang.msg("command.info_box.paged", "current_page" to 1, "total_pages" to 2) }
        verify(exactly = 1) { lang.msg("command.claim.trust_list.header", "claim" to "Home") }
    }

    @Test
    fun `rows are sorted by name and carry joined permissions`() {
        val ids = trustedPlayers(3)
        every { perms.execute(claimId, ids[0]) } returns listOf(ClaimPermission.BUILD, ClaimPermission.SIGN)

        command().trustList(player, 1)

        verify(exactly = 1) {
            lang.msg(
                "command.claim.trust_list.row",
                "player" to "p01",
                "permissions" to "BUILDgeneral.list_separatorSIGN",
            )
        }
        verify(exactly = 3) { lang.msg("command.claim.trust_list.row", *anyVararg()) }
    }

    @Test
    fun `page two with eleven players shows sorted indexes five to eight and never index four`() {
        val ids = trustedPlayers(11)

        command().trustList(player, 2)

        for (i in 5..8) verify(exactly = 1) { perms.execute(claimId, ids[i]) }
        for (i in listOf(0, 1, 2, 3, 4, 9, 10)) verify(exactly = 0) { perms.execute(claimId, ids[i]) }
        verify(exactly = 1) { lang.msg("command.info_box.paged", "current_page" to 2, "total_pages" to 3) }
    }

    @Test
    fun `single player and boundary counts use legacy edges`() {
        val ids = trustedPlayers(1)
        command().trustList(player, 1)
        verify(exactly = 1) { perms.execute(claimId, ids[0]) }
        verify(exactly = 1) { lang.msg("command.info_box.paged", "current_page" to 1, "total_pages" to 1) }

        // page 2 requires >= 11 players: 10 players -> invalid
        trustedPlayers(10)
        command().trustList(player, 2)
        assertEquals(1, invalidPages())
    }
}
