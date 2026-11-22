package net.badgersmc.nexus.annotations

/**
 * Used to disambiguate between multiple beans of the same type.
 * When multiple candidates exist, @Qualifier specifies which bean to inject by name.
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Qualifier(
    val value: String
)
