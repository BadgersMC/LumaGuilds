package net.badgersmc.nexus.annotations

/**
 * Marks a method to be invoked before the container is shut down.
 * Useful for cleanup logic and resource release.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreDestroy
