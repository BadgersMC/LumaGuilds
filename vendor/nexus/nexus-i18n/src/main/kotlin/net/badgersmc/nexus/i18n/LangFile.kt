package net.badgersmc.nexus.i18n

/**
 * Class-level annotation declaring the JAR resource prefix that holds bundled
 * locale files (e.g. `lang/`). The consumer plugin attaches this to a marker
 * class that lives in the same module as the bundled resources so the i18n
 * service can read them via that class's [ClassLoader].
 *
 * Example:
 * ```
 * @LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
 * class EnthusiaMarketLang
 * ```
 *
 * `lang/en_US.yml` (and any other locale files) live under
 * `src/main/resources/lang/` in the consumer plugin.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LangFile(
    /** JAR resource prefix containing locale YAML files (no leading slash). */
    val resourcePrefix: String = "lang",
    /** Locale id used when the user file is missing — must exist in resources. */
    val defaultLocale: String = "en_US"
)
