package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Permission declaration consistency: every @CommandPermission node used in
 * code MUST be declared in plugin.yml, must carry the intended defaults, and
 * no stale `lumalyte.` prefix may survive in code or shipped config.
 *
 * REQ-001, REQ-002, REQ-003 (docs/requirements.md).
 */
class PermissionConsistencyTest {

    private val projectRoot: File = File("").absoluteFile
    private val pluginYml: File = File(projectRoot, "src/main/resources/plugin.yml")
    private val configYml: File = File(projectRoot, "src/main/resources/config.yml")
    private val sourceRoot: File = File(projectRoot, "src/main/kotlin")

    private val guildNodes14 = listOf(
        "lumaguilds.guild.join", "lumaguilds.guild.list", "lumaguilds.guild.lfg",
        "lumaguilds.guild.decline", "lumaguilds.guild.invites", "lumaguilds.guild.leave",
        "lumaguilds.guild.transfer", "lumaguilds.guild.getvault", "lumaguilds.guild.vault",
        "lumaguilds.guild.help", "lumaguilds.guild.ally", "lumaguilds.guild.enemy",
        "lumaguilds.guild.truce", "lumaguilds.guild.neutral",
    )

    private val claimNodes4 = listOf(
        "lumaguilds.command.claim.partitions",
        "lumaguilds.command.claim.trustlist",
        "lumaguilds.command.claimmenu",
        "lumaguilds.command.claimoverride",
    )

    private val lines: List<String> by lazy { pluginYml.readLines() }

    /** All nodes declared in plugin.yml (top-level permission keys + wildcard children). */
    private fun declaredNodes(): Set<String> {
        val nodes = mutableSetOf<String>()
        var inPermissions = false
        val nodeKey = Regex("""^(\s{2,6})([\w.]+):(\s*(true|false))?$""")
        for (line in lines) {
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

    /** `default:` value of an individually-declared node, or null if undeclared. */
    private fun individualDefault(node: String): String? {
        val idx = lines.indexOfFirst { it.trim() == "$node:" }
        if (idx < 0) return null
        return lines.drop(idx + 1)
            .firstOrNull { it.trim().startsWith("default:") }
            ?.trim()
            ?.removePrefix("default:")
            ?.trim()
    }

    /** Children entries of a wildcard node (the block after `children:` up to the next 2-space key). */
    private fun wildcardChildren(wildcard: String): List<String> {
        val start = lines.indexOfFirst { it.trim() == "$wildcard:" }
        require(start >= 0) { "wildcard $wildcard not found in plugin.yml" }
        val childrenIdx = lines.drop(start).indexOfFirst { it.trim() == "children:" }
        if (childrenIdx < 0) return emptyList()
        return lines.drop(start + childrenIdx + 1)
            .takeWhile { it.startsWith("      ") && !it.trim().startsWith("children:") }
            .map { it.trim() }
    }

    @Test
    fun `every CommandPermission node used in code is declared in plugin yml`() {
        val missing = usedNodes() - declaredNodes()
        assertEquals(emptySet<String>(), missing,
            "Permission nodes used in code but missing from plugin.yml " +
                "(default false -> ACF silently blocks the command): $missing")
    }

    @Test
    fun `no stale lumalyte prefix survives in permission strings or shipped config`() {
        val stale = Regex("""\Q"\E(lumalyte\.[\w.]+)""")
        val hits = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    stale.findAll(line).forEach { hits += "${f.name}:${i + 1} ${it.groupValues[1]}" }
                }
            }
        configYml.readLines().forEachIndexed { i, line ->
            stale.findAll(line).forEach { hits += "config.yml:${i + 1} ${it.groupValues[1]}" }
        }
        assertTrue(hits.isEmpty(), "Stale lumalyte.* prefix (Lumalyte rename leftover):\n${hits.joinToString("\n")}")
    }

    @Test
    fun `the 14 guild command nodes are default true and wildcard children`() {
        val children = wildcardChildren("lumaguilds.guild.*")
        guildNodes14.forEach { node ->
            assertTrue(children.any { it == "$node: true" },
                "$node must be a child of lumaguilds.guild.*")
            assertEquals("true", individualDefault(node),
                "$node must be individually declared with default: true")
        }
    }

    @Test
    fun `claim command nodes are default op and granted via wildcard`() {
        // REQ-003 regression guard: declared individually (default: op) AND as
        // children of lumaguilds.command.* so the wildcard (default op) grants them.
        val children = wildcardChildren("lumaguilds.command.*")
        claimNodes4.forEach { node ->
            assertTrue(children.any { it == "$node: true" },
                "$node must remain a child of lumaguilds.command.*")
            assertEquals("op", individualDefault(node),
                "$node must be individually declared with default: op")
        }
    }
}
