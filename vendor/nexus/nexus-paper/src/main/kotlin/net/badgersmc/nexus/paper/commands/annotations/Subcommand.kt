package net.badgersmc.nexus.paper.commands.annotations

/** Marks a method as a subcommand. Value is space-separated path, e.g. "join" or "admin setup". */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subcommand(
    val value: String
)
