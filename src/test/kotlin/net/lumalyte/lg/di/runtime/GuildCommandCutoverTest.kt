package net.lumalyte.lg.di.runtime

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-1/2: the runtime registers only the explicit Nexus command catalog, and an
 * unproven or missing legacy root blocks activation instead of falling back to ACF.
 */
class GuildCommandCutoverTest {
    private val commandSources = File("src/main/kotlin/net/lumalyte/lg/interaction/commands")

    @Test
    fun `production command sources have no ACF dependencies`() {
        val offenders = commandSources.walkTopDown().filter { source ->
            source.extension == "kt" && source.useLines { lines -> lines.any { it.startsWith("import co.aikar.commands.") } }
        }.map { it.relativeTo(commandSources).invariantSeparatorsPath }.sorted().toList()
        assertEquals(emptyList(), offenders)
    }
    @Test
    fun `Bukkit descriptor reserves only the administrative commands owned by Bukkit`() {
        val descriptor = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File("src/main/resources/plugin.yml"))
        val commands = requireNotNull(descriptor.getConfigurationSection("commands"))
        assertEquals(setOf("lumaguilds", "bedrockcachestats", "vaultrollback", "bankcredit", "removevault"), commands.getKeys(false))
        val labels = commands.getKeys(false) + commands.getKeys(false).flatMap { commands.getStringList("$it.aliases") }
        assertTrue(GuildCommandParity.legacyRoots.flatMap { it.labels }.none { it in labels })
    }
    @Test
    fun `legacy root inventory matches every ACF command alias`() {
        val fixtures = File("src/test/kotlin/net/lumalyte/lg/interaction/commands")
        val aliasGroups = fixtures.listFiles { file -> file.extension == "kt" }!!
            .flatMap { file -> Regex("""@CommandAlias\("([^"]+)"\)""").findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .map { group -> group.split('|').toSet() }
            .toSet()

        val inventory = GuildCommandParity.legacyRoots.map { it.labels }.toSet()

        assertEquals(aliasGroups, inventory)
    }

    @Test
    fun `claims disabled does not require claim roots`() {
        val disabled = GuildCommandParity.requiredRoots(claimsEnabled = false).map { it.name }.toSet()
        val enabled = GuildCommandParity.requiredRoots(claimsEnabled = true).map { it.name }.toSet()

        assertEquals(setOf("guild", "pc", "gc", "gac", "gmc", "ga", "gfa"), disabled)
        assertEquals(disabled + setOf("claim", "claimlist", "claimmenu", "claimoverride"), enabled)
    }

    @Test
    fun `catalog contains exactly the Nexus command classes in source`() {
        val declared = File(commandSources, "nexus").listFiles { file -> file.extension == "kt" }!!
            .flatMap { file ->
                Regex("""@Command\([^)]*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:internal\s+)?class\s+(\w+)""")
                    .findAll(file.readText()).map { it.groupValues[1] }.toList()
            }
            .toSet()

        val catalog = NexusCommandCatalog.classes(claimsEnabled = true).map { it.simpleName }.toSet()

        assertEquals(declared, catalog, "Newly ported command classes must be added to NexusCommandCatalog centrally")
    }

    @Test
    fun `claims disabled catalog contains no claim command class`() {
        val disabled = NexusCommandCatalog.classes(claimsEnabled = false)

        assertTrue(disabled.none { it.simpleName!!.contains("Claim") || it.simpleName!!.contains("Trust") })
        assertTrue(NexusCommandCatalog.providedRoots(claimsEnabled = false).none { it.claimsOnly || it.name.startsWith("claim") })
    }

    @Test
    fun `gate passes only when every required label is provided and proven`() {
        val required = listOf(CommandRoot("guild", setOf("g")), CommandRoot("pc", setOf("pchat", "partychat")))
        val provided = listOf(CommandRoot("guild", setOf("g")), CommandRoot("pc", setOf("pchat", "partychat")))

        val report = evaluateCommandParity(required, provided, mapOf("guild" to RootParity.Proven, "pc" to RootParity.Proven))

        assertTrue(report.proven)
    }

    @Test
    fun `missing roots, missing aliases and unproven roots each block`() {
        val required = listOf(CommandRoot("guild", setOf("g")), CommandRoot("pc", setOf("pchat")), CommandRoot("gc"))
        val provided = listOf(CommandRoot("guild"), CommandRoot("gc"))

        val report = evaluateCommandParity(
            required, provided,
            mapOf("guild" to RootParity.Proven, "gc" to RootParity.Unproven("root greedy argument fails at pinned Nexus")),
        )

        assertFalse(report.proven)
        assertEquals(setOf("pc"), report.missing.map { it.name }.toSet())
        assertEquals(mapOf("guild" to setOf("g")), report.missingAliases)
        assertEquals(setOf("gc"), report.unproven.keys)
        assertTrue(report.describe().contains("root greedy argument fails at pinned Nexus"))
    }

    @Test
    fun `undeclared parity status is unproven`() {
        val report = evaluateCommandParity(listOf(CommandRoot("ga")), listOf(CommandRoot("ga")), emptyMap())

        assertEquals(setOf("ga"), report.unproven.keys)
    }

    @Test
    fun `reviewed Nexus revision provides every required root in both claims modes`() {
        listOf(true, false).forEach { claimsEnabled ->
            val report = GuildCommandParity.evaluate(claimsEnabled)
            assertTrue(report.proven, report.describe())
            GuildCommandParity.requireProven(claimsEnabled)
        }
    }
}
