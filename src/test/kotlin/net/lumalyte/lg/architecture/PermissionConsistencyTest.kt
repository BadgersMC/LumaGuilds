package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Permission declaration consistency: every @CommandPermission node used in
 * code MUST be declared in plugin.yml, and no stale `lumalyte.` prefix may
 * survive in permission strings (Lumalyte rename leftovers).
 *
 * REQ-001, REQ-002, REQ-003 (docs/requirements.md).
 */
class PermissionConsistencyTest {

    private val projectRoot: File = File("").absoluteFile
    private val pluginYml: File = File(projectRoot, "src/main/resources/plugin.yml")
    private val sourceRoot: File = File(projectRoot, "src/main/kotlin")

    /** All nodes declared in plugin.yml (top-level permission keys + wildcard children). */
    private fun declaredNodes(): Set<String> {
        val nodes = mutableSetOf<String>()
        var inPermissions = false
        val nodeKey = Regex("""^(\s{2,6})([\w.]+):(\s*(true|false))?$""")
        for (line in pluginYml.readLines()) {
            when {
                line.trim() == "permissions:" -> inPermissions = true
                !inPermissions -> Unit
                line.trim() == "children:" -> Unit
                else -> {
                    val m = nodeKey.matchEntire(line)
                    if (m != null) nodes += m.groupValues[2]
                }
            }
        }
        return nodes
    }

    /** All permission nodes referenced by @CommandPermission in main sources. */
    private fun usedNodes(): Set<String> {
        val perm = Regex("""@CommandPermission\("([\w.]+)"\)""")
        val used = mutableSetOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEach { line ->
                    perm.findAll(line).forEach { used += it.groupValues[1] }
                }
            }
        return used
    }

    @Test
    fun `every CommandPermission node used in code is declared in plugin yml`() {
        val declared = declaredNodes()
        val missing = usedNodes() - declared
        assertEquals(emptySet<String>(), missing,
            "Permission nodes used in code but missing from plugin.yml " +
                "(default false -> ACF silently blocks the command): $missing")
    }

    @Test
    fun `no stale lumalyte prefix survives in permission strings`() {
        val stale = Regex("""\Q"\E(lumalyte\.[\w.]+)""")
        val hits = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    stale.findAll(line).forEach { hits += "${f.name}:${i + 1} ${it.groupValues[1]}" }
                }
            }
        assertTrue(hits.isEmpty(), "Stale lumalyte.* permission prefix (Lumalyte rename leftover):\n${hits.joinToString("\n")}")
    }

    @Test
    fun `claim command nodes used by claim commands are declared and granted via wildcard`() {
        // REQ-003 regression guard: these were once claimed missing; they are declared
        // under lumaguilds.command.* so the wildcard (default op) grants them.
        val declared = declaredNodes()
        val claimNodes = listOf(
            "lumaguilds.command.claim.partitions",
            "lumaguilds.command.claim.trustlist",
            "lumaguilds.command.claimmenu",
            "lumaguilds.command.claimoverride",
        )
        claimNodes.forEach { node ->
            assertTrue(node in declared, "Claim node $node must stay declared in plugin.yml")
        }
        // And they must be children of the lumaguilds.command.* wildcard.
        val wildcardChildren = pluginYml.readLines()
            .dropWhile { it.trim() != "lumaguilds.command.*:" }
            .takeWhile { it.trim() != "lumaguilds.command.claim:" }
            .map { it.trim() }
        claimNodes.forEach { node ->
            assertTrue(
                wildcardChildren.any { it == "$node: true" },
                "$node must remain a child of lumaguilds.command.*"
            )
        }
    }
}
