package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-2: production startup activates exactly one Nexus context and never starts
 * Koin or ACF alongside it. Legacy module sources remain compiled for existing tests only.
 */
class NexusRuntimeOwnershipTest {
    private val root = File("src/main/kotlin/net/lumalyte/lg")
    private val legacyGraphFiles = setOf("di/Modules.kt", "di/LegacyMenuServices.kt", "di/LegacyInteractionDependencies.kt")

    private fun productionFiles() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun offenders(pattern: Regex, files: List<File>) = files.flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
            if (pattern.containsMatchIn(line)) "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
        }
    }

    @Test
    fun `bootstrap and runtime reference neither Koin nor ACF`() {
        val bootstrap = listOf(File(root, "LumaGuilds.kt")) + File(root, "di/runtime").listFiles()!!.filter { it.extension == "kt" }
        assertTrue(bootstrap.size > 1)

        assertEquals(emptyList(), offenders(Regex("""org\.koin|co\.aikar\.commands|startKoin|PaperCommandManager"""), bootstrap))
    }

    @Test
    fun `no production code starts Koin or an ACF command manager`() {
        val starters = Regex("""\bstartKoin\b|\bkoinApplication\b|PaperCommandManager\(|\.registerCommand\(""")

        assertEquals(emptyList(), offenders(starters, productionFiles()))
    }

    @Test
    fun `the legacy Koin module is not invoked by production code`() {
        val invocations = Regex("""\bappModule\(""")
        val outsideDeclaration = productionFiles().filterNot { it.relativeTo(root).invariantSeparatorsPath in legacyGraphFiles }

        assertEquals(emptyList(), offenders(invocations, outsideDeclaration))
    }

    @Test
    fun `exactly one Nexus context is created and only by the foundation`() {
        val creations = offenders(Regex("""NexusContext\.create\("""), productionFiles())

        assertEquals(1, creations.size, creations.joinToString("\n"))
        assertTrue(creations.single().startsWith("di/nexus/GuildNexusFoundation.kt:"))
        val plugin = File(root, "LumaGuilds.kt").readText()
        assertEquals(1, Regex("""GuildRuntimeLifecycle\(""").findAll(plugin).count())
    }
}
