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

    private val kotlinFiles: List<File> by lazy {
        sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /** Legacy ACF `@CommandPermission("node")` string nodes (may legitimately become empty once ACF is removed). */
    private fun acfNodes(): Set<String> {
        val perm = Regex("""@CommandPermission\(\s*"([\w.]+)"\s*\)""")
        return kotlinFiles.flatMap { f -> perm.findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
    }

    /** Top-level `const val NAME = "literal"` string constants across main sources. */
    private fun stringConstants(): Map<String, String> {
        val const = Regex("""const\s+val\s+(\w+)\s*(?::\s*String\s*)?=\s*"([^"$\\]*)"""")
        val out = mutableMapOf<String, String>()
        kotlinFiles.forEach { f -> const.findAll(f.readText()).forEach { out[it.groupValues[1]] = it.groupValues[2] } }
        return out
    }

    /**
     * Nexus `@Permission(...)` nodes: string literals plus const-val identifiers.
     * Any argument that is neither a literal nor a resolvable constant is returned
     * in [second] so callers can fail loudly instead of silently skipping it.
     */
    private fun nexusNodes(): Pair<Set<String>, Set<String>> {
        val arg = Regex("""@Permission\(\s*(?:value\s*=\s*)?([^)]*?)\s*\)""")
        val literal = Regex(""""([\w.]+)"""")
        val ident = Regex("""[A-Za-z_]\w*""")
        val consts = stringConstants()
        val nodes = mutableSetOf<String>()
        val unresolved = mutableSetOf<String>()
        kotlinFiles.forEach { f ->
            arg.findAll(f.readText()).forEach { m ->
                val raw = m.groupValues[1]
                val lits = literal.findAll(raw).map { it.groupValues[1] }.toList()
                nodes += lits
                // Non-literal remainder: identifiers outside quotes must resolve to constants.
                val rest = raw.replace(Regex("\"[^\"]*\""), " ")
                ident.findAll(rest).map { it.value }.forEach { id ->
                    val v = consts[id]
                    if (v != null) nodes += v else unresolved += "${f.name}:$id"
                }
            }
        }
        return nodes to unresolved
    }

    /** All permission nodes referenced by ACF @CommandPermission and Nexus @Permission in main sources. */
    private fun usedNodes(): Set<String> = acfNodes() + nexusNodes().first

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
    fun `permission extraction is not vacuous and resolves every Nexus permission token`() {
        val (nexus, unresolved) = nexusNodes()
        assertEquals(emptySet<String>(), unresolved,
            "Nexus @Permission arguments that are neither string literals nor const val strings " +
                "(scanner cannot validate them against plugin.yml): $unresolved")
        assertTrue(nexus.isNotEmpty(), "No Nexus @Permission nodes extracted; scanner is vacuous or Nexus sources moved")
        assertTrue("lumaguilds.guild.chat" in nexus,
            "const-val token QUICK_CHAT_PERMISSION must resolve to lumaguilds.guild.chat; got $nexus")
        assertTrue(usedNodes().isNotEmpty(), "No permission nodes extracted from ACF or Nexus sources")
        assertTrue(declaredNodes().isNotEmpty(), "No nodes parsed from plugin.yml")
    }

    @Test
    fun `every ACF CommandPermission and Nexus Permission node used in code is declared in plugin yml`() {
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
