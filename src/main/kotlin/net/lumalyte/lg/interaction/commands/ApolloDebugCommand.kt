package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.infrastructure.services.apollo.GuildRichPresenceService
import net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Command to debug and manually trigger Apollo integrations
 * Usage: /apollodebug [refresh|status]
 */
class ApolloDebugCommand : CommandExecutor, KoinComponent {

    private val lunarClientService: LunarClientService by inject()
    private val richPresenceService: GuildRichPresenceService? by inject()
    private val waypointService: GuildWaypointService? by inject()
    private val notificationService: net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService? by inject()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return true
        }

        if (!sender.hasPermission("lumalyte.apollo.debug")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "refresh" -> refreshApollo(sender)
            "status" -> showStatus(sender)
            "test" -> testNotification(sender)
            "help", null -> showHelp(sender)
            else -> {
                sender.sendMessage("§cUnknown subcommand. Use /apollodebug help for available commands.")
            }
        }

        return true
    }

    private fun refreshApollo(player: Player) {
        player.sendMessage("§6=== Refreshing Apollo Integrations ===")

        // Check if player is using Lunar Client
        val isLC = lunarClientService.isLunarClient(player)
        player.sendMessage("§eLunar Client: ${if (isLC) "§a✓ Detected" else "§c✗ Not Detected"}")

        if (!isLC) {
            player.sendMessage("§cYou must be using Lunar Client to see Apollo features.")
            return
        }

        // Get Apollo player
        val apolloPlayer = lunarClientService.getApolloPlayer(player)
        if (apolloPlayer == null) {
            player.sendMessage("§cFailed to get ApolloPlayer instance.")
            return
        }

        // Refresh Rich Presence
        try {
            richPresenceService?.updateGuildRichPresence(player)
            player.sendMessage("§aRich Presence: §f✓ Refreshed")
        } catch (e: Exception) {
            player.sendMessage("§cRich Presence: §f✗ Error - ${e.message}")
        }

        // Refresh Waypoints
        try {
            waypointService?.showGuildHomeWaypoints(player)
            player.sendMessage("§aWaypoints: §f✓ Refreshed")
        } catch (e: Exception) {
            player.sendMessage("§cWaypoints: §f✗ Error - ${e.message}")
        }

        player.sendMessage("§aApollo integrations refreshed! Check your Lunar Client.")
    }

    private fun testNotification(player: Player) {
        player.sendMessage("§6=== Testing Apollo Notification ===")

        // Check if player is using Lunar Client
        val isLC = lunarClientService.isLunarClient(player)
        if (!isLC) {
            player.sendMessage("§cYou must be using Lunar Client to see notifications.")
            return
        }

        // Test welcome notification
        try {
            notificationService?.sendWelcomeNotification(player)
            player.sendMessage("§aWelcome notification sent! Check your Lunar Client.")
        } catch (e: Exception) {
            player.sendMessage("§cNotification Error: ${e.message}")
        }
    }

    private fun showStatus(player: Player) {
        player.sendMessage("§6=== Apollo Integration Status ===")

        // Lunar Client detection
        val isLC = lunarClientService.isLunarClient(player)
        player.sendMessage("§eLunar Client: ${if (isLC) "§a✓ Detected" else "§c✗ Not Detected"}")

        // Apollo availability
        val apolloAvailable = lunarClientService.isApolloAvailable()
        player.sendMessage("§eApollo Plugin: ${if (apolloAvailable) "§a✓ Loaded" else "§c✗ Not Loaded"}")

        // Lunar Client count
        val lcCount = lunarClientService.getLunarClientCount()
        player.sendMessage("§eLunar Client Players: §f$lcCount online")

        // Rich Presence status
        val rpEnabled = richPresenceService != null
        player.sendMessage("§eRich Presence: ${if (rpEnabled) "§a✓ Enabled" else "§c✗ Disabled"}")

        // Waypoint status
        val waypointEnabled = waypointService != null
        player.sendMessage("§eWaypoints: ${if (waypointEnabled) "§a✓ Enabled" else "§c✗ Disabled"}")

        // Notification status
        val notificationEnabled = notificationService != null
        player.sendMessage("§eNotifications: ${if (notificationEnabled) "§a✓ Enabled" else "§c✗ Disabled"}")

        waypointService?.let { service ->
            val stats = service.getStats()
            player.sendMessage("§7  - Active Players: §f${stats["active_players"]}")
            player.sendMessage("§7  - Total Waypoints: §f${stats["total_waypoints"]}")
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Apollo Debug Commands ===")
        sender.sendMessage("§e/apollodebug status §7- Show Apollo integration status")
        sender.sendMessage("§e/apollodebug refresh §7- Manually refresh Rich Presence & Waypoints")
        sender.sendMessage("§e/apollodebug test §7- Send a test welcome notification")
        sender.sendMessage("§e/apollodebug help §7- Show this help message")
        sender.sendMessage("")
        sender.sendMessage("§7This command requires Lunar Client to function.")
    }
}
