package net.badgersmc.nexus.annotations

/**
 * Indicates that a class is a component managed by Nexus.
 * Components are automatically detected during classpath scanning and registered in the container.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component(
    val value: String = ""
)
