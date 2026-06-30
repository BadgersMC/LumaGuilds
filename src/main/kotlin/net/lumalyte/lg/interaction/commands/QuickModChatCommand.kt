package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick mod chat: `/gmc <message>` sends one message to guild moderators
 * without changing the player's current chat channel.
 *
 * `/gmc` alone shows help text. Only usable by guild moderators.
 */
@CommandAlias("gmc")
internal class QuickModChatCommand : BaseCommand(), KoinComponent {

    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    /** Shows usage help when `/gmc` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }
        val primaryGuildId = guilds.first().id
        if (!memberService.hasPermission(
                playerId, primaryGuildId, RankPermission.MODERATE_CHAT,
            )
        ) {
            player.sendMessage("§c❌ Only guild moderators can use mod chat!")
            return
        }
        player.sendMessage("§1=== Quick Mod Chat ===")
        player.sendMessage("§7Use §f/gmc <message> §7to send a message to guild moderators.")
        player.sendMessage("§7Only guild moderators will see your message.")
        player.sendMessage("§7To toggle mod chat mode, use §f/g modchat§7.")
    }

    /** Routes a one-shot message to mod chat via [ChatService.routeMessage]. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val primaryGuildId = guilds.first().id
        if (!memberService.hasPermission(
                playerId, primaryGuildId, RankPermission.MODERATE_CHAT,
            )
        ) {
            player.sendMessage("§c❌ Only guild moderators can use mod chat!")
            return
        }

        val text = message.joinToString(" ")
        if (text.isBlank()) {
            player.sendMessage("§c❌ Message cannot be empty.")
            return
        }

        val success = chatService.routeMessage(playerId, text, ChatChannel.MODCHAT)
        if (!success) {
            player.sendMessage(
                "§e⚠️ No guild moderators are currently online to receive your message.",
            )
        }
        // No success echo — the message is routed to recipients (including sender)
    }
}
