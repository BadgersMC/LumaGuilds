package net.badgersmc.nexus.config

/**
 * Adds documentation comments to config fields.
 * Multiple lines can be specified as separate strings in the array.
 *
 * Example:
 * ```kotlin
 * @Comment("Maximum number of players allowed", "Set to -1 for unlimited")
 * var maxPlayers: Int = 100
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Comment(vararg val value: String)
