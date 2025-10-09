package net.lumalyte.lg.interaction.brigadier

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.interaction.commands.AdminSurveillanceBasicCommand
import net.lumalyte.lg.interaction.commands.BedrockCacheStatsBasicCommand
import net.lumalyte.lg.interaction.commands.LumaGuildsBasicCommand
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

/**
 * Command registrar that manages the transition from legacy Bukkit commands to Brigadier commands.
 * Uses lifecycle gating to determine whether to register Brigadier commands or fall back to legacy registration.
 */
class CommandRegistrar(private val plugin: LumaGuilds) {

    /**
     * Registers all commands based on Brigadier availability and configuration.
     * Checks for Paper lifecycle events and the brigadier.enabled config flag.
     */
    fun registerAll() {
        val brigadierEnabled = plugin.config.getBoolean("brigadier_enabled", true)
        val brigadierAvailable = detectLifecycleBrigadier()

        if (brigadierAvailable && brigadierEnabled) {
            plugin.logger.info("✓ Brigadier command system available and enabled - registering Brigadier trees")
            registerBrigadier()
        } else {
            val reason = when {
                !brigadierAvailable -> "Paper lifecycle events unavailable"
                !brigadierEnabled -> "brigadier_enabled=false in config"
                else -> "unknown"
            }
            plugin.logger.info("⚠ Brigadier not available ($reason) - falling back to legacy command registration")
            registerLegacy()
        }
    }

    /**
     * Detects if Paper's LifecycleEvents for Brigadier command registration are available.
     * This checks for the presence of io.papermc.paper.plugin.lifecycle.event.LifecycleEvents class.
     */
    private fun detectLifecycleBrigadier(): Boolean {
        return try {
            // Check if Paper's LifecycleEvents class exists
            Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEvents")

            // Check if Commands class exists
            Class.forName("io.papermc.paper.command.brigadier.Commands")

            // Check if CommandSourceStack exists
            Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack")

            plugin.logger.info("✓ Paper LifecycleEvents API detected")
            true
        } catch (e: ClassNotFoundException) {
            plugin.logger.info("⚠ Paper LifecycleEvents API not found - ${e.message}")
            false
        } catch (e: Exception) {
            plugin.logger.warning("⚠ Error detecting LifecycleEvents API: ${e.message}")
            false
        }
    }

    /**
     * Registers Brigadier command trees using Paper's command system.
     * Called when Brigadier API is available and enabled.
     */
    private fun registerBrigadier() {
        try {
            // Create a command dispatcher and register all command trees
            val dispatcher = com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>()

            // Register available command trees
            ClaimRootTree.register(dispatcher)
            ClaimListCommand.register(dispatcher)
            ClaimMenuCommand.register(dispatcher)
            ClaimOverrideRootTree.register(dispatcher)
            GuildRootTree.register(dispatcher)
            PartyChatRootTree.register(dispatcher)
            // Temporarily disabled - stub code being fixed
            // LumaGuildsAdminRootTree.register(dispatcher)
            // BedrockCacheStatsRootTree.register(dispatcher)
            // AdminSurveillanceRootTree.register(dispatcher)

            // Register the dispatcher with Paper's command system
            registerWithPaper(dispatcher)

            plugin.logger.info("✓ Brigadier command trees registered successfully")
        } catch (e: Exception) {
            plugin.logger.warning("❌ Failed to register Brigadier commands: ${e.message}")
            plugin.logger.info("Falling back to legacy command registration")
            registerLegacy()
        }
    }

    /**
     * Registers the command dispatcher with Paper's command system.
     * This is a temporary implementation until proper lifecycle events are integrated.
     */
    private fun registerWithPaper(dispatcher: com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>) {
        try {
            // Get Paper's command manager and register our dispatcher
            val server = plugin.server
            val commandMap = server.commandMap

            // For each root command in our dispatcher, create a wrapper that delegates to Brigadier
            dispatcher.root.children.forEach { node ->
                val name = node.name
                val brigadierCommand = BrigadierCommandWrapper(dispatcher, name)
                commandMap.register(plugin.name.lowercase(), brigadierCommand)
            }

            plugin.logger.info("✓ Registered ${dispatcher.root.children.size} Brigadier commands with Paper")
        } catch (e: Exception) {
            plugin.logger.warning("❌ Failed to register with Paper command system: ${e.message}")
            throw e
        }
    }

    /**
     * Registers commands using the legacy Bukkit command system.
     * This is the current fallback mechanism during the Brigadier migration.
     */
    private fun registerLegacy() {
        try {
            // Register basic admin commands using traditional Bukkit system
            plugin.getCommand("lumaguilds")?.setExecutor(LumaGuildsBasicCommand())
            plugin.getCommand("lumaguilds")?.tabCompleter = LumaGuildsBasicCommand()

            plugin.getCommand("adminsurveillance")?.setExecutor(AdminSurveillanceBasicCommand())
            plugin.getCommand("bedrockcachestats")?.setExecutor(BedrockCacheStatsBasicCommand())

            plugin.logger.info("✓ Legacy commands registered successfully")
        } catch (e: Exception) {
            plugin.logger.warning("❌ Failed to register legacy commands: ${e.message}")
        }
    }
}
