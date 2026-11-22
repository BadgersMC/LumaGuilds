package net.badgersmc.nexus.config

/**
 * Marks a field as transient - it will not be saved to or loaded from the config file.
 * Useful for runtime-only fields or computed properties.
 *
 * Example:
 * ```kotlin
 * @Transient
 * var cachedValue: String? = null
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Transient
