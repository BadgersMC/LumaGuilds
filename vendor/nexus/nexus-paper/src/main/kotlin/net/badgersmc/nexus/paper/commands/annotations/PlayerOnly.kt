package net.badgersmc.nexus.paper.commands.annotations

/** Require sender to be a Player; injects Player as the first @Context parameter. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PlayerOnly
