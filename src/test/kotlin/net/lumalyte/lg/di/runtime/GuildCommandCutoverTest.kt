package net.lumalyte.lg.di.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `legacy root inventory matches every ACF command alias`() {
        val aliasGroups = commandSources.listFiles { file -> file.extension == "kt" }!!
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
    fun `current candidate fails closed with every unproven root reported`() {
        listOf(true, false).forEach { claimsEnabled ->
            val report = GuildCommandParity.evaluate(claimsEnabled)
            assertFalse(report.proven)
            val blocked = report.missing.map { it.name }.toSet() + report.missingAliases.keys + report.unproven.keys
            assertEquals(GuildCommandParity.requiredRoots(claimsEnabled).map { it.name }.toSet(), blocked)
            assertTrue("pc" in report.missing.map { it.name }, "party chat has no Nexus port yet")

            val failure = assertThrows<CommandCutoverBlocked> { GuildCommandParity.requireProven(claimsEnabled) }
            assertTrue(failure.message!!.contains("pc"))
        }
    }
}
