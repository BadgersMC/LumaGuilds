package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick guild chat: /gc <message> sends one message to guild chat without
 * changing the player's current chat channel (which /g chat permanently toggles).
 *
 * /gc alone shows help text.
 */
@CommandAlias("gc")
class QuickGuildChatCommand : BaseCommand(), KoinComponent {

    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()

    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }
        player.sendMessage("§2=== Quick Guild Chat ===")
        player.sendMessage("§7Use §f/gc <message> §7to send a single message to guild chat.")
        player.sendMessage("§7Your chat channel won't change — you stay in your current chat.")
        player.sendMessage("§7To toggle permanent guild chat mode, use §f/g chat§7.")
    }

    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val text = message.joinToString(" ")
        if (text.isBlank()) {
            player.sendMessage("§c❌ Message cannot be empty.")
            return
        }

        val success = chatService.routeMessage(playerId, text, ChatChannel.GUILD)
        if (!success) {
            player.sendMessage("§e⚠️ No guild members are currently online to receive your message.")
        }
        // No success echo — the message is routed to recipients (including sender)
    }
}
