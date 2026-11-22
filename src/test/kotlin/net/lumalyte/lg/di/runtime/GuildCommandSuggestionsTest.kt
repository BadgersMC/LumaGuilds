package net.lumalyte.lg.di.runtime

import io.mockk.mockk
import net.lumalyte.lg.interaction.commands.nexus.scanGuildCommandDefinitions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class GuildCommandSuggestionsTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `runtime publishes exactly the active command suggestion keys`(claimsEnabled: Boolean) {
        val catalog = NexusCommandCatalog.classes(claimsEnabled)
        val required = scanGuildCommandDefinitions().filter { it.commandClass in catalog }
            .flatMap { definition -> definition.subcommands.flatMap { it.suggestions.values } }.toSet()
        val providers = runtimeCommandSuggestions(mockk(), mockk(), claimsEnabled, Runnable::run)
        assertEquals(required, providers.keys)
    }
}
