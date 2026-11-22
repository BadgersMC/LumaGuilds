package net.badgersmc.nexus.annotations

/**
 * Defines the lifecycle scope of a component.
 * SINGLETON: One instance shared across the entire application (default)
 * PROTOTYPE: New instance created for each injection
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(
    val value: ScopeType = ScopeType.SINGLETON
)

enum class ScopeType {
    SINGLETON,
    PROTOTYPE
}
