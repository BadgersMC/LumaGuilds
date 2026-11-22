package net.badgersmc.nexus.annotations

/**
 * Specialized component for repository/data access layer classes.
 * Semantically equivalent to @Component but provides clarity for the persistence layer.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(
    val value: String = ""
)
