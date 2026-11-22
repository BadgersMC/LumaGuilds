package net.badgersmc.nexus.paper.commands

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test fixture command class in this package so scanner finds it.
// Methods exist only to carry annotations the scanner reads via reflection;
// their bodies and parameters are intentionally unused.
@Suppress("EmptyFunctionBlock", "UnusedParameter")
@Command(name = "test", description = "Test command")
class TestCommand {
    @Subcommand("hello")
    @Permission("test.hello")
    @PlayerOnly
    fun hello(@Context sender: String) {}

    @Subcommand("admin kick")
    @Permission("test.admin")
    @Async
    suspend fun adminKick(
        @Context sender: String,
        @Arg("target") target: String
    ) {}
}

class PaperCommandScannerTest {
    private val scanner = PaperCommandScanner()

    @Test
    fun `scans subcommand methods correctly`() {
        val cl = TestCommand::class.java.classLoader
        val defs = scanner.scanCommands(TestCommand::class.java.packageName, cl)
        // Should find TestCommand (may also find PaperCommandScannerTest if it has @Command — it doesn't)
        val def = defs.find { it.annotation.name == "test" }
        assertTrue(def != null, "Should discover TestCommand")
        assertEquals("test", def.annotation.name)
        assertEquals(2, def.subcommands.size)
    }

    @Test
    fun `parses nested subcommand path`() {
        val cl = TestCommand::class.java.classLoader
        val defs = scanner.scanCommands(TestCommand::class.java.packageName, cl)
        val def = defs.find { it.annotation.name == "test" }!!
        val adminKick = def.subcommands.find { it.path == listOf("admin", "kick") }
        assertTrue(adminKick != null, "Should find admin kick subcommand")
        assertTrue(adminKick.isAsync)
    }

    @Test
    fun `detects PlayerOnly annotation`() {
        val cl = TestCommand::class.java.classLoader
        val defs = scanner.scanCommands(TestCommand::class.java.packageName, cl)
        val def = defs.find { it.annotation.name == "test" }!!
        val hello = def.subcommands.find { it.path == listOf("hello") }
        assertTrue(hello != null)
        assertTrue(hello.isPlayerOnly)
    }
}
