package net.badgersmc.nexus.commands.annotations

/**
 * Marks a parameter as a context value that should be injected at runtime
 * rather than read from user input.
 *
 * Context parameters are populated by the command adapter based on the
 * parameter type. The set of injectable types is adapter-specific — see the
 * Paper adapter in `nexus-paper` for the canonical list (`Player`,
 * `CommandSender`, `CommandSourceStack`, `Server`).
 *
 * Example:
 * ```kotlin
 * @Command(name = "heal")
 * class HealCommand {
 *     fun execute(
 *         @Context sender: CommandSender,
 *         @Arg("amount") amount: Int
 *     ) { /* ... */ }
 * }
 * ```
 *
 * Context parameters can appear in any order relative to `@Arg` parameters,
 * but it's conventional to place them first.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Context
