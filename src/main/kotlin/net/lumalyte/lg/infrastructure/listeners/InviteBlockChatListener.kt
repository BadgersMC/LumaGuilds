package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannel
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Hides a blocked player's messages in the guild, ally and mod channels from the player who blocked them. RoseChat
 * fires [PreParseMessageEvent] once per recipient, so cancelling it only skips that viewer. Registered only when
 * RoseChat is present; async-safe (blocks are read from memory).
 */
internal class InviteBlockChatListener(private val blocks: InviteBlockService) : Listener {
    /** Cancels only the blocking recipient's guild-channel parse event. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPreParseMessage(event: PreParseMessageEvent) {
        if (event.message.channel !is LumaGuildsChannel) return
        val viewer = event.viewer.asPlayer() ?: return
        val sender = event.message.sender.asPlayer() ?: return
        if (blocks.hidesChat(viewer.uniqueId, sender.uniqueId)) event.isCancelled = true
    }
}
