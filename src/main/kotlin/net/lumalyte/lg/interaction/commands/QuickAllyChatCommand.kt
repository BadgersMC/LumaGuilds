package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick ally chat: `/gac <message>` sends one message to ally chat without
 * changing the player's current chat channel (which `/g allychat` permanently
 * toggles).
 *
 * `/gac` alone shows help text.
 */
@CommandAlias("gac")
internal class QuickAllyChatCommand : BaseCommand(), KoinComponent {
    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()

    /** Shows usage help when `/gac` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        if (!player.requireGuildMembership(guildService)) return
        player.sendMessage("§3=== Quick Ally Chat ===")
        player.sendMessage("§7Use §f/gac <message> §7to send a single message to ally chat.")
        player.sendMessage("§7Your chat channel won't change — you stay in your current chat.")
        player.sendMessage("§7To toggle permanent ally chat mode, use §f/g allychat§7.")
    }

    /** Routes a one-shot message to ally chat via [ChatService.routeMessage]. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        val playerId = player.uniqueId
        if (!player.requireGuildMembership(guildService)) return

        val text = message.joinToString(" ")
        if (text.isBlank()) {
            player.sendMessage("§c❌ Message cannot be empty.")
            return
        }

        val success = chatService.routeMessage(playerId, text, ChatChannel.ALLY)
        if (!success) {
            player.sendMessage(
                "§e⚠️ No allied guild members are currently online to receive your message.",
            )
        }
        // No success echo — the message is routed to recipients (including sender)
    }
}
