package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.flags.EnableClaimFlagResult
import net.lumalyte.lg.application.results.claim.permission.GrantPlayerClaimPermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.values.Position2D
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** REQ-NEXUS-CUTOVER-1: exercise the pinned parser, live command permissions and legacy name matching. */
class NexusClaimArgumentsParityTest {
    private val claimId = UUID.randomUUID()
    private val server = mockk<Server>()
    private val actor = mockk<Player>(relaxed = true) {
        every { server } returns this@NexusClaimArgumentsParityTest.server
        every { hasPermission(any<String>()) } returns true
        every { canSee(any<Player>()) } returns true
    }
    private val source = mockk<CommandSourceStack> { every { sender } returns actor }
    private val lang = mockk<LangService> {
        every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
    private val access = mockk<NexusClaimAccess> {
        every { partitionAtPlayer(actor) } returns partition
        every { hasPermission(actor, partition, RankPermission.MANAGE_PERMISSIONS) } returns true
        every { hasPermission(actor, partition, RankPermission.MANAGE_FLAGS) } returns true
    }
    private val details = mockk<GetClaimDetails> { every { execute(claimId) } returns null }
    private val grant = mockk<GrantPlayerClaimPermission> {
        every { execute(any(), any(), any()) } returns GrantPlayerClaimPermissionResult.Success
    }

    private fun target(name: String, online: Boolean = true): Player = mockk(relaxed = true) {
        every { this@mockk.name } returns name
        every { uniqueId } returns UUID.randomUUID()
        every { isOnline } returns online
    }

    private fun trust(input: String): Int {
        registerClaimCommandArguments()
        return claimDispatcher(NexusTrustCommand(lang, access, grant, details), claimCommandSuggestionProviders())
            .execute("claim trust $input", source)
    }

    @Test
    fun `claim token declares a native client argument type rather than an unserializable parser`() {
        registerClaimCommandArguments()
        val resolver = checkNotNull(PaperArgumentResolvers.get(ClaimArgumentToken::class))
        val argument = assertIs<CustomArgumentType<*, *>>(resolver.argumentType())
        assertIs<StringArgumentType>(argument.nativeType)
    }

    @Test
    fun `unique partial name and punctuation-normalized permission resolve before mutation`() {
        val alex = target("Alex")
        val targetId = alex.uniqueId
        every { server.matchPlayer("Al") } returns listOf(alex)

        assertEquals(1, trust("Al b-u_i.l-d"))

        verify(exactly = 1) { grant.execute(claimId, targetId, ClaimPermission.BUILD) }
    }

    @Test
    fun `untrust uses the same explicit name resolver and permission token`() {
        registerClaimCommandArguments()
        val alex = target("Alex")
        val targetId = alex.uniqueId
        every { server.matchPlayer("Al") } returns listOf(alex)
        val revoke = mockk<RevokePlayerClaimPermission> {
            every { execute(claimId, targetId, ClaimPermission.BUILD) } returns RevokePlayerClaimPermissionResult.Success
        }
        val dispatcher = claimDispatcher(NexusUntrustCommand(lang, access, revoke, details), claimCommandSuggestionProviders())

        assertEquals(1, dispatcher.execute("claim untrust Al b-u-i-l-d", source))

        verify(exactly = 1) { revoke.execute(claimId, targetId, ClaimPermission.BUILD) }
    }

    @Test
    fun `ambiguous visible partial name never chooses the first target`() {
        every { server.matchPlayer("Al") } returns listOf(target("Alex"), target("Alan"))

        trust("Al BUILD")

        verify(exactly = 0) { access.partitionAtPlayer(any()) }
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
    }

    @Test
    fun `selector syntax is treated as a literal name and never expands to a player`() {
        every { server.matchPlayer("@p") } returns emptyList()
        trust("@p BUILD")

        verify(exactly = 0) { grant.execute(any(), any(), any()) }
    }

    @Test
    fun `literal Floodgate name beginning with at reaches trust`() {
        val target = target("@Bedrock")
        val targetId = target.uniqueId
        every { server.matchPlayer("@Bedrock") } returns listOf(target)

        assertEquals(1, trust("@Bedrock BUILD"))

        verify(exactly = 1) { grant.execute(claimId, targetId, ClaimPermission.BUILD) }
    }

    @Test
    fun `hidden player requires visibility privilege and explicit confirmation`() {
        val hidden = target("Hidden")
        val targetId = hidden.uniqueId
        every { server.matchPlayer("Hidden") } returns listOf(hidden)
        every { actor.canSee(hidden) } returns false
        every { actor.hasPermission("acf.seevanish") } returns false
        trust("Hidden:confirm BUILD")
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
        verify(exactly = 0) { lang.msg("command.claim.hidden_player_confirm", *anyVararg()) }

        every { actor.hasPermission("acf.seevanish") } returns true
        trust("Hidden BUILD")
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
        verify(exactly = 1) { lang.msg("command.claim.hidden_player_confirm", "player" to "Hidden") }

        assertEquals(1, trust("Hidden:confirm BUILD"))
        verify(exactly = 1) { grant.execute(claimId, targetId, ClaimPermission.BUILD) }
    }

    @Test
    fun `offline or absent target never accesses claim state`() {
        every { server.matchPlayer("Offline") } returns listOf(target("Offline", online = false))
        every { server.matchPlayer("Missing") } returns emptyList()

        trust("Offline BUILD")
        trust("Missing BUILD")

        verify(exactly = 0) { access.partitionAtPlayer(any()) }
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
    }

    @Test
    fun `command permission denial rejects before name lookup`() {
        every { actor.hasPermission("lumaguilds.command.claim.trust") } returns false

        assertFailsWith<CommandSyntaxException> { trust("Alex BUILD") }

        verify(exactly = 0) { server.matchPlayer(any()) }
        verify(exactly = 0) { grant.execute(any(), any(), any()) }
    }

    @Test
    fun `console cannot execute a player-only trust route`() {
        registerClaimCommandArguments()
        val console = mockk<ConsoleCommandSender> { every { hasPermission(any<String>()) } returns true }
        val consoleSource = mockk<CommandSourceStack> { every { sender } returns console }
        val dispatcher = claimDispatcher(NexusTrustCommand(lang, access, grant, details), claimCommandSuggestionProviders())

        assertFailsWith<CommandSyntaxException> { dispatcher.execute("claim trust Alex BUILD", consoleSource) }

        verify(exactly = 0) { grant.execute(any(), any(), any()) }
    }

    @Test
    fun `raw enum token preserves legacy simplified flag spellings through dispatcher`() {
        registerClaimCommandArguments()
        val enable = mockk<EnableClaimFlag> {
            every { execute(Flag.FALLING_BLOCK, claimId) } returns EnableClaimFlagResult.Success
            every { execute(Flag.FIRE, claimId) } returns EnableClaimFlagResult.Success
        }
        val dispatcher = claimDispatcher(NexusClaimAddFlagCommand(lang, access, enable, details), claimCommandSuggestionProviders())

        assertEquals(1, dispatcher.execute("claim addflag falling-block", source))
        assertEquals(1, dispatcher.execute("claim addflag F!IRE", source))

        verify(exactly = 1) { enable.execute(Flag.FALLING_BLOCK, claimId) }
        verify(exactly = 1) { enable.execute(Flag.FIRE, claimId) }
    }

    @Test
    fun `flag suggestion annotation is wired into the actual command tree`() {
        registerClaimCommandArguments()
        val dispatcher = claimDispatcher(NexusClaimAddFlagCommand(lang, access, mockk(), details), claimCommandSuggestionProviders())

        val suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("claim addflag f", source)).get()

        assertEquals(Flag.entries.map { it.name }.filter { it.startsWith("F") }.sorted(), suggestions.list.map { it.text }.sorted())
        verify(exactly = 0) { access.partitionAtPlayer(any()) }
    }

    @Test
    fun `each operation filters enum completions by its own permission and case insensitive prefix`() {
        val providers = claimCommandSuggestionProviders()
        val entries = listOf(
            Triple("claim.flags.add", "addflag", Flag.entries.map { it.name }),
            Triple("claim.flags.remove", "removeflag", Flag.entries.map { it.name }),
            Triple("claim.permissions.trust", "trust", ClaimPermission.entries.map { it.name }),
            Triple("claim.permissions.untrust", "untrust", ClaimPermission.entries.map { it.name }),
            Triple("claim.permissions.trustall", "trustall", ClaimPermission.entries.map { it.name }),
            Triple("claim.permissions.untrustall", "untrustall", ClaimPermission.entries.map { it.name }),
        )
        val context = mockk<CommandContext<CommandSourceStack>> { every { source } returns this@NexusClaimArgumentsParityTest.source }
        for ((key, operation, expected) in entries) {
            val provider = providers.getValue(key)
            every { actor.hasPermission("lumaguilds.command.claim.$operation") } returns true
            assertEquals(expected.sorted(), provider.getSuggestions(context, SuggestionsBuilder("", 0)).get().list.map { it.text }.sorted())
            val prefix = expected.first().take(2).lowercase()
            assertEquals(expected.filter { it.startsWith(prefix, ignoreCase = true) }.sorted(),
                provider.getSuggestions(context, SuggestionsBuilder(prefix, 0)).get().list.map { it.text }.sorted())
            every { actor.hasPermission("lumaguilds.command.claim.$operation") } returns false
            assertTrue(provider.getSuggestions(context, SuggestionsBuilder("", 0)).get().isEmpty)
        }
    }

    @Test
    fun `player completions expose only visible names to an authorized player`() {
        val visible = target("Alex")
        val hidden = target("Alan")
        every { actor.canSee(hidden) } returns false
        every { server.onlinePlayers } returns listOf(visible, hidden)
        val context = mockk<CommandContext<CommandSourceStack>> { every { source } returns this@NexusClaimArgumentsParityTest.source }
        val provider = claimCommandSuggestionProviders().getValue("claim.players.trust")

        assertEquals(listOf("Alex"), provider.getSuggestions(context, SuggestionsBuilder("a", 0)).get().list.map { it.text })
        every { actor.hasPermission("lumaguilds.command.claim.trust") } returns false
        assertTrue(provider.getSuggestions(context, SuggestionsBuilder("", 0)).get().isEmpty)
    }
}
