package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.services.RoseChatQuickChat
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
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    /** Shows usage help when `/gmc` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        if (!player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT,
                "§c❌ Only guild moderators can use mod chat!",
            )
        ) return
        player.sendMessage("§1=== Quick Mod Chat ===")
        player.sendMessage("§7Use §f/gmc <message> §7to send a message to guild moderators.")
        player.sendMessage("§7Only guild moderators will see your message.")
        player.sendMessage("§7To toggle mod chat mode, use §f/g modchat§7.")
    }

    /** Sends a one-shot message to the RoseChat guild-modchat channel via quickChat. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        if (!player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT,
                "§c❌ Only guild moderators can use mod chat!",
            )
        ) return

        val text = message.joinToString(" ")
        when (RoseChatQuickChat.send(player, "guild-modchat", text)) {
            RoseChatQuickChat.Result.Sent -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage("§c❌ Message cannot be empty.")
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage("§c❌ Mod chat channel is not configured in RoseChat.")
        }
    }
}
