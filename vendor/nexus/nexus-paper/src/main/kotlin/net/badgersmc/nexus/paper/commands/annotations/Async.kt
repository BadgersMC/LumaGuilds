package net.badgersmc.nexus.paper.commands.annotations

/** Method runs in nexusScope coroutine; method may be suspend. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Async
