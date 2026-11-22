package net.badgersmc.nexus.commands.annotations

/**
 * Marks a class as a command that should be auto-discovered and registered.
 *
 * Used by every Nexus command adapter (currently `nexus-paper`'s Paper
 * Brigadier adapter). Adapters scan the consumer's class path for
 * `@Command`-annotated classes and wire each one up using its own conventions
 * (e.g. nexus-paper looks for `@Subcommand` methods on the class).
 *
 * Example (Paper):
 * ```kotlin
 * @Command(name = "sg", description = "Survival Games", permission = "sg.use")
 * class SGCommand(private val games: GameManager) {
 *     @Subcommand("join")
 *     fun join(@Context player: Player, @Arg("arena") arena: String) {
 *         games.joinGame(player, arena)
 *     }
 * }
 * ```
 *
 * @param name The command name (e.g., "teleport", "give")
 * @param description Command description shown in help
 * @param permission Required permission (empty = no permission required)
 * @param aliases Alternative names for the command
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Command(
    val name: String,
    val description: String = "",
    val permission: String = "",
    val aliases: Array<String> = []
)
