package net.badgersmc.nexus.paper.commands.annotations

/** Captures the rest of the command line as a single String argument. Must be the final @Arg. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Greedy
