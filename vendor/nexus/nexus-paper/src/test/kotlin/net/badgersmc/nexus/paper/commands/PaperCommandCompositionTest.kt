package net.badgersmc.nexus.paper.commands

import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import fixtures.invalid.InvalidGreedyFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("UnusedParameter", "EmptyFunctionBlock")
@Command(name = "guild", aliases = ["g"])
class GuildRootFixture {
    @Subcommand("")
    fun menu() {}

    @Subcommand("help")
    fun help() {}
}

@Suppress("UnusedParameter", "EmptyFunctionBlock")
@Command(name = "guild", aliases = ["g"])
class GuildHomeFixture {
    @Subcommand("home")
    @Permission("guild.home")
    fun home(@Arg("name", required = false) name: String = "main") {}

    @Subcommand("home activate")
    fun activate(@Arg("name") name: String) {}

    @Subcommand("lookup")
    fun lookup(@Arg("guildName") suppliedName: String) {}

    @Subcommand("announce")
    fun announce(@Arg("message") @Greedy message: String) {}
}

class PaperCommandCompositionTest {
    private val scanner = PaperCommandScanner()
    private val loader = GuildRootFixture::class.java.classLoader

    @Test
    fun `shared root classes compose to one guild command with aliases`() {
        val definitions = scanner.scanCommands(GuildRootFixture::class.java.packageName, loader)
            .filter { it.commandClass == GuildRootFixture::class || it.commandClass == GuildHomeFixture::class }
        val plan = PaperCommandPlanner.plan(definitions)
        assertEquals(1, plan.size)
        assertEquals("guild", plan.single().name)
        assertEquals(listOf("g"), plan.single().aliases)
        assertEquals(2, plan.single().definitions.size)
        assertTrue(plan.single().definitions.any { def -> def.subcommands.any { it.path.isEmpty() } })
        assertTrue(plan.single().definitions.any { def ->
            def.subcommands.any { it.path == listOf("home", "activate") }
        })
    }

    @Test
    fun `scanner records optional and greedy arguments`() {
        val scannerMethod = GuildHomeFixture::class
        val definition = scanner.scanCommands(GuildRootFixture::class.java.packageName, loader)
            .first { it.commandClass == scannerMethod }
        val home = assertNotNull(definition.subcommands.find { it.path == listOf("home") })
        assertTrue(home.parameters.single().isOptional)
        val announce = assertNotNull(definition.subcommands.find { it.path == listOf("announce") })
        assertEquals(setOf("message"), announce.greedyParameters)
        assertEquals("guildName", definition.subcommands.single { it.path == listOf("lookup") }.parameters.single().name)
    }

    @Test
    fun `duplicate routes are rejected before registration`() {
        val definitions = scanner.scanCommands(GuildRootFixture::class.java.packageName, loader)
            .filter { it.commandClass == GuildRootFixture::class || it.commandClass == GuildHomeFixture::class }
        val duplicate = definitions.last().copy(subcommands = listOf(definitions.first().subcommands.first()))
        assertFailsWith<CommandException> {
            PaperCommandPlanner.plan(listOf(definitions.first(), duplicate))
        }
    }

    @Test
    fun `greedy argument must be last`() {
        assertFailsWith<CommandException> {
            scanner.processClass(InvalidGreedyFixture::class)
        }
    }
}
