package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannel
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Enforces guild mutes on the RoseChat message path.
 *
 * The mute checks in [net.lumalyte.lg.interaction.commands.QuickGuildChatCommand]
 * and [GuildChatListener] only cover channel entry and the `/gc` command — a
 * player who is ALREADY seated in the guild channel when the mute lands could
 * keep chatting. RoseChat fires a cancellable [PreParseMessageEvent] for every
 * recipient of a channel message, so cancelling it here (whenever the sender's
 * guild is muted) blocks delivery to every recipient.
 *
 * Registered only when RoseChat is present (softdepend), same as the channel
 * provider. Async-safe: PreParseMessageEvent is fired off the main thread.
 */
class GuildMuteChatListener(
    private val guildService: GuildService,
    private val penaltyService: PenaltyService,
    private val lang: LangService,
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPreParseMessage(event: PreParseMessageEvent) {
        val channel = event.message.channel
        if (channel !is LumaGuildsChannel) return

        val sender = event.message.sender.asPlayer() ?: return
        val senderGuilds = guildService.getPlayerGuilds(sender.uniqueId)
        if (senderGuilds.isEmpty()) return

        // A guild-wide mute silences everyone in that guild, on every channel
        // type (GUILD / ALLY / MODCHAT) — same rule as the /gc and /g chat checks.
        val muted = senderGuilds.any { penaltyService.isGuildMuted(it.id) }
        if (!muted) return

        event.isCancelled = true
        sender.sendMessage(lang.msg("notification.guild_chat.guild_muted"))
    }
}
