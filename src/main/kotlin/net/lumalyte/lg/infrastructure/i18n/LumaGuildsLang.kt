package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangFile

/**
 * Marker class that locates LumaGuilds' bundled locale files for
 * `nexus-i18n`'s `LangService`. Lives in the same module as the
 * `src/main/resources/lang/` YAML files so the service can resolve them via
 * this class's [ClassLoader].
 *
 * Adding new locales: drop another `lang/<locale>.yml` next to `en_US.yml`.
 */
@LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
class LumaGuildsLang