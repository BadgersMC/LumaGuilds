package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.services.ConfigService
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

/**
 * Interface for handling different types of chat input
 */
interface ChatInputHandler {
    fun onChatInput(player: Player, input: String)
    fun onCancel(player: Player)
}

/**
 * Listener for handling chat input when players are prompted to enter guild tag
 */
class ChatInputListener : Listener, KoinComponent {

    private val configService: ConfigService by inject()

    init {
        // Initialize ChatInputListener
    }

    /**
     * Gets the configured event priority for chat input handling
     */
    private fun getChatInputPriority(): EventPriority {
        val config = configService.loadConfig()
        val priorityString = config.party.chatInputListenerPriority.uppercase()

        return when (priorityString) {
            "HIGHEST" -> EventPriority.HIGHEST
            "HIGH" -> EventPriority.HIGH
            "NORMAL" -> EventPriority.NORMAL
            "LOW" -> EventPriority.LOW
            "LOWEST" -> EventPriority.LOWEST
            else -> {
                // Default to HIGHEST if invalid priority specified
                EventPriority.HIGHEST
            }
        }
    }

    // Track players who are in input mode with their handlers
    private val inputModePlayers = mutableMapOf<UUID, ChatInputHandler>()

    /**
     * Registers a player as being in input mode with a handler
     */
    fun startInputMode(player: Player, handler: ChatInputHandler) {
        inputModePlayers[player.uniqueId] = handler
    }

    /**
     * Removes a player from input mode
     */
    fun stopInputMode(player: Player) {
        inputModePlayers.remove(player.uniqueId)
    }

    /**
     * Checks if a player is in input mode
     */
    fun isInInputMode(player: Player): Boolean {
        return inputModePlayers.containsKey(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGHEST) // Note: Priority configurable via party.chat_input_listener_priority in config.yml
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        if (inputModePlayers.containsKey(player.uniqueId)) {
            handleChatInput(event)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChatMonitor(event: AsyncChatEvent) {
        val player = event.player
        if (inputModePlayers.containsKey(player.uniqueId)) {
            // Monitor cancelled status for debugging if needed
        }
    }

    // Fallback for older server versions or plugins that don't use AsyncChatEvent
    // Using deprecated AsyncPlayerChatEvent intentionally for broader compatibility
    @Suppress("DEPRECATION")
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    fun onPlayerChatFallbackHighest(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val player = event.player
        if (inputModePlayers.containsKey(player.uniqueId)) {
            handleChatInputFallback(event)
        }
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onPlayerChatFallbackLowest(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val player = event.player
        if (inputModePlayers.containsKey(player.uniqueId)) {
            handleChatInputFallback(event)
        }
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    fun onPlayerChatFallbackMonitor(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val player = event.player
        if (inputModePlayers.containsKey(player.uniqueId)) {
            // Monitor cancelled status for debugging if needed
        }
    }

    private fun handleChatInput(event: AsyncChatEvent) {
        val player = event.player
        val handler = inputModePlayers[player.uniqueId]

        // Cancel the chat event to prevent it from being sent to other players
        event.isCancelled = true

        // Convert Adventure Component to plain text for processing
        val plainTextSerializer = PlainTextComponentSerializer.plainText()
        val input = plainTextSerializer.serialize(event.message()).trim()

        processChatInput(player, handler, input)
    }

    @Suppress("DEPRECATION")
    private fun handleChatInputFallback(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val player = event.player
        val handler = inputModePlayers[player.uniqueId]

        // Cancel the chat event to prevent it from being sent to other players
        event.isCancelled = true

        // Get plain text from the message
        val input = event.message.trim()

        processChatInput(player, handler, input)
    }

    private fun processChatInput(player: Player, handler: ChatInputHandler?, input: String) {
        // Check if player wants to cancel
        if (input.equals("cancel", ignoreCase = true)) {
            stopInputMode(player)

            // Schedule cancel callback on main thread (in case it opens GUIs)
            org.bukkit.Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
                handler?.onCancel(player)
            })
            return
        }

        // Process the input using the handler
        // IMPORTANT: Schedule on main thread because handlers often reopen GUIs
        org.bukkit.Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable {
            handler?.onChatInput(player, input)
        })

        // Remove from input mode
        stopInputMode(player)
    }
}
