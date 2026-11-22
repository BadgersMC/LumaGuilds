package net.lumalyte.lg.di.runtime

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Party
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-NEXUS-CUTOVER-1: the custom ACF completion ids remain available as Brigadier providers. */
class GuildCommandSuggestionsTest {
    private val playerId = UUID.randomUUID()
    private val guild = mockk<Guild>(relaxed = true) { every { id } returns UUID.randomUUID() }

    private fun suggest(provider: String, graph: GuildRuntimeGraph, sender: CommandSender, input: String = ""): List<String> {
        val context = mockk<CommandContext<CommandSourceStack>>()
        val source = mockk<CommandSourceStack>()
        every { context.source } returns source
        every { source.sender } returns sender
        every { source.executor } returns null
        val builder = SuggestionsBuilder(input, 0)
        val providers = guildCommandSuggestions(graph, offlineName = { "name-$it" }, onlinePlayerNames = { listOf("Alice", "Bob") })
        return providers.getValue(provider).getSuggestions(context, builder).get().list.map { it.text }
    }

    private fun player(): Player = mockk { every { uniqueId } returns playerId }

    @Test
    fun `every custom ACF completion id has a Nexus provider`() {
        val builtIns = setOf("players", "nothing")
        val used = File("src/main/kotlin/net/lumalyte/lg/interaction/commands").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> Regex("""@CommandCompletion\("([^"]*)"\)""").findAll(file.readText()).map { it.groupValues[1] } }
            .flatMap { Regex("@([a-z]+)").findAll(it).map { match -> match.groupValues[1] } }
            .filterNot { it in builtIns }
            .toSet()

        val providers = guildCommandSuggestions(mockk(), offlineName = { null }, onlinePlayerNames = { emptyList() }).keys

        assertTrue(providers.containsAll(used), "Missing providers: ${used - providers}")
    }

    @Test
    fun `guild members exclude the sender and filter by typed prefix`() {
        val other = UUID.randomUUID()
        val bobby = UUID.randomUUID()
        val graph = mockk<GuildRuntimeGraph> {
            every { guildService } returns mockk<GuildService> { every { getPlayerGuilds(playerId) } returns setOf(guild) }
            every { memberService } returns mockk<MemberService> {
                every { getGuildMembers(guild.id) } returns setOf(
                    mockk<Member> { every { playerId } returns this@GuildCommandSuggestionsTest.playerId },
                    mockk<Member> { every { playerId } returns other },
                    mockk<Member> { every { playerId } returns bobby },
                )
            }
        }

        val names = guildCommandSuggestions(
            graph,
            offlineName = { mapOf(other to "Alice", bobby to "Bobby")[it] },
            onlinePlayerNames = { emptyList() },
        ).getValue("guildmembers").let { provider ->
            val context = mockk<CommandContext<CommandSourceStack>>()
            val source = mockk<CommandSourceStack>()
            every { context.source } returns source
            every { source.sender } returns player()
            every { source.executor } returns null
            provider.getSuggestions(context, SuggestionsBuilder("b", 0)).get().list.map { it.text }
        }

        assertEquals(listOf("Bobby"), names)
    }

    @Test
    fun `console receives no player-scoped suggestions`() {
        val console = mockk<CommandSender>()

        assertEquals(emptyList(), suggest("guildmembers", mockk(), console))
        assertEquals(listOf("GLOBAL"), suggest("parties", mockk(), console))
    }

    @Test
    fun `parties list GLOBAL first then unbanned active guild parties`() {
        val open = mockk<Party> { every { name } returns "raid"; every { isPlayerBanned(playerId) } returns false }
        val banned = mockk<Party> { every { name } returns "secret"; every { isPlayerBanned(playerId) } returns true }
        val graph = mockk<GuildRuntimeGraph> {
            every { guildService } returns mockk<GuildService> { every { getPlayerGuilds(playerId) } returns setOf(guild) }
            every { partyService } returns mockk<PartyService> { every { getActivePartiesForGuild(guild.id) } returns setOf(open, banned) }
        }

        assertEquals(listOf("GLOBAL", "raid"), suggest("parties", graph, player()))
    }

    @Test
    fun `all players come from the online player snapshot`() {
        assertEquals(listOf("Bob"), suggest("allplayers", mockk(), mockk<CommandSender>(), input = "b"))
    }
}
