package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.MessageService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Wrapper that bridges Bukkit's command system with Brigadier commands.
 * Allows Brigadier commands to be registered as traditional Bukkit commands.
 */
class BrigadierCommandWrapper(
    private val dispatcher: CommandDispatcher<CommandSourceStack>,
    private val commandName: String
) : Command(commandName), TabCompleter, KoinComponent {

    private val messageService: MessageService by inject()

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        return try {
            // Convert Bukkit CommandSender to Brigadier CommandSourceStack
            val sourceStack = createCommandSourceStack(sender)

            // Join args back into a single string for parsing
            val fullCommand = if (args.isEmpty()) commandName else "$commandName ${args.joinToString(" ")}"

            // Parse and execute the command
            val parseResults = dispatcher.parse(fullCommand, sourceStack)
            val result = dispatcher.execute(parseResults)

            // Return true if command executed successfully (result >= 0)
            result >= 0
        } catch (e: CommandSyntaxException) {
            // Handle Brigadier syntax errors
            val errorMessage = e.message ?: "Invalid command syntax"
            sender.sendMessage(Component.text("❌ $errorMessage").color(NamedTextColor.RED))
            false
        } catch (e: Exception) {
            // Handle other execution errors
            sender.sendMessage(Component.text("❌ Command execution failed").color(NamedTextColor.RED))
            e.printStackTrace()
            false
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        return try {
            val sourceStack = createCommandSourceStack(sender)
            val fullCommand = "$commandName ${args.joinToString(" ")}".trim()

            val parseResults = dispatcher.parse(fullCommand, sourceStack)
            val suggestions = dispatcher.getCompletionSuggestions(parseResults).get()

            suggestions.list.map { it.text }
        } catch (e: Exception) {
            // Return empty list on tab completion errors
            emptyList()
        }
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        return onTabComplete(sender, this, alias, args) ?: emptyList()
    }

    /**
     * Creates a CommandSourceStack from a Bukkit CommandSender.
     */
    private fun createCommandSourceStack(sender: CommandSender): CommandSourceStack {
        // Create a minimal CommandSourceStack wrapper for the Bukkit sender
        return object : CommandSourceStack {
            override fun getSender(): CommandSender = sender
            override fun getLocation(): org.bukkit.Location? = when (sender) {
                is Player -> sender.location
                else -> null
            }
            override fun getExecutor(): org.bukkit.entity.Entity? = when (sender) {
                is Player -> sender
                else -> null
            }
            override fun withLocation(location: org.bukkit.Location): CommandSourceStack = this
            override fun withExecutor(executor: org.bukkit.entity.Entity): CommandSourceStack = this
        }
    }

    override fun getDescription(): String {
        // Try to get description from plugin.yml or provide a default
        return "Brigadier command: $commandName"
    }

    override fun getUsage(): String {
        return "/$commandName <args>"
    }

    override fun getAliases(): List<String> {
        // TODO: Implement alias support if needed
        return emptyList()
    }
}
