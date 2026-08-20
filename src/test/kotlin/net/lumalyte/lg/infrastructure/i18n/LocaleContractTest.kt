package net.lumalyte.lg.infrastructure.i18n

import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

class LocaleContractTest {

    private val projectRoot = Path.of(System.getProperty("user.dir"))
    private val declaredDynamicKeys =
        ClaimPermission.entries.flatMap { listOf(it.nameKey, it.loreKey) }.toSet() +
            Flag.entries.flatMap { listOf(it.nameKey, it.loreKey) }.toSet()

    @Test
    fun `locale contains no positional placeholders`() {
        val positional = flatten(loadLocale()).filterValues { Regex("<\\d+>").containsMatchIn(it) }

        assertEquals(BASELINE_POSITIONAL_PLACEHOLDERS, positional.size, positional.keys.sorted().joinToString())
    }

    @Test
    fun `yaml boolean words remain strings`() {
        val locale = loadLocale()

        assertEquals("No", locale.getString("menu.confirmation.item.no.name"))
        assertEquals("Yes", locale.getString("menu.confirmation.item.yes.name"))
    }

    @Test
    fun `locale scalar values are strings`() {
        val nonStringValues = loadLocale().getValues(true)
            .filterValues { it !is ConfigurationSection && it !is String }

        assertEquals(emptyMap<String, Any?>(), nonStringValues)
    }

    @Test
    fun `locale root namespaces occur once in the resource`() {
        val roots = localeResourceLines()
            .filter { it.matches(Regex("^[A-Za-z][A-Za-z0-9_-]*:\\s*(?:#.*)?$")) }
            .map { it.substringBefore(':') }

        assertEquals(roots.size, roots.toSet().size, roots.joinToString())
    }

    @Test
    fun `locale mapping keys are nonblank strings`() {
        val keys = loadLocale().getKeys(true)

        assertEquals(emptyList<String>(), keys.filter(String::isBlank))
    }

    @Test
    fun `source scanner separates literal and dynamic localization calls`() {
        val root = Files.createTempDirectory("locale-source-scanner")
        val source = root.resolve("ScannerFixture.kt")
        Files.writeString(
            source,
            """
            fun render(lang: Any, key: String) {
                lang.msg("command.guild.invite.success", "player" to "Ada")
                lang.legacy("menu.guild.title")
                lang.raw(key)
                val visible = "§cNo permission"
            }
            """.trimIndent()
        )

        val inventory = LocaleSourceScanner.scan(root)

        assertEquals(setOf("command.guild.invite.success", "menu.guild.title"), inventory.literalKeys)
        assertEquals(setOf("player"), inventory.calls.single { it.key == "command.guild.invite.success" }.placeholderNames)
        assertEquals(1, inventory.dynamicCalls.size)
        assertEquals(1, inventory.playerTextCandidates.size)
    }

    @Test
    fun `literal localization references match the recovery baseline`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))
        val missing = inventory.literalKeys - localeKeys()

        assertEquals(BASELINE_MISSING_KEYS, missing.size, missing.sorted().joinToString())
    }

    @Test
    fun `locale dead keys match the recovery baseline`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))
        val unused = localeKeys() - inventory.literalKeys - declaredDynamicKeys

        assertEquals(BASELINE_UNUSED_KEYS, unused.size, unused.sorted().joinToString())
    }

    @Test
    fun `dynamic localization calls match the recovery baseline`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))

        assertEquals(
            BASELINE_DYNAMIC_CALLS,
            inventory.dynamicCalls.size,
            inventory.dynamicCalls.joinToString { "${it.file}:${it.line} ${it.source}" },
        )
    }

    @Test
    fun `hardcoded player text candidates match the recovery baseline`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))

        assertEquals(
            BASELINE_HARDCODED_PLAYER_TEXT,
            inventory.playerTextCandidates.size,
            inventory.playerTextCandidates.joinToString { "${it.file}:${it.line} ${it.source}" },
        )
    }

    @Test
    fun `call site placeholder mismatches match the recovery baseline`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))

        assertEquals(
            BASELINE_PLACEHOLDER_MISMATCHES,
            inventory.placeholderMismatches(localeValues()).size,
        )
    }

    private fun loadLocale(): YamlConfiguration {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("lang/en_US.yml"))
        return stream.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }
    }

    private fun localeResourceLines(): List<String> {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("lang/en_US.yml"))
        return stream.use { InputStreamReader(it, Charsets.UTF_8).readLines() }
    }

    private fun flatten(locale: YamlConfiguration): Map<String, String> =
        locale.getValues(true)
            .filterValues { it !is ConfigurationSection }
            .mapValues { (_, value) -> value as String }

    private fun localeKeys(): Set<String> = flatten(loadLocale()).keys

    private fun localeValues(): Map<String, String> = flatten(loadLocale())

    private companion object {
        const val BASELINE_POSITIONAL_PLACEHOLDERS = 135
        const val BASELINE_MISSING_KEYS = 0
        const val BASELINE_UNUSED_KEYS = 388
        const val BASELINE_DYNAMIC_CALLS = 32
        const val BASELINE_HARDCODED_PLAYER_TEXT = 4016
        const val BASELINE_PLACEHOLDER_MISMATCHES = 0
    }
}
