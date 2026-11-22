package net.badgersmc.nexus.commands

/**
 * Exception thrown when command scanning, validation, or registration fails.
 *
 * This exception is used for fail-fast validation during context initialization.
 * If a CommandException is thrown during startup, it indicates a configuration
 * error that must be fixed before the application can run.
 *
 * Common causes:
 * - Missing ArgumentResolver for a parameter type
 * - Invalid argument order (required after optional)
 * - No execute() method found
 * - Invalid @Context parameter type
 * - Duplicate command names
 */
class CommandException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
