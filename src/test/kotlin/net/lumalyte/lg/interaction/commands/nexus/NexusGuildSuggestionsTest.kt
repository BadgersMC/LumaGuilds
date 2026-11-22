package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.interaction.commands.GuildCommandCompletions
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-NEXUS-CUTOVER-1: Nexus `/guild` suggestions reuse the shared ACF completion sources. */
class NexusGuildSuggestionsTest {
    private val completions = mockk<GuildCommandCompletions>()
    private val providers = nexusGuildSuggestionProviders(completions)

    private fun suggest(key: String, sender: CommandSender, typed: String = ""): Set<String> {
        val source = mockk<CommandSourceStack>().also { every { it.sender } returns sender }
        val context = mockk<CommandContext<CommandSourceStack>>().also { every { it.source } returns source }
        return providers.getValue(key).getSuggestions(context, SuggestionsBuilder(typed, 0)).get().list.map { it.text }.toSet()
    }

    @Test
    fun `provider map covers exactly the keys referenced by guild routes`() {
        val referenced = guildRootDefinitions().flatMap { d -> d.subcommands.flatMap { it.suggestions.values } }.toSet()
        assertEquals(referenced, providers.keys)
        assertEquals(NexusGuildCommandParityTest.suggestionKeys.toSet(), providers.keys)
    }

    @Test
    fun `player scoped sources use the executing player and filter by typed prefix ignoring case`() {
        val player = mockk<Player>()
        every { completions.guildMembers(player) } returns listOf("Alex", "alfred", "Steve")
        assertEquals(setOf("Alex", "alfred"), suggest(NexusGuildSuggestionKeys.GUILD_MEMBERS, player, "AL"))
        verify(exactly = 1) { completions.guildMembers(player) }
    }

    @Test
    fun `player scoped sources are empty for console like ACF null player`() {
        val console = mockk<CommandSender>()
        listOf(
            NexusGuildSuggestionKeys.GUILD_MEMBERS, NexusGuildSuggestionKeys.PENDING_INVITES,
            NexusGuildSuggestionKeys.ALLY_GUILDS, NexusGuildSuggestionKeys.GUILD_HOMES,
            NexusGuildSuggestionKeys.GUILD_ACCESSIBLE_HOMES, NexusGuildSuggestionKeys.UNLOCKED_EMOJIS,
        ).forEach { assertEquals(emptySet(), suggest(it, console), it) }
    }

    @Test
    fun `global and static sources match ACF values`() {
        val player = mockk<Player>()
        every { completions.guilds() } returns listOf("Iron Order", "Knights")
        every { completions.allPlayers() } returns listOf("Steve")
        every { completions.guildsOrPlayers() } returns listOf("Iron Order", "Steve")
        every { completions.visiblePlayers(player) } returns listOf("Alex")
        assertEquals(setOf("Iron Order"), suggest(NexusGuildSuggestionKeys.GUILDS, player, "i"))
        assertEquals(setOf("Steve"), suggest(NexusGuildSuggestionKeys.ALL_PLAYERS, player))
        assertEquals(setOf("Iron Order", "Steve"), suggest(NexusGuildSuggestionKeys.GUILDS_OR_PLAYERS, player))
        assertEquals(setOf("Alex"), suggest(NexusGuildSuggestionKeys.PLAYERS, player))
        assertEquals(setOf("off"), suggest(NexusGuildSuggestionKeys.ON_OFF, player, "of"))
        assertEquals(setOf("on", "off", "toggle"), suggest(NexusGuildSuggestionKeys.ON_OFF_TOGGLE, player))
        assertEquals(setOf("peaceful", "hostile"), suggest(NexusGuildSuggestionKeys.MODES, player))
    }
}
