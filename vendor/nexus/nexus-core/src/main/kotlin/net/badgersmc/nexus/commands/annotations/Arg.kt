package net.badgersmc.nexus.commands.annotations

/**
 * Marks a parameter as a command argument that will be provided by the user.
 *
 * The parameter type must have a registered resolver — see each adapter's
 * resolver registry (`PaperArgumentResolvers` for nexus-paper).
 *
 * Example:
 * ```kotlin
 * fun execute(
 *     @Arg("target", "Player to heal") target: Player,
 *     @Arg("amount", "Amount of health", required = false, defaultValue = "20") amount: Int
 * )
 * ```
 *
 * **Argument order rules:**
 * - Required arguments must come before optional arguments
 * - Arguments are processed left-to-right in the method signature
 *
 * **Argument kinds:**
 * - Required (`required = true`, no `defaultValue`): user must provide it
 * - Optional (`required = false`): can be omitted
 * - Default (`defaultValue` specified): value used if omitted
 *
 * @param name The argument name (used in error messages and flag-style adapters)
 * @param description Description shown in help text
 * @param required Whether the argument is required (true) or optional (false)
 * @param defaultValue Default value if not provided
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Arg(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val defaultValue: String = ""
)
