package net.badgersmc.nexus.paper.commands.annotations

/**
 * Attach a named suggestion provider to an @Arg parameter for tab-completion.
 *
 * The [value] must match a key in the `suggestionProviders` map passed to
 * [registerPaperCommands][net.badgersmc.nexus.paper.registerPaperCommands].
 *
 * Example:
 * ```kotlin
 * @Subcommand("start")
 * fun start(@Arg("arena") @Suggests("arenaNames") arenaName: String)
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Suggests(val value: String)
