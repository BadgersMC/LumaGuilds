package net.badgersmc.nexus.i18n

import net.badgersmc.nexus.resources.ResourceExtractor
import net.kyori.adventure.text.Component as AdventureComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Minimal view of the host environment the [LangService] needs. Lets us swap a
 * real [JavaPlugin] for a hand-rolled stub in unit tests without instantiating
 * the abstract Bukkit class.
 */
interface LangHost {
    val dataFolder: File
    val resourceClassLoader: ClassLoader

    companion object {
        /**
         * Build a [LangHost] view over an existing Paper [JavaPlugin]. The
         * recommended path for production callers.
         */
        fun of(plugin: JavaPlugin): LangHost = object : LangHost {
            override val dataFolder: File get() = plugin.dataFolder
            override val resourceClassLoader: ClassLoader get() = plugin.javaClass.classLoader
        }
    }
}

/**
 * Adventure / MiniMessage backed i18n service. Loads a YAML locale file from the
 * consumer plugin's data folder, copies the bundled default on first run, and
 * overlays bundled defaults so a partial user file still resolves missing keys.
 *
 * Recommended consumer wiring:
 *
 * ```
 * @LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
 * class FooLang
 *
 * // onEnable:
 * val lang = LangService(LangHost.of(this), Locale(config.lang.locale), FooLang::class.java)
 * nexus.registerBean("langService", LangService::class, lang)
 * ```
 */
class LangService(
    private val host: LangHost,
    private val locale: Locale,
    private val langClass: Class<*>
) {
    /** Convenience overload for the production path. */
    constructor(plugin: JavaPlugin, locale: Locale, langClass: Class<*>) :
        this(LangHost.of(plugin), locale, langClass)

    private val logger = Logger.getLogger(LangService::class.java.name)
    private val mm = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacySection()
    private val globalResolvers = ConcurrentHashMap<String, TagResolver>()

    private val annotation: LangFile = langClass.getAnnotation(LangFile::class.java)
        ?: error("Class ${langClass.name} is missing @LangFile — required for LangService")
    private val resourcePrefix: String = annotation.resourcePrefix.trim('/')
    private val defaultLocale: String = annotation.defaultLocale

    init {
        require(resourcePrefix.isNotBlank()) {
            "@LangFile.resourcePrefix on ${langClass.name} must not be blank"
        }
        require(defaultLocale.isNotBlank() && !defaultLocale.contains('/') && !defaultLocale.contains('\\')) {
            "@LangFile.defaultLocale on ${langClass.name} must be a non-blank locale id without path separators (got '$defaultLocale')"
        }
    }

    @Volatile
    private var values: Map<String, String> = emptyMap()

    @Volatile
    private var prefix: String = ""

    init {
        reload()
    }

    /**
     * Reload the active locale from disk, copying the bundled default if the
     * user file is missing.
     */
    fun reload() {
        val effectiveLocale = locale.id.ifBlank { defaultLocale }
        val target = File(File(host.dataFolder, resourcePrefix), "$effectiveLocale.yml")

        ResourceExtractor.extractIfMissing(
            host.resourceClassLoader, "$resourcePrefix/$effectiveLocale.yml", target
        ) ?: run {
            // Locale file missing from JAR — try the default locale as a fallback.
            if (effectiveLocale != defaultLocale) {
                val fallback = File(File(host.dataFolder, resourcePrefix), "$defaultLocale.yml")
                ResourceExtractor.extractIfMissing(
                    host.resourceClassLoader, "$resourcePrefix/$defaultLocale.yml", fallback
                )
            }
        }

        val userYaml = if (target.exists()) {
            YamlConfiguration.loadConfiguration(InputStreamReader(target.inputStream(), StandardCharsets.UTF_8))
        } else {
            YamlConfiguration()
        }

        val flat = mutableMapOf<String, String>()

        // 1. Layer bundled defaults first so missing user keys are still resolved.
        host.resourceClassLoader.getResourceAsStream("$resourcePrefix/$defaultLocale.yml")?.use { defStream ->
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(defStream, StandardCharsets.UTF_8))
            flatten("", defaults.getValues(true), flat)
        }
        // 2. Overlay user values on top — they win.
        flatten("", userYaml.getValues(true), flat)

        values = flat
        prefix = flat["prefix"] ?: ""
        logger.fine("LangService loaded ${flat.size} keys from '${target.name}'")
    }

    private fun flatten(prefix: String, source: Map<String, Any?>, out: MutableMap<String, String>) {
        for ((rawKey, rawValue) in source) {
            val path = if (prefix.isEmpty()) rawKey else "$prefix.$rawKey"
            when (rawValue) {
                is String -> out[path] = rawValue
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    flatten(path, rawValue as Map<String, Any?>, out)
                }
                else -> { /* lists, numbers, booleans — not used at present */ }
            }
        }
    }

    /**
     * Register a [TagResolver] that will be available to every [msg] / [legacy]
     * call across the plugin lifetime. Useful for cross-cutting placeholders
     * (server name, world name, player meta…).
     */
    fun registerGlobalResolver(name: String, resolver: TagResolver) {
        globalResolvers[name] = resolver
    }

    /**
     * Resolve [key] and render the result as an Adventure [AdventureComponent].
     */
    fun msg(key: String, vararg placeholders: Pair<String, Any?>): AdventureComponent {
        val template = values[key] ?: return AdventureComponent.text(key)
        return mm.deserialize(template, buildResolvers(placeholders))
    }

    /**
     * Same as [msg] but returns a legacy §-serialised string. Use only for
     * paths that cannot render Adventure components directly (e.g. Cumulus
     * Bedrock forms).
     */
    fun legacy(key: String, vararg placeholders: Pair<String, Any?>): String {
        return legacy.serialize(msg(key, *placeholders))
    }

    /**
     * Return the raw MiniMessage template for [key] without rendering it. Useful
     * when the value will be embedded into another template as a `<placeholder>`
     * payload.
     */
    fun raw(key: String): String = values[key] ?: key

    private fun buildResolvers(placeholders: Array<out Pair<String, Any?>>): TagResolver {
        val builder = TagResolver.builder()
        builder.resolver(Placeholder.parsed("prefix", prefix))
        for ((name, value) in placeholders) {
            builder.resolver(Placeholder.parsed(name, value?.toString() ?: ""))
        }
        for ((_, resolver) in globalResolvers) {
            builder.resolver(resolver)
        }
        return builder.build()
    }
}
