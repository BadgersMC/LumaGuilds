package net.badgersmc.nexus.annotations

/**
 * Marks a constructor parameter, field, or method as requiring dependency injection.
 * Nexus will automatically resolve and inject the dependency.
 */
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject
