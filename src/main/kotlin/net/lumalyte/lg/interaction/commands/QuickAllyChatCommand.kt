package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.services.RoseChatQuickChat
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

    /** Sends a one-shot message to the RoseChat guild-ally channel via quickChat. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        if (!player.requireGuildMembership(guildService)) return

        val text = message.joinToString(" ")
        when (RoseChatQuickChat.send(player, "guild-ally", text)) {
            RoseChatQuickChat.Result.Sent -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage("§c❌ Message cannot be empty.")
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage("§c❌ Ally channel is not configured in RoseChat.")
        }
    }
}
