package net.lumalyte.lg.utils

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.MessageService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Safe executor for command operations that provides error handling and user-friendly feedback.
 * Wraps command executions to catch exceptions and present them appropriately to users.
 */
object CommandSafeExecutor : KoinComponent {

    private val messageService: MessageService by inject()
    private val logger: Logger by inject()

    /**
     * Executes a command block safely with CommandContext, extracting audience and command name automatically.
     *
     * @param context The command context from Brigadier
     * @param block The command execution block that returns an Int result
     * @return The result of the block, or 0 if an error occurred
     */
    fun execute(context: com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack>, block: () -> Int): Int {
        val sender = context.source.sender
        val commandName = context.input.split(" ").firstOrNull() ?: "unknown"
        return try {
            block()
        } catch (e: Exception) {
            handleCommandError(sender, commandName, e)
            0
        }
    }

    /**
     * Executes a command block safely, catching exceptions and providing user feedback.
     *
     * @param audience The audience (player/console) to send error messages to
     * @param commandName The name of the command being executed (for logging)
     * @param block The command execution block
     * @return true if execution succeeded, false if an error occurred
     */
    fun execute(audience: Audience, commandName: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            handleCommandError(audience, commandName, e)
            false
        }
    }

    /**
     * Executes a command block safely with a custom error message key.
     *
     * @param audience The audience (player/console) to send error messages to
     * @param commandName The name of the command being executed (for logging)
     * @param errorMessageKey The localization key for the error message
     * @param block The command execution block
     * @return true if execution succeeded, false if an error occurred
     */
    fun executeWithCustomError(
        audience: Audience,
        commandName: String,
        errorMessageKey: String,
        block: () -> Unit
    ): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            handleCommandError(audience, commandName, e, errorMessageKey)
            false
        }
    }

    /**
     * Executes a command block that returns a result, with safe error handling.
     *
     * @param audience The audience (player/console) to send error messages to
     * @param commandName The name of the command being executed (for logging)
     * @param defaultResult The default result to return on error
     * @param block The command execution block that returns a result
     * @return The result of the block, or defaultResult if an error occurred
     */
    fun <T> executeWithResult(
        audience: Audience,
        commandName: String,
        defaultResult: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            handleCommandError(audience, commandName, e)
            defaultResult
        }
    }

    /**
     * Handles command execution errors by logging and sending user-friendly messages.
     */
    private fun handleCommandError(
        audience: Audience,
        commandName: String,
        exception: Exception,
        customErrorKey: String? = null
    ) {
        // Log the full error with context
        logger.log(Level.WARNING, "Command execution failed: $commandName", exception)

        // Send user-friendly error message
        try {
            val errorMessage = if (customErrorKey != null) {
                messageService.renderSystem(customErrorKey)
            } else {
                messageService.renderSystem("command.execution_failed", mapOf(
                    "command" to commandName
                ))
            }
            audience.sendMessage(errorMessage)
        } catch (renderException: Exception) {
            // Fallback if message rendering fails
            logger.log(Level.WARNING, "Failed to render error message", renderException)
            audience.sendMessage(Component.text("An error occurred while executing the command.")
                .color(NamedTextColor.RED))
        }
    }
}
