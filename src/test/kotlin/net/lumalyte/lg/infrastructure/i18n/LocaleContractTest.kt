package net.lumalyte.lg.infrastructure.i18n

import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.help.HelpTopics
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag
import net.kyori.adventure.text.minimessage.MiniMessage
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

class LocaleContractTest {

    private val projectRoot = Path.of(System.getProperty("user.dir"))
    private val claimPermissionDynamicKeys = ClaimPermission.entries
        .flatMap { listOf(it.nameKey, it.loreKey) }
        .toSet()
    private val flagDynamicKeys = Flag.entries
        .flatMap { listOf(it.nameKey, it.loreKey) }
        .toSet()
    private val rankPermissionDynamicKeys = RankPermission.entries
        .map { permission -> "permission.${permission.name.lowercase().replace("_", ".")}" }
        .toSet()
    private val finiteMenuStateKeys = setOf(
        "menu.settings.item.mode.lore.hostile",
        "menu.settings.item.mode.lore.peaceful",
        "menu.leaderboards.item.activity.name",
        "menu.leaderboards.item.bank.name",
        "menu.leaderboards.item.kills.name",
        "menu.leaderboards.item.level.name",
        "menu.bank.security.risk.critical",
        "menu.bank.security.risk.high",
        "menu.bank.security.risk.low",
        "menu.bank.security.risk.medium",
        "menu.bank.stats.activity.high",
        "menu.bank.stats.activity.low",
        "menu.bank.stats.activity.moderate",
        "menu.bank.stats.activity.none",
        "menu.bank.stats.activity.recent",
        "menu.bank.stats.activity.very_high",
    )
    private val helpTopicDynamicKeys = HelpTopics.all.flatMap { listOf(it.menuKey, it.pageKey) }.toSet()
    private val localizedHelperKeys = setOf(
        "menu.guild_mode.cooldown.expired",
        "menu.guild_mode.cooldown.hostile",
        "menu.guild_mode.cooldown.lock_expired",
        "menu.guild_mode.cooldown.no_changes",
        "menu.guild_mode.cooldown.peaceful",
        "menu.join_requirements.reason.already_member",
        "menu.join_requirements.reason.error",
        "menu.join_requirements.reason.guild_full",
        "menu.join_requirements.reason.insufficient_funds",
        "menu.join_requirements.reason.ready",
        "menu.join_requirements.reason.vault_unavailable",
        "menu.statistics.chart.label.weeks_ago",
        "menu.statistics.chart.title.balance",
        "menu.statistics.chart.title.contributions",
        "menu.statistics.chart.title.kills",
        "menu.tag_editor.validation.double_brackets",
        "menu.tag_editor.validation.empty",
        "menu.tag_editor.validation.format_error",
        "menu.tag_editor.validation.invalid_syntax",
        "menu.tag_editor.validation.too_long",
        "menu.tag_editor.validation.unclosed",
        "menu.tag_editor.validation.unknown_tag",
    )
    private val declaredDynamicKeys =
        claimPermissionDynamicKeys + flagDynamicKeys + rankPermissionDynamicKeys + finiteMenuStateKeys +
            helpTopicDynamicKeys + localizedHelperKeys

    @Test
    fun `locale values use exclusively MiniMessage without legacy formatting codes`() {
        val legacyCode = Regex("(?i)(?:§[0-9A-FK-ORX]|&(?:[0-9A-FK-OR]|#[0-9A-F]{6}|x(?:&[0-9A-F]){6}))")
        val miniMessage = MiniMessage.miniMessage()
        val violations = localeValues().mapNotNull { (key, value) ->
            when {
                legacyCode.containsMatchIn(value) -> "$key: legacy formatting"
                else -> runCatching { miniMessage.deserialize(value) }
                    .exceptionOrNull()
                    ?.let { "$key: ${it.message?.lineSequence()?.firstOrNull()}" }
            }
        }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `production localization never serializes through lang legacy`() {
        val inventory = LocaleSourceScanner.scan(projectRoot.resolve("src/main/kotlin"))
        val legacyCalls = inventory.calls.filter { it.renderer == "legacy" }

        assertEquals(
            emptyList<LocalizationCall>(),
            legacyCalls,
            legacyCalls.joinToString { "${it.file}:${it.line} lang.${it.renderer}(\"${it.key}\")" },
        )
    }

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
    fun `strict yaml parser accepts every locale root and mapping key`() {
        assertEquals(emptyList<String>(), strictYamlErrors(localeResourceText()))
    }

    @Test
    fun `strict yaml parser rejects typed root mapping keys`() {
        assertEquals(
            listOf("<root>: mapping key is not a string"),
            strictYamlErrors("1: value\n"),
        )
    }

    @Test
    fun `finite dynamic localization families are declared exactly`() {
        val permissionKeys = ClaimPermission.entries.flatMap { listOf(it.nameKey, it.loreKey) }.toSet()
        val flagKeys = Flag.entries.flatMap { listOf(it.nameKey, it.loreKey) }.toSet()
        val rankPermissionKeys = RankPermission.entries
            .map { permission -> "permission.${permission.name.lowercase().replace("_", ".")}" }
            .toSet()

        val expectedMenuStateKeys = setOf(
            "menu.settings.item.mode.lore.hostile",
            "menu.settings.item.mode.lore.peaceful",
            "menu.leaderboards.item.activity.name",
            "menu.leaderboards.item.bank.name",
            "menu.leaderboards.item.kills.name",
            "menu.leaderboards.item.level.name",
            "menu.bank.security.risk.critical",
            "menu.bank.security.risk.high",
            "menu.bank.security.risk.low",
            "menu.bank.security.risk.medium",
            "menu.bank.stats.activity.high",
            "menu.bank.stats.activity.low",
            "menu.bank.stats.activity.moderate",
            "menu.bank.stats.activity.none",
            "menu.bank.stats.activity.recent",
            "menu.bank.stats.activity.very_high",
        )

        assertEquals(expectedMenuStateKeys, finiteMenuStateKeys)
        val expectedHelpTopicKeys = HelpTopics.all
            .flatMap { listOf(it.menuKey, it.pageKey) }
            .toSet()

        assertEquals(
            permissionKeys + flagKeys + rankPermissionKeys + expectedMenuStateKeys + expectedHelpTopicKeys + localizedHelperKeys,
            declaredDynamicKeys,
        )
    }

    @Test
    fun `escaped command metavariables are not locale placeholders`() {
        assertEquals(
            setOf("player"),
            LocaleSourceScanner.placeholderNames("Invite <player> with /guild invite \\<name>"),
        )
    }

    @Test
    fun `source scanner separates literal and dynamic localization calls`() {
        val root = Files.createTempDirectory("locale-source-scanner")
        val source = root.resolve("ScannerFixture.kt")
        Files.writeString(
            source,
            """
            fun render(lang: Any, key: String) {
                // lang.msg("commented.line")
                /* lang.legacy("commented.block") */
                /* outer comment /* nested comment */ lang.raw("after.nested.comment") */
                val ordinary = "lang.raw(\\\"ordinary.string\\\")"
                val raw = ${"\"\"\""}lang.msg("triple.quoted")${"\"\"\""}
                val interpolation = "value ${'$'}{lang.msg("ordinary.interpolation")}"
                val rawInterpolation = ${"\"\"\""}value ${'$'}{lang.legacy("triple.interpolation")}${"\"\"\""}
                lang.msg("command.guild.invite.success", "player" to "Ada")
                lang.legacy("menu.guild.title")
                lang.raw(key)
                val visible = "§cNo permission"
            }
            """.trimIndent()
        )

        val inventory = LocaleSourceScanner.scan(root)

        assertEquals(
            setOf(
                "command.guild.invite.success",
                "menu.guild.title",
                "ordinary.interpolation",
                "triple.interpolation",
            ),
            inventory.literalKeys,
        )
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
        val mismatches = inventory.placeholderMismatches(localeValues())

        assertEquals(
            BASELINE_PLACEHOLDER_MISMATCHES,
            mismatches.size,
            mismatches.joinToString { "${it.key}: expected=${it.expected}, actual=${it.actual}" },
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

    private fun localeResourceText(): String {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("lang/en_US.yml"))
        return stream.use { InputStreamReader(it, Charsets.UTF_8).readText() }
    }

    private fun strictYamlErrors(yaml: String): List<String> {
        val root = Yaml().compose(StringReader(yaml))
        if (root !is MappingNode) return listOf("<root>: locale must be a mapping")

        val errors = mutableListOf<String>()
        inspectMapping(root, "<root>", errors)
        return errors
    }

    private fun inspectMapping(mapping: MappingNode, path: String, errors: MutableList<String>) {
        val keys = mutableListOf<String>()
        mapping.value.forEach { tuple ->
            val key = tuple.keyNode as? ScalarNode
            if (key == null || key.tag != Tag.STR) {
                errors += "$path: mapping key is not a string"
                return@forEach
            }
            keys += key.value
            inspectValue(tuple.valueNode, "$path.${key.value}", errors)
        }
        keys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { errors += "$path: duplicate mapping key '$it'" }
    }

    private fun inspectValue(value: Node, path: String, errors: MutableList<String>) {
        when (value) {
            is MappingNode -> inspectMapping(value, path, errors)
            is ScalarNode -> if (value.tag != Tag.STR) errors += "$path: scalar value is not a string"
            else -> errors += "$path: locale value must be a mapping or string"
        }
    }

    private fun flatten(locale: YamlConfiguration): Map<String, String> =
        locale.getValues(true)
            .filterValues { it !is ConfigurationSection }
            .mapValues { (_, value) -> value as String }

    private fun localeKeys(): Set<String> = flatten(loadLocale()).keys

    private fun localeValues(): Map<String, String> = flatten(loadLocale())

    private companion object {
        const val BASELINE_POSITIONAL_PLACEHOLDERS = 0
        const val BASELINE_MISSING_KEYS = 0
        const val BASELINE_UNUSED_KEYS = 0
        const val BASELINE_DYNAMIC_CALLS = 37
        const val BASELINE_HARDCODED_PLAYER_TEXT = 0
        const val BASELINE_PLACEHOLDER_MISMATCHES = 0
    }
}
