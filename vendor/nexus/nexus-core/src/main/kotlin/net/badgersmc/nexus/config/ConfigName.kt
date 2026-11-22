package net.badgersmc.nexus.config

/**
 * Specifies a custom name for a config field.
 * If not present or empty, the actual field name is used.
 *
 * Example:
 * ```kotlin
 * @ConfigName("max-players")
 * var maxPlayers: Int = 100
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigName(val value: String)
