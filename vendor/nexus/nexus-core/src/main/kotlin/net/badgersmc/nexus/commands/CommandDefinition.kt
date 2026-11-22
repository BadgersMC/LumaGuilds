package net.badgersmc.nexus.commands

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Metadata for a discovered command.
 *
 * Contains all information needed to wire a command into an adapter:
 * - The command class and its @Command annotation
 * - The `execute()` method to invoke
 * - Parameter metadata for argument/context injection
 *
 * Produced by an adapter-specific scanner during classpath scanning.
 */
data class CommandDefinition(
    /**
     * The command class (annotated with @Command).
     */
    val commandClass: KClass<*>,

    /**
     * The @Command annotation from the class.
     */
    val annotation: Command,

    /**
     * The execute() method that will be invoked when the command runs.
     */
    val executeMethod: KFunction<*>,

    /**
     * Metadata for each parameter of the execute() method.
     * Parameters are in the same order as the method signature.
     */
    val parameters: List<CommandParameter>
)

/**
 * Metadata for a single parameter of the execute() method.
 *
 * Each parameter is either:
 * - An @Arg parameter (user-provided argument)
 * - A @Context parameter (runtime-injected value)
 *
 * Exactly one of argAnnotation or contextAnnotation must be non-null.
 */
data class CommandParameter(
    /**
     * Parameter name (from reflection).
     */
    val name: String,

    /**
     * Parameter type (e.g., `String::class`, `Int::class`, `CommandSender::class`).
     */
    val type: KClass<*>,

    /**
     * Position in the execute() method signature (0-indexed).
     */
    val index: Int,

    /**
     * The @Arg annotation if this is a user-provided argument.
     * Null if this is a @Context parameter.
     */
    val argAnnotation: Arg? = null,

    /**
     * The @Context annotation if this is a runtime-injected value.
     * Null if this is an @Arg parameter.
     */
    val contextAnnotation: Context? = null
) {
    /**
     * True if this parameter has @Arg(required = false) or a default value.
     */
    val isOptional: Boolean
        get() = argAnnotation?.let { !it.required || it.defaultValue.isNotEmpty() } ?: false

    /**
     * True if this parameter is an @Arg (user-provided argument).
     */
    val isArg: Boolean
        get() = argAnnotation != null

    /**
     * True if this parameter is a @Context (runtime-injected value).
     */
    val isContext: Boolean
        get() = contextAnnotation != null

    init {
        // Validation: exactly one annotation must be present
        val hasArg = argAnnotation != null
        val hasContext = contextAnnotation != null

        require(hasArg xor hasContext) {
            "Parameter '$name' must have exactly one of @Arg or @Context annotation"
        }
    }
}
