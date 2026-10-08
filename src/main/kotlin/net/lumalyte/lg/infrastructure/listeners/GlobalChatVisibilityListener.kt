package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent
import dev.rosewood.rosechat.message.MessageDirection
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.infrastructure.services.RealRoseChatAdapter
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** Filters only default-channel player chat for the receiving player's preference. */
internal class GlobalChatVisibilityListener(
    private val settings: ChatSettingsRepository,
    private val chat: RoseChatAdapter = RealRoseChatAdapter(),
) : Listener {
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onMessage(event: PreParseMessageEvent) {
        val viewer = event.viewer.asPlayer() ?: return
        if (event.direction != MessageDirection.PLAYER_TO_SERVER || !event.message.sender.isPlayer) return
        val channel = event.message.channel ?: return // DMs and server notices are preserved.
        val global = chat.getDefaultChannel() ?: return
        if (channel.id == global.id && !settings.getVisibilitySettings(viewer.uniqueId).globalChatVisible) {
            event.isCancelled = true
        }
    }
}
