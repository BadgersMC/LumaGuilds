package net.badgersmc.nexus.config

/**
 * Marks a class as a configuration file.
 * Specifies the filename (without extension) for the HOCON config file.
 *
 * Example:
 * ```kotlin
 * @ConfigFile("main")
 * class MainConfig {
 *     // fields...
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigFile(val value: String)
