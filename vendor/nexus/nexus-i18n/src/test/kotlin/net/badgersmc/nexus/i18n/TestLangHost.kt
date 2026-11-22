package net.badgersmc.nexus.i18n

import java.io.File

/**
 * Minimal [LangHost] for unit tests. Avoids the need to instantiate the
 * abstract [org.bukkit.plugin.java.JavaPlugin] class.
 */
internal class TestLangHost(
    override val dataFolder: File,
    override val resourceClassLoader: ClassLoader
) : LangHost
