package net.badgersmc.nexus.annotations

/**
 * Marks a method to be invoked after dependency injection is complete.
 * Useful for initialization logic that requires injected dependencies.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostConstruct
