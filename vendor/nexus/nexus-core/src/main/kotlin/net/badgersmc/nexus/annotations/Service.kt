package net.badgersmc.nexus.annotations

/**
 * Specialized component for service layer classes.
 * Semantically equivalent to @Component but provides clarity for the service layer.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(
    val value: String = ""
)
